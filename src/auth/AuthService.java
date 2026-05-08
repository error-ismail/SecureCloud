package auth;

import crypto.HashUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import logs.AuditLogger;

public class AuthService {
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final long LOCK_MS = 5 * 60 * 1000L;

    private final Path usersFile;
    private final AuditLogger auditLogger;

    public AuthService(Path usersFile, AuditLogger auditLogger) {
        this.usersFile = usersFile;
        this.auditLogger = auditLogger;
    }

    public void ensureDataLayout() {
        try {
            if (!Files.exists(usersFile)) {
                Files.createFile(usersFile);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize users file", ex);
        }
    }

    public boolean register(String username, String email, String password) {
        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return false;
        }
        if (username.contains("|") || (email != null && email.contains("|"))) {
            return false;
        }
        List<User> users = loadUsers();
        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                return false;
            }
        }
        String salt = generateSalt();
        String hash = HashUtil.sha256Hex(password + salt);
        String role = users.isEmpty() ? "admin" : "user";
        User user = new User(username, email, hash, salt, role, 0L, 0, System.currentTimeMillis());
        users.add(user);
        saveUsers(users);
        auditLogger.log(username, "User registered");
        return true;
    }

    public Session login(String username, String password) {
        List<User> users = loadUsers();
        List<User> updated = new ArrayList<>();
        Session session = null;
        long now = System.currentTimeMillis();

        for (User user : users) {
            if (!user.getUsername().equalsIgnoreCase(username)) {
                updated.add(user);
                continue;
            }

            if (user.getLockUntilEpochMs() > now) {
                auditLogger.log(username, "Login locked");
                updated.add(user);
                continue;
            }

            String hash = HashUtil.sha256Hex(password + user.getSalt());
            if (hash.equals(user.getPasswordHash())) {
                session = new Session(user.getUsername(), user.getRole());
                User reset = new User(user.getUsername(), user.getEmail(), user.getPasswordHash(),
                        user.getSalt(), user.getRole(), 0L, 0, now);
                updated.add(reset);
                auditLogger.log(username, "Login success");
            } else {
                int failed = user.getFailedAttempts() + 1;
                long lockUntil = user.getLockUntilEpochMs();
                if (failed >= MAX_FAILED_ATTEMPTS) {
                    lockUntil = now + LOCK_MS;
                    failed = 0;
                    auditLogger.log(username, "Account locked (3 failures)");
                } else {
                    auditLogger.log(username, "Login failed");
                }
                User failedUser = new User(user.getUsername(), user.getEmail(), user.getPasswordHash(),
                    user.getSalt(), user.getRole(), lockUntil, failed, user.getLastActiveEpochMs());
                updated.add(failedUser);
            }
        }

        saveUsers(updated);
        return session;
    }

    private List<User> loadUsers() {
        List<User> users = new ArrayList<>();
        if (!Files.exists(usersFile)) {
            return users;
        }
        try {
            List<String> lines = Files.readAllLines(usersFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                User user = User.fromLine(line);
                if (user != null) {
                    users.add(user);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read users", ex);
        }
        return users;
    }

    private void saveUsers(List<User> users) {
        List<String> lines = new ArrayList<>();
        for (User user : users) {
            lines.add(user.toLine());
        }
        try {
            Files.write(usersFile, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write users", ex);
        }
    }

    private String generateSalt() {
        byte[] bytes = new byte[8];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
