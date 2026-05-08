package storage;

import access.PermissionManager;
import crypto.AESUtil;
import crypto.HashUtil;
import crypto.RSAUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import logs.AuditLogger;

public class CloudManager {
    private static final long USER_QUOTA_BYTES = 100L * 1024L * 1024L;

    private final Path dataDir;
    private final FileManager fileManager;
    private final MetadataStore metadataStore;
    private final AuditLogger auditLogger;
    private final PermissionManager permissionManager;

    public CloudManager(Path dataDir, AuditLogger auditLogger, PermissionManager permissionManager) {
        this.dataDir = dataDir;
        this.auditLogger = auditLogger;
        this.permissionManager = permissionManager;
        this.fileManager = new FileManager(dataDir.resolve("storage"));
        this.metadataStore = new MetadataStore(dataDir.resolve("files.txt"));
    }

    public void ensureDataLayout() {
        try {
            Files.createDirectories(dataDir.resolve("storage"));
            Files.createDirectories(dataDir.resolve("keys"));
            Files.createDirectories(dataDir.resolve("tmp"));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to init data folders", ex);
        }
        metadataStore.ensureDataLayout();
    }

    public boolean uploadFile(String username, String localPath, String permission, String folder) {
        try {
            Path input = Path.of(localPath);
            if (!Files.exists(input)) {
                return false;
            }
            fileManager.ensureUserFolder(username);
            long usage = fileManager.getUserUsageBytes(username);
            long size = Files.size(input);
            if (usage + size > USER_QUOTA_BYTES) {
                auditLogger.log(username, "Quota exceeded");
                return false;
            }

            SecretKey aesKey = AESUtil.loadOrCreateKey(dataDir.resolve("system.key"));
            KeyPair keyPair = RSAUtil.loadOrCreateKeyPair(dataDir.resolve("keys"), username);

            String fileId = UUID.randomUUID().toString() + ".enc";
            Path encrypted = fileManager.resolveEncryptedPath(username, fileId);

            AESUtil.encryptFile(input, encrypted, aesKey);

            byte[] hash = HashUtil.sha256Bytes(input);
            String hashHex = HashUtil.toHex(hash);
            String signature = RSAUtil.signHash(hash, keyPair.getPrivate());

            FileMetadata meta = new FileMetadata(
                    fileId,
                    input.getFileName().toString(),
                    username,
                    hashHex,
                    System.currentTimeMillis(),
                    signature,
                    normalizePermission(permission),
                    new ArrayList<>(),
                    folder == null || folder.isBlank() ? "root" : folder
            );

            List<FileMetadata> all = metadataStore.loadAll();
            all.add(meta);
            metadataStore.saveAll(all);

            auditLogger.log(username, "Uploaded file: " + meta.getOriginalName());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean downloadFile(String username, String fileId, String destinationPath) {
        FileMetadata meta = findById(fileId);
        if (meta == null) {
            return false;
        }
        if (!permissionManager.canAccess(username, meta)) {
            auditLogger.log(username, "Download denied for fileId=" + fileId);
            return false;
        }
        try {
            SecretKey aesKey = AESUtil.loadOrCreateKey(dataDir.resolve("system.key"));
            Path encrypted = fileManager.resolveEncryptedPath(meta.getOwner(), meta.getId());
            Path dest = Path.of(destinationPath);
            AESUtil.decryptFile(encrypted, dest, aesKey);
            auditLogger.log(username, "Downloaded file: " + meta.getOriginalName());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public VerifyResult verifyFile(String fileId) {
        FileMetadata meta = findById(fileId);
        if (meta == null) {
            return null;
        }
        Path temp = dataDir.resolve("tmp").resolve(UUID.randomUUID().toString());
        try {
            SecretKey aesKey = AESUtil.loadOrCreateKey(dataDir.resolve("system.key"));
            Path encrypted = fileManager.resolveEncryptedPath(meta.getOwner(), meta.getId());
            AESUtil.decryptFile(encrypted, temp, aesKey);

            byte[] hash = HashUtil.sha256Bytes(temp);
            String hashHex = HashUtil.toHex(hash);
            boolean integrityOk = hashHex.equals(meta.getHashHex());

            PublicKey publicKey = RSAUtil.loadPublicKey(dataDir.resolve("keys"), meta.getOwner());
            if (publicKey == null) {
                return new VerifyResult(meta.getOwner(), false, false,
                        "Owner public key not found");
            }
            boolean signatureOk = RSAUtil.verifyHash(hash, meta.getSignatureBase64(), publicKey);

            if (!integrityOk) {
                return new VerifyResult(meta.getOwner(), false, signatureOk,
                        "Integrity check failed (file modified)");
            }
            if (!signatureOk) {
                return new VerifyResult(meta.getOwner(), true, false,
                        "Signature invalid (owner mismatch)");
            }
            return new VerifyResult(meta.getOwner(), true, true, null);
        } catch (Exception ex) {
            return new VerifyResult(meta.getOwner(), false, false,
                    "Encrypted file corrupted or unreadable");
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ex) {
                // ignore
            }
        }
    }

    public boolean shareFile(String owner, String fileId, String targetUser) {
        List<FileMetadata> all = metadataStore.loadAll();
        boolean updated = false;
        List<FileMetadata> next = new ArrayList<>();

        for (FileMetadata meta : all) {
            if (meta.getId().equals(fileId) && meta.getOwner().equalsIgnoreCase(owner)) {
                List<String> shared = new ArrayList<>(meta.getSharedWith());
                if (!shared.contains(targetUser)) {
                    shared.add(targetUser);
                }
                FileMetadata updatedMeta = new FileMetadata(
                        meta.getId(), meta.getOriginalName(), meta.getOwner(), meta.getHashHex(),
                        meta.getTimestampEpochMs(), meta.getSignatureBase64(),
                        "shared", shared, meta.getFolder()
                );
                next.add(updatedMeta);
                updated = true;
                auditLogger.log(owner, "Shared with " + targetUser + ": " + meta.getOriginalName());
            } else {
                next.add(meta);
            }
        }
        if (updated) {
            metadataStore.saveAll(next);
        }
        return updated;
    }

    public boolean deleteFile(String owner, String fileId) {
        List<FileMetadata> all = metadataStore.loadAll();
        List<FileMetadata> next = new ArrayList<>();
        boolean removed = false;

        for (FileMetadata meta : all) {
            if (meta.getId().equals(fileId) && meta.getOwner().equalsIgnoreCase(owner)) {
                fileManager.deleteEncryptedFile(meta.getOwner(), meta.getId());
                removed = true;
                auditLogger.log(owner, "Deleted file: " + meta.getOriginalName());
            } else {
                next.add(meta);
            }
        }

        if (removed) {
            metadataStore.saveAll(next);
        }
        return removed;
    }

    public boolean renameFile(String owner, String fileId, String newName) {
        return updateMetadata(owner, fileId, newName, null);
    }

    public boolean moveFile(String owner, String fileId, String newFolder) {
        return updateMetadata(owner, fileId, null, newFolder);
    }

    private boolean updateMetadata(String owner, String fileId, String newName, String newFolder) {
        List<FileMetadata> all = metadataStore.loadAll();
        List<FileMetadata> next = new ArrayList<>();
        boolean updated = false;

        for (FileMetadata meta : all) {
            if (meta.getId().equals(fileId) && meta.getOwner().equalsIgnoreCase(owner)) {
                FileMetadata updatedMeta = new FileMetadata(
                        meta.getId(),
                        newName == null ? meta.getOriginalName() : newName,
                        meta.getOwner(),
                        meta.getHashHex(),
                        meta.getTimestampEpochMs(),
                        meta.getSignatureBase64(),
                        meta.getPermission(),
                        meta.getSharedWith(),
                        newFolder == null ? meta.getFolder() : newFolder
                );
                next.add(updatedMeta);
                updated = true;
                auditLogger.log(owner, "Updated metadata for fileId=" + meta.getId());
            } else {
                next.add(meta);
            }
        }
        if (updated) {
            metadataStore.saveAll(next);
        }
        return updated;
    }

    public List<FileMetadata> listFilesOwnedBy(String owner) {
        List<FileMetadata> result = new ArrayList<>();
        for (FileMetadata meta : metadataStore.loadAll()) {
            if (meta.getOwner().equalsIgnoreCase(owner)) {
                result.add(meta);
            }
        }
        return result;
    }

    public List<FileMetadata> listFilesSharedWith(String username) {
        List<FileMetadata> result = new ArrayList<>();
        for (FileMetadata meta : metadataStore.loadAll()) {
            if (!meta.getOwner().equalsIgnoreCase(username)
                    && permissionManager.canAccess(username, meta)) {
                result.add(meta);
            }
        }
        return result;
    }

    private FileMetadata findById(String fileId) {
        for (FileMetadata meta : metadataStore.loadAll()) {
            if (meta.getId().equals(fileId)) {
                return meta;
            }
        }
        return null;
    }

    private String normalizePermission(String permission) {
        if (permission == null) {
            return "private";
        }
        String lower = permission.trim().toLowerCase();
        if (lower.equals("public") || lower.equals("shared")) {
            return lower;
        }
        return "private";
    }

    public static class VerifyResult {
        public final String owner;
        public final boolean integrityOk;
        public final boolean signatureOk;
        public final String error;

        public VerifyResult(String owner, boolean integrityOk, boolean signatureOk, String error) {
            this.owner = owner;
            this.integrityOk = integrityOk;
            this.signatureOk = signatureOk;
            this.error = error;
        }
    }
}
