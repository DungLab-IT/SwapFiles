package smartdownloadorganizer;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HistoryRecord {

    private final String fileName;
    private final String categoryName;
    private final Path sourcePath;
    private final Path targetPath;
    private final LocalDateTime time;
    private String status;

    public HistoryRecord(
            String fileName,
            String categoryName,
            Path sourcePath,
            Path targetPath,
            String status
    ) {
        this.fileName = fileName;
        this.categoryName = categoryName;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.status = status;
        this.time = LocalDateTime.now();
    }

    public String getFileName() {
        return fileName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public Path getTargetPath() {
        return targetPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimeText() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        return time.format(formatter);
    }
}