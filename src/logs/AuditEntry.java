package logs;

import java.time.Instant;

public class AuditEntry {
    private final String timestampIso;
    private final String username;
    private final String action;

    public AuditEntry(String timestampIso, String username, String action) {
        this.timestampIso = timestampIso;
        this.username = username;
        this.action = action;
    }

    public String getTimestampIso() {
        return timestampIso;
    }

    public String getUsername() {
        return username;
    }

    public String getAction() {
        return action;
    }

    public String toLine() {
        return String.join("|", timestampIso, username, action);
    }

    public static AuditEntry fromLine(String line) {
        String[] parts = line.split("\\|", 3);
        if (parts.length < 3) {
            return null;
        }
        return new AuditEntry(parts[0], parts[1], parts[2]);
    }

    public static AuditEntry createNow(String username, String action) {
        return new AuditEntry(Instant.now().toString(), username, action);
    }
}
