package logs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AuditLogger {
    private final Path logFile;

    public AuditLogger(Path logFile) {
        this.logFile = logFile;
    }

    public void log(String username, String action) {
        String line = AuditEntry.createNow(username, action).toLine();
        try {
            Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    Files.exists(logFile)
                            ? java.nio.file.StandardOpenOption.APPEND
                            : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write audit log", ex);
        }
    }
}
