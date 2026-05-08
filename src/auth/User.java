package auth;

public class User {
    private final String username;
    private final String email;
    private final String passwordHash;
    private final String salt;
    private final String role;
    private final long lockUntilEpochMs;
    private final int failedAttempts;
    private final long lastActiveEpochMs;

    public User(String username, String email, String passwordHash, String salt, String role,
                long lockUntilEpochMs, int failedAttempts, long lastActiveEpochMs) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
        this.lockUntilEpochMs = lockUntilEpochMs;
        this.failedAttempts = failedAttempts;
        this.lastActiveEpochMs = lastActiveEpochMs;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public String getRole() {
        return role;
    }

    public long getLockUntilEpochMs() {
        return lockUntilEpochMs;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public long getLastActiveEpochMs() {
        return lastActiveEpochMs;
    }

    public String toLine() {
        return String.join("|",
                username,
                email,
                passwordHash,
                salt,
            role,
                String.valueOf(lockUntilEpochMs),
                String.valueOf(failedAttempts),
                String.valueOf(lastActiveEpochMs));
    }

    public static User fromLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 7) {
            return null;
        }
        if (parts.length == 7) {
            return new User(
                parts[0],
                parts[1],
                parts[2],
                parts[3],
                "user",
                parseLong(parts[4]),
                parseInt(parts[5]),
                parseLong(parts[6])
            );
        }
        return new User(
            parts[0],
            parts[1],
            parts[2],
            parts[3],
            parts[4],
            parseLong(parts[5]),
            parseInt(parts[6]),
            parseLong(parts[7])
        );
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
