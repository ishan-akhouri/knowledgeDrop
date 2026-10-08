package dev.coms4156.knowledgedrop.exception;

/**
 * JSON body returned for every error ("Error with reason" in the API spec).
 *
 * @param error HTTP reason phrase, such as "Not Found"
 * @param reason explanation of what went wrong
 */
public record ErrorResponse(String error, String reason) {}
