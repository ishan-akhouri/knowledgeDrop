package dev.coms4156.knowledgedrop.service;

import dev.coms4156.knowledgedrop.api.dto.SummarizeRequest;
import dev.coms4156.knowledgedrop.api.dto.SummarizeResponse;
import dev.coms4156.knowledgedrop.exception.ApiException;
import dev.coms4156.knowledgedrop.model.ClientRecord;
import dev.coms4156.knowledgedrop.model.DocumentRecord;
import dev.coms4156.knowledgedrop.model.DocumentStatus;
import dev.coms4156.knowledgedrop.model.SummaryLength;
import dev.coms4156.knowledgedrop.repository.DocumentRepository;
import dev.coms4156.knowledgedrop.retrieval.KeywordIndex;
import dev.coms4156.knowledgedrop.retrieval.LlmClient;
import dev.coms4156.knowledgedrop.retrieval.RankedChunk;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Map-reduce summarization of a single document.
 *
 * <p>The document's chunks are split into groups of at most {@link #MAX_CONTEXT_CHARS}. A document
 * that fits one group is summarized in a single LLM call. Otherwise each group is summarized on its
 * own (map), and the partial summaries are combined into the final summary (reduce). If the
 * partial summaries are themselves too long for one call, they are combined in groups first, as
 * many rounds as needed. Calls run one at a time to stay within the LLM's rate limits.
 *
 * <p>Uses {@link LlmClient} (Gemini Flash). Prompt construction lives here, not in the client.
 */
@Service
public class SummarizationService {

  /** System prompt template; {@code %s} is filled with the length instructions. */
  static final String SYSTEM_PROMPT =
      "You are a professional writer summarizing a document for a reader who has not seen it. "
          + "The context is the document's text, split into consecutive excerpts that may repeat a "
          + "few words at their boundaries. Use only information in the context; do not add facts, "
          + "opinions, or outside knowledge. Write in plain prose with no preamble such as "
          + "\"Here is a summary\". %s";

  /** System prompt for the map step: a detailed summary of one group of excerpts. */
  static final String MAP_SYSTEM_PROMPT =
      "You are a professional writer summarizing one part of a longer document. The context is "
          + "consecutive excerpts from that part, which may repeat a few words at their "
          + "boundaries. Use only information in the context; do not add facts, opinions, or "
          + "outside knowledge. Write a detailed summary of 300 to 500 words in plain prose that "
          + "keeps every key point, figure, date, name, and conclusion, so it can later be "
          + "combined with summaries of the other parts. Do not add a preamble such as "
          + "\"Here is a summary\".";

  /** System prompt template for the reduce step; {@code %s} is filled with the instructions. */
  static final String REDUCE_SYSTEM_PROMPT =
      "You are a professional writer summarizing a document for a reader who has not seen it. "
          + "The context is a series of summaries of consecutive parts of the document, in order. "
          + "Combine them into one coherent summary of the whole document, merging repeated "
          + "points. Use only information in the context; do not add facts, opinions, or outside "
          + "knowledge. Write in plain prose with no preamble such as \"Here is a summary\". %s";

  /** Length instructions for {@link SummaryLength#SHORT}. */
  static final String SHORT_INSTRUCTIONS =
      "Write one paragraph of 3 to 5 sentences covering the document's main purpose and its most "
          + "important conclusions.";

  /** Length instructions for {@link SummaryLength#DETAILED}. */
  static final String DETAILED_INSTRUCTIONS =
      "Write a thorough summary of several paragraphs that follows the document's structure. "
          + "Cover each major section, its key points, and any important figures, dates, names, or "
          + "conclusions.";

  /** Most tokens of document context sent to the LLM in one call (one map group). */
  static final int MAX_CONTEXT_TOKENS = 25_000;

  /** Rough characters per token for English text, used to turn the token budget into chars. */
  static final int CHARS_PER_TOKEN = 4;

  /** {@link #MAX_CONTEXT_TOKENS} in characters, the unit chunk texts are measured in. */
  static final int MAX_CONTEXT_CHARS = MAX_CONTEXT_TOKENS * CHARS_PER_TOKEN;

  /** Placed between chunk texts when they are joined into one context. */
  static final String CONTEXT_SEPARATOR = "\n\n";

  private final LlmClient llmClient;
  private final DocumentRepository documentRepository;
  private final KeywordIndex keywordIndex;

  /**
   * Creates the service.
   *
   * @param llmClient the chat/completion model used for the map and reduce steps
   * @param documentRepository document records, used to check ownership and status
   * @param keywordIndex the chunk index, read in document order
   */
  public SummarizationService(
      LlmClient llmClient, DocumentRepository documentRepository, KeywordIndex keywordIndex) {
    this.llmClient = llmClient;
    this.documentRepository = documentRepository;
    this.keywordIndex = keywordIndex;
  }

