package ui;

import access.PermissionManager;
import auth.AuthService;
import auth.Session;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import logs.AuditLogger;
import storage.CloudManager;
import storage.FileMetadata;

public class ConsoleUI {
    private static final long SESSION_TIMEOUT_MS = 10 * 60 * 1000L;

    private final Scanner scanner = new Scanner(System.in);
    private final AuthService authService;
    private final AuditLogger auditLogger;
    private final CloudManager cloudManager;

    public ConsoleUI() {
        Path dataDir = Path.of("data");
        this.auditLogger = new AuditLogger(dataDir.resolve("logs.txt"));
        this.authService = new AuthService(dataDir.resolve("users.txt"), auditLogger);
        this.cloudManager = new CloudManager(dataDir, auditLogger, new PermissionManager());
    }

    public void start() {
        ensureDataFiles();
        while (true) {
            System.out.println("\n=== SecureCloud ===");
            System.out.println("1) Register");
            System.out.println("2) Login");
            System.out.println("3) Exit");
            System.out.print("Choose: ");
            String choice = scanner.nextLine().trim();

            if ("1".equals(choice)) {
                handleRegister();
            } else if ("2".equals(choice)) {
                Session session = handleLogin();
                if (session != null) {
                    handleDashboard(session);
                }
            } else if ("3".equals(choice)) {
                System.out.println("Goodbye.");
                return;
            } else {
                System.out.println("Invalid choice.");
            }
        }
    }

    private void ensureDataFiles() {
        cloudManager.ensureDataLayout();
        authService.ensureDataLayout();
    }

    private void handleRegister() {
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Email: ");
        String email = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        boolean ok = authService.register(username, email, password);
        System.out.println(ok ? "Registered successfully." : "Registration failed.");
    }

    private Session handleLogin() {
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        Session session = authService.login(username, password);
        if (session == null) {
            System.out.println("Login failed.");
            return null;
        }
        System.out.println("Login success.");
        return session;
    }

    private void handleDashboard(Session session) {
        while (true) {
            if (isSessionExpired(session)) {
                System.out.println("Session timeout. Logged out.");
                return;
            }
            System.out.println("\n--- Dashboard (" + session.getUsername() + ") ---");
            System.out.println("1) Upload file");
            System.out.println("2) Download file");
            System.out.println("3) Verify file");
            System.out.println("4) Share file");
            System.out.println("5) List my files");
            System.out.println("6) List shared with me");
            System.out.println("7) Delete file");
            System.out.println("8) Rename file");
            System.out.println("9) Move file (change folder)");
            System.out.println("0) Logout");
            System.out.print("Choose: ");
            String choice = scanner.nextLine().trim();

            session.touch();
            if ("1".equals(choice)) {
                handleUpload(session);
            } else if ("2".equals(choice)) {
                handleDownload(session);
            } else if ("3".equals(choice)) {
                handleVerify(session);
            } else if ("4".equals(choice)) {
                handleShare(session);
            } else if ("5".equals(choice)) {
                handleListMyFiles(session);
            } else if ("6".equals(choice)) {
                handleListSharedWithMe(session);
            } else if ("7".equals(choice)) {
                handleDelete(session);
            } else if ("8".equals(choice)) {
                handleRename(session);
            } else if ("9".equals(choice)) {
                handleMove(session);
            } else if ("0".equals(choice)) {
                System.out.println("Logged out.");
                return;
            } else {
                System.out.println("Invalid choice.");
            }
        }
    }

    private boolean isSessionExpired(Session session) {
        long idle = System.currentTimeMillis() - session.getLastActiveEpochMs();
        return idle > SESSION_TIMEOUT_MS;
    }

    private void handleUpload(Session session) {
        System.out.print("Local file path: ");
        String path = scanner.nextLine().trim();
        System.out.print("Permission (private/shared/public): ");
        String perm = scanner.nextLine().trim();
        System.out.print("Folder (e.g., Documents): ");
        String folder = scanner.nextLine().trim();

        boolean ok = cloudManager.uploadFile(session.getUsername(), path, perm, folder);
        System.out.println(ok ? "Upload success." : "Upload failed.");
    }

    private void handleDownload(Session session) {
        System.out.print("File ID (from list): ");
        String fileId = scanner.nextLine().trim();
        System.out.print("Destination path: ");
        String dest = scanner.nextLine().trim();

        boolean ok = cloudManager.downloadFile(session.getUsername(), fileId, dest);
        System.out.println(ok ? "Download success." : "Download failed.");
    }

    private void handleVerify(Session session) {
        System.out.print("File ID (from list): ");
        String fileId = scanner.nextLine().trim();

        System.out.println("Verifying as: " + session.getUsername());

        CloudManager.VerifyResult result = cloudManager.verifyFile(fileId);
        if (result == null) {
            System.out.println("Verify failed.");
            return;
        }
        if (result.error != null) {
            System.out.println("Verify error: " + result.error);
            return;
        }
        System.out.println("Integrity: " + (result.integrityOk ? "OK" : "FAILED"));
        System.out.println("Signature: " + (result.signatureOk ? "OK" : "FAILED"));
        if (result.integrityOk && result.signatureOk) {
            System.out.println("Verified owner: " + result.owner);
        }
    }

    private void handleShare(Session session) {
        System.out.print("File ID (from list): ");
        String fileId = scanner.nextLine().trim();
        System.out.print("Share with username: ");
        String target = scanner.nextLine().trim();

        boolean ok = cloudManager.shareFile(session.getUsername(), fileId, target);
        System.out.println(ok ? "Shared." : "Share failed.");
    }

    private void handleListMyFiles(Session session) {
        List<FileMetadata> files = cloudManager.listFilesOwnedBy(session.getUsername());
        if (files.isEmpty()) {
            System.out.println("No files.");
            return;
        }
        for (FileMetadata f : files) {
            System.out.println(f.toDisplayString());
        }
    }

    private void handleListSharedWithMe(Session session) {
        List<FileMetadata> files = cloudManager.listFilesSharedWith(session.getUsername());
        if (files.isEmpty()) {
            System.out.println("No shared files.");
            return;
        }
        for (FileMetadata f : files) {
            System.out.println(f.toDisplayString());
        }
    }

    private void handleDelete(Session session) {
        System.out.print("File ID (from list): ");
        String fileId = scanner.nextLine().trim();
        boolean ok = cloudManager.deleteFile(session.getUsername(), fileId);
        System.out.println(ok ? "Deleted." : "Delete failed.");
    }

    private void handleRename(Session session) {
        System.out.print("File ID (from list): ");
        String fileId = scanner.nextLine().trim();
        System.out.print("New name: ");
        String newName = scanner.nextLine().trim();
        boolean ok = cloudManager.renameFile(session.getUsername(), fileId, newName);
        System.out.println(ok ? "Renamed." : "Rename failed.");
    }

    private void handleMove(Session session) {
        System.out.print("File ID (from list): ");
        String fileId = scanner.nextLine().trim();
        System.out.print("New folder: ");
        String folder = scanner.nextLine().trim();
        boolean ok = cloudManager.moveFile(session.getUsername(), fileId, folder);
        System.out.println(ok ? "Moved." : "Move failed.");
    }
}
