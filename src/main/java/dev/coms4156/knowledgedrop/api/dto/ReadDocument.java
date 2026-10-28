package dev.coms4156.knowledgedrop.api.dto;

import java.io.InputStream;

/**
 * An open document: its file metadata plus a stream over the stored file content.
 *
 * <p>The stream is opened but not read, so large files are never held in memory. The receiver
 * owns the stream and must close it, or hand it to something that does (for example Spring's
 * {@code InputStreamResource}, which closes it after writing the response).
 *
 * @param fileName the document name (the original filename it was created with)
 * @param fileType the document's content type
 * @param fileSize the size of the stored file in bytes
 * @param inputStream a stream over the stored file content
 */
public record ReadDocument(String fileName, String fileType, long fileSize,
    InputStream inputStream) {}
