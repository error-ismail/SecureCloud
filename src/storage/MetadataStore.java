package storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MetadataStore {
    private final Path filesFile;

    public MetadataStore(Path filesFile) {
        this.filesFile = filesFile;
    }

    public void ensureDataLayout() {
        try {
            if (!Files.exists(filesFile)) {
                Files.createFile(filesFile);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to init files metadata", ex);
        }
    }

    public List<FileMetadata> loadAll() {
        List<FileMetadata> result = new ArrayList<>();
        if (!Files.exists(filesFile)) {
            return result;
        }
        try {
            List<String> lines = Files.readAllLines(filesFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                FileMetadata meta = FileMetadata.fromLine(line);
                if (meta != null) {
                    result.add(meta);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read metadata", ex);
        }
        return result;
    }

    public void saveAll(List<FileMetadata> items) {
        List<String> lines = new ArrayList<>();
        for (FileMetadata item : items) {
            lines.add(item.toLine());
        }
        try {
            Files.write(filesFile, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write metadata", ex);
        }
    }
}