  /**
   * Builds the system prompt for the requested length.
   *
   * @param length the requested length; null means short
   * @return the system prompt with the length instructions filled in
   */
  static String systemPrompt(SummaryLength length) {
    return String.format(SYSTEM_PROMPT, lengthInstructions(length));
  }

  /**
   * Builds the reduce-step system prompt for the requested length.
   *
   * @param length the requested length; null means short
   * @return the reduce prompt with the length instructions filled in
   */
  static String reducePrompt(SummaryLength length) {
    return String.format(REDUCE_SYSTEM_PROMPT, lengthInstructions(length));
  }

  private static String lengthInstructions(SummaryLength length) {
    return length == SummaryLength.DETAILED ? DETAILED_INSTRUCTIONS : SHORT_INSTRUCTIONS;
  }

  /**
   * Summarizes a document.
   *
   * @param client the authenticated caller
   * @param request the document ID and optional length
   * @return the summary and the number of chunks used
   * @throws ApiException 401 if there is no authenticated client, 400 if the request or its
   *     document ID is missing, 404 if the document does not exist, belongs to another client or
   *     was deleted, 409 if the document is still processing or failed ingestion, 500 if its
   *     chunks cannot be read or hold no text, and the LLM client's 502/503 errors (any failed
   *     call fails the whole summary)
   */
  public SummarizeResponse summarize(ClientRecord client, SummarizeRequest request) {
    if (client == null) {
      throw ApiException.unauthorized("Missing authenticated client");
    }
    if (request == null) {
      throw ApiException.badRequest("Request body is required");
    }
    if (request.documentId() == null) {
      throw ApiException.badRequest("documentId is required");
    }
    UUID documentId = request.documentId();
    requireReadyDocument(client.getId(), documentId);
    List<List<RankedChunk>> groups = groupChunks(loadChunks(documentId));
    if (groups.isEmpty()) {
      throw ApiException.internalServerError(
          "Indexed chunks of document " + documentId + " hold no text");
    }
    int chunksUsed = groups.stream().mapToInt(List::size).sum();
    SummaryLength length = request.length();

    if (groups.size() == 1) {
      String summary = llmClient.complete(systemPrompt(length), toContext(groups.get(0)));
      return new SummarizeResponse(summary, documentId, chunksUsed);
    }

    // Map: summarize each group on its own, in document order.
    List<String> partials = new ArrayList<>();
    for (List<RankedChunk> group : groups) {
      partials.add(llmClient.complete(MAP_SYSTEM_PROMPT, toContext(group)));
    }
    // Reduce: combine the partial summaries into one.
    String summary = llmClient.complete(reducePrompt(length), combinePartials(partials));
    return new SummarizeResponse(summary, documentId, chunksUsed);
  }

  /**
   * Shrinks partial summaries until they fit one call, then joins them. While the joined text is
   * over {@link #MAX_CONTEXT_CHARS}, the partials are grouped and each group is combined into a
   * detailed intermediate summary. Stops early if grouping no longer reduces the count, so it
   * always ends.
   *
   * @param partials the partial summaries in document order
   * @return the context for the final reduce call
   */
  private String combinePartials(List<String> partials) {
    List<String> current = partials;
    while (joinedLength(current) > MAX_CONTEXT_CHARS) {
      List<List<String>> groups = groupTexts(current, MAX_CONTEXT_CHARS);
      if (groups.size() >= current.size()) {
        break;
      }
      List<String> next = new ArrayList<>();
      for (List<String> group : groups) {
        next.add(
            llmClient.complete(
                reducePrompt(SummaryLength.DETAILED), String.join(CONTEXT_SEPARATOR, group)));
      }
      current = next;
    }
    return String.join(CONTEXT_SEPARATOR, current);
  }

  private static int joinedLength(List<String> texts) {
    int length = 0;
    for (String text : texts) {
      length += text.length();
    }
    return length + Math.max(0, texts.size() - 1) * CONTEXT_SEPARATOR.length();
  }

  /**
   * Reads a document's chunks in order from the keyword index.
   *
   * @param documentId the document
   * @return the chunks in document order, never empty
   * @throws ApiException 500 if the index cannot be read or holds no chunks for the document
   */
  private List<RankedChunk> loadChunks(UUID documentId) {
    List<RankedChunk> chunks;
    try {
      chunks = keywordIndex.chunksInOrder(documentId);
    } catch (UncheckedIOException e) {
      throw ApiException.internalServerError(
          "Could not read chunks for document " + documentId + ": " + e.getMessage());
    }
    // A ready document always has chunks, so none means the index is out of sync.
    if (chunks == null || chunks.isEmpty()) {
      throw ApiException.internalServerError("No indexed chunks found for document " + documentId);
    }
    return chunks;
  }

