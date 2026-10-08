package dev.coms4156.knowledgedrop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application tunables bound from the {@code knowledgedrop.*} properties.
 *
 * <p>Nothing reads these yet: they are the knobs the TODO services (ingestion, retrieval) will use.
 *
 * @param storageDir directory where original uploaded files are kept
 * @param ingestion chunking settings
 * @param retrieval retrieval and rank-fusion settings
 */
@ConfigurationProperties(prefix = "knowledgedrop")
public record KnowledgeDropProperties(
    @DefaultValue("./data/files") String storageDir,
    @DefaultValue Ingestion ingestion,
    @DefaultValue Retrieval retrieval) {

  /**
   * Chunking settings.
   *
   * @param chunkSize characters per chunk
   * @param chunkOverlap characters shared between consecutive chunks
   */
  public record Ingestion(
      @DefaultValue("800") int chunkSize,
      @DefaultValue("100") int chunkOverlap) {}

  /**
   * Retrieval settings.
   *
   * @param defaultK default number of results when a request omits K
   * @param rrfK constant in the reciprocal rank fusion formula 1 / (rrfK + rank)
   * @param candidatePool number of candidates fetched per mode before fusion
   */
  public record Retrieval(
      @DefaultValue("5") int defaultK,
      @DefaultValue("60") int rrfK,
      @DefaultValue("50") int candidatePool) {}
}
