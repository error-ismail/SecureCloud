package storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileMetadata {
    private final String id;
    private final String originalName;
    private final String owner;
    private final String hashHex;
    private final long timestampEpochMs;
    private final String signatureBase64;
    private final String permission;
    private final List<String> sharedWith;
    private final String folder;

    public FileMetadata(String id, String originalName, String owner, String hashHex,
                        long timestampEpochMs, String signatureBase64, String permission,
                        List<String> sharedWith, String folder) {
        this.id = id;
        this.originalName = originalName;
        this.owner = owner;
        this.hashHex = hashHex;
        this.timestampEpochMs = timestampEpochMs;
        this.signatureBase64 = signatureBase64;
        this.permission = permission;
        this.sharedWith = sharedWith;
        this.folder = folder;
    }

    public String getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getOwner() {
        return owner;
    }

    public String getHashHex() {
        return hashHex;
    }

    public long getTimestampEpochMs() {
        return timestampEpochMs;
    }

    public String getSignatureBase64() {
        return signatureBase64;
    }

    public String getPermission() {
        return permission;
    }

    public List<String> getSharedWith() {
        return sharedWith;
    }

    public String getFolder() {
        return folder;
    }

    public String toLine() {
        String shared = String.join(",", sharedWith);
        return String.join("|",
                id,
                originalName,
                owner,
                hashHex,
                String.valueOf(timestampEpochMs),
                signatureBase64,
                permission,
                shared,
                folder);
    }

    public static FileMetadata fromLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 9) {
            return null;
        }
        List<String> shared = new ArrayList<>();
        if (!parts[7].isBlank()) {
            shared.addAll(Arrays.asList(parts[7].split(",")));
        }
        return new FileMetadata(
                parts[0],
                parts[1],
                parts[2],
                parts[3],
                parseLong(parts[4]),
                parts[5],
                parts[6],
                shared,
                parts[8]
        );
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public String toDisplayString() {
        return "ID=" + id + " | name=" + originalName + " | owner=" + owner +
                " | perm=" + permission + " | folder=" + folder;
    }
}
