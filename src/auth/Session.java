package auth;

public class Session {
    private final String username;
    private final String role;
    private final long loginEpochMs;
    private long lastActiveEpochMs;

    public Session(String username, String role) {
        this.username = username;
        this.role = role;
        this.loginEpochMs = System.currentTimeMillis();
        this.lastActiveEpochMs = loginEpochMs;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public long getLoginEpochMs() {
        return loginEpochMs;
    }

    public long getLastActiveEpochMs() {
        return lastActiveEpochMs;
    }

    public void touch() {
        this.lastActiveEpochMs = System.currentTimeMillis();
    }
}
