package access;

import storage.FileMetadata;

public class PermissionManager {
    public boolean canAccess(String username, FileMetadata meta) {
        if (meta.getOwner().equalsIgnoreCase(username)) {
            return true;
        }
        String perm = meta.getPermission().toLowerCase();
        if (perm.equals("public")) {
            return true;
        }
        if (perm.equals("shared")) {
            return isSharedWith(username, meta);
        }
        return false;
    }

    public boolean isSharedWith(String username, FileMetadata meta) {
        for (String user : meta.getSharedWith()) {
            if (user.equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }
}
