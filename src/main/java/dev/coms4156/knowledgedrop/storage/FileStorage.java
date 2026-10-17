package dev.coms4156.knowledgedrop.storage;

import dev.coms4156.knowledgedrop.config.KnowledgeDropProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Stores original uploaded files on disk at {@code {storageDir}/{clientId}/{fileName}}.
 *
 * <p>File names come from user input, so every path is normalized and checked to stay inside the
 * client's directory.
 */
@Component
public class FileStorage {

  private final Path baseDir;

  /**
   * Creates the storage rooted at the configured storage directory.
   *
   * @param properties application properties holding {@code storageDir}
   */
  public FileStorage(KnowledgeDropProperties properties) {
    this.baseDir = Path.of(properties.storageDir()).toAbsolutePath().normalize();
  }

  /**
   * Writes the stream to {@code {storageDir}/{clientId}/{fileName}}, replacing any existing file.
   * The stream is read to the end but not closed.
   *
   * @param clientId ID of the owning client
   * @param fileName original filename of the upload
   * @param in the file content
   * @return the path the file was written to
   * @throws IOException if the file cannot be written
   */
  public Path save(UUID clientId, String fileName, InputStream in) throws IOException {
    Path target = resolve(clientId, fileName);
    Files.createDirectories(target.getParent());
    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    return target;
  }

  /**
   * Opens a stored file for reading. The caller must close the returned stream.
   *
   * @param clientId ID of the owning client
   * @param fileName original filename of the upload
   * @return a stream over the stored content
   * @throws IOException if the file does not exist or cannot be read
   */
  public InputStream open(UUID clientId, String fileName) throws IOException {
    return Files.newInputStream(resolve(clientId, fileName));
  }

  /**
   * Resolves the target path and rejects any file name that would escape the client's directory
   * (for example {@code ../../x}), so files always land in {@code baseDir/clientId/}.
   */
  private Path resolve(UUID clientId, String fileName) {
    if (clientId == null || fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("Client ID and file name must not be empty");
    }
    Path clientDir = baseDir.resolve(clientId.toString()).normalize();
    Path target = clientDir.resolve(fileName).normalize();
    if (!target.startsWith(clientDir) || target.equals(clientDir)) {
      throw new IllegalArgumentException("Invalid file name");
    }
    return target;
  }
}