  /**
   * Joins chunk texts into the LLM context, in order. Overlap between neighbouring chunks is kept;
   * it only repeats a little text and keeps sentences that cross a chunk boundary intact.
   *
   * @param chunks the chunks in document order
   * @return the context text
   */
  static String toContext(List<RankedChunk> chunks) {
    StringBuilder context = new StringBuilder();
    for (RankedChunk chunk : chunks) {
      if (chunk.text() == null || chunk.text().isBlank()) {
        continue;
      }
      if (!context.isEmpty()) {
        context.append(CONTEXT_SEPARATOR);
      }
      context.append(chunk.text());
    }
    return context.toString();
  }

  /**
   * Splits chunks into consecutive groups whose joined context fits {@link #MAX_CONTEXT_CHARS}.
   *
   * @param chunks the chunks in document order
   * @return the groups in document order
   * @see #groupChunks(List, int)
   */
  static List<List<RankedChunk>> groupChunks(List<RankedChunk> chunks) {
    return groupChunks(chunks, MAX_CONTEXT_CHARS);
  }

  /**
   * Splits chunks into consecutive groups, in order, so that {@link #toContext} of each group is
   * at most {@code maxChars} long. A new group starts when the next chunk would push the current
   * one over the limit. A single chunk longer than the limit gets a group of its own rather than
   * being cut. Blank chunks are skipped, as in {@link #toContext}.
   *
   * @param chunks the chunks in document order
   * @param maxChars the most characters of context per group; must be positive
   * @return the groups in document order, each non-empty; empty if there is no text
   */
  static List<List<RankedChunk>> groupChunks(List<RankedChunk> chunks, int maxChars) {
    List<RankedChunk> withText =
        chunks.stream().filter(c -> c.text() != null && !c.text().isBlank()).toList();
    return group(withText, RankedChunk::text, maxChars);
  }

  /**
   * Splits texts into consecutive groups whose joined length fits {@code maxChars}, with the same
   * rules as {@link #groupChunks(List, int)}.
   *
   * @param texts the texts in order
   * @param maxChars the most characters per joined group; must be positive
   * @return the groups in order
   */
  static List<List<String>> groupTexts(List<String> texts, int maxChars) {
    return group(texts, text -> text, maxChars);
  }

  private static <T> List<List<T>> group(List<T> items, Function<T, String> text, int maxChars) {
    if (maxChars <= 0) {
      throw new IllegalArgumentException("maxChars must be positive");
    }
    List<List<T>> groups = new ArrayList<>();
    List<T> current = new ArrayList<>();
    int currentChars = 0;
    for (T item : items) {
      int length = text.apply(item).length();
      int added = current.isEmpty() ? length : CONTEXT_SEPARATOR.length() + length;
      if (!current.isEmpty() && currentChars + added > maxChars) {
        groups.add(current);
        current = new ArrayList<>();
        currentChars = 0;
        added = length;
      }
      current.add(item);
      currentChars += added;
    }
    if (!current.isEmpty()) {
      groups.add(current);
    }
    return groups;
  }

  /**
   * Loads a document owned by the client and checks that it is ready to summarize.
   *
   * @param clientId the caller's ID
   * @param documentId the requested document's ID
   * @return the ready document
   * @throws ApiException 404 if missing, owned by someone else or deleted; 409 if processing or
   *     failed
   */
  private DocumentRecord requireReadyDocument(UUID clientId, UUID documentId) {
    // Scoped to the owner, so another client's document looks the same as a missing one.
    DocumentRecord document =
        documentRepository
            .findByIdAndOwnerId(documentId, clientId)
            .filter(d -> d.getDeletedAt() == null)
            .orElseThrow(() -> ApiException.notFound("Document " + documentId + " not found"));

    DocumentStatus status = document.getStatus();
    if (status == DocumentStatus.PROCESSING) {
      throw ApiException.conflict(
          "Document " + documentId + " is still processing, try again once it is ready");
    }
    if (status == DocumentStatus.FAILED) {
      String reason = document.getFailureReason();
      throw ApiException.conflict(
          "Document " + documentId + " failed ingestion"
              + (reason == null || reason.isBlank() ? "" : ": " + reason));
    }
    if (status != DocumentStatus.READY) {
      throw ApiException.conflict(
          "Document " + documentId + " is not ready (status: " + status + ")");
    }
    return document;
  }
}
