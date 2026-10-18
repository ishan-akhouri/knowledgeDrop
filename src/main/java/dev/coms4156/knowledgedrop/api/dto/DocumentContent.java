package dev.coms4156.knowledgedrop.api.dto;

import dev.coms4156.knowledgedrop.model.DocumentRecord;
import java.io.InputStream;

/**
 * An open document: its metadata plus a stream over the stored file content.
 *
 * <p>The stream is opened but not read, so large files are never held in memory. The receiver
 * owns the stream and must close it, or hand it to something that does (for example Spring's
 * {@code InputStreamResource}, which closes it after writing the response).
 *
 * @param document the document's metadata record
 * @param stream a stream over the stored file content
 */
public record DocumentContent(DocumentRecord document, InputStream stream) {}
