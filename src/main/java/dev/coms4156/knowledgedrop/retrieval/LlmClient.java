package dev.coms4156.knowledgedrop.retrieval;

/**
 * Sends a prompt to the chat/completion model.
 *
 * <p>DEFERRED: the LLM provider has not been chosen. Used by ask (answer from context) and
 * summarize (map and reduce steps). Keep prompt construction in the services, not here, so this
 * stays a thin, mockable wrapper.
 */
public interface LlmClient {

  /**
   * Completes a prompt.
   *
   * @param systemPrompt instructions for the model (for example "answer only from the context")
   * @param userPrompt the question plus any retrieved context
   * @return the model's text answer
   */
  String complete(String systemPrompt, String userPrompt);

  // TODO(llm): add a timeout and a retry policy (provider calls fail). Surface failures as an
  //   ApiException so the controller returns a clear error instead of a stack trace.
}

