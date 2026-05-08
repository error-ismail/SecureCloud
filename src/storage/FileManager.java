package storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileManager {
    private final Path storageRoot;

    public FileManager(Path storageRoot) {
        this.storageRoot = storageRoot;
    }

    public void ensureUserFolder(String username) {
        Path userDir = storageRoot.resolve(username);
        try {
            Files.createDirectories(userDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create user storage", ex);
        }
    }

    public Path resolveEncryptedPath(String owner, String fileId) {
        return storageRoot.resolve(owner).resolve(fileId);
    }

    public void deleteEncryptedFile(String owner, String fileId) {
        Path path = resolveEncryptedPath(owner, fileId);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete encrypted file", ex);
        }
    }

    public long getUserUsageBytes(String owner) {
        Path userDir = storageRoot.resolve(owner);
        if (!Files.exists(userDir)) {
            return 0L;
        }
        try (var stream = Files.list(userDir)) {
            return stream.mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException ex) {
                    return 0L;
                }
            }).sum();
        } catch (IOException ex) {
            return 0L;
        }
    }
}
