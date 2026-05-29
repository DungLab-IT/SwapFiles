package smartdownloadorganizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileOrganizerService {

    private final CategoryManager categoryManager;
    private final HistoryService historyService;
    private final LogCallback logCallback;

    public interface LogCallback {
        void log(String message);
    }

    public FileOrganizerService(
            CategoryManager categoryManager,
            HistoryService historyService,
            LogCallback logCallback
    ) {
        this.categoryManager = categoryManager;
        this.historyService = historyService;
        this.logCallback = logCallback;
    }

    public void organizeFolder(Path sourceFolder) {
        try {
            if (sourceFolder == null) {
                log("Thư mục nguồn không hợp lệ.");
                return;
            }

            sourceFolder = sourceFolder.toAbsolutePath().normalize();

            if (!Files.exists(sourceFolder)) {
                log("Thư mục nguồn không tồn tại: " + sourceFolder);
                return;
            }

            if (!Files.isDirectory(sourceFolder)) {
                log("Đường dẫn nguồn không phải là thư mục: " + sourceFolder);
                return;
            }

            Files.list(sourceFolder)
                    .filter(Files::isRegularFile)
                    .forEach(this::organizeFile);

            log("Hoàn tất sắp xếp thư mục: " + sourceFolder);

        } catch (IOException e) {
            log("Lỗi khi quét thư mục: " + e.getMessage());
        }
    }

    public void organizeFile(Path filePath) {
        try {
            if (filePath == null) {
                return;
            }

            filePath = filePath.toAbsolutePath().normalize();

            if (!Files.exists(filePath)) {
                return;
            }

            if (!Files.isRegularFile(filePath)) {
                return;
            }

            String fileName = filePath.getFileName().toString();

            if (isTemporaryDownload(fileName)) {
                log("Bỏ qua file đang tải dở: " + fileName);
                return;
            }

            String extension = getExtension(fileName);

            Category category = categoryManager.findCategoryByExtension(extension);

            if (category == null) {
                log("Không tìm thấy danh mục cho file: " + fileName);
                return;
            }

            if (!category.isEnabled()) {
                log("Danh mục đang tắt, bỏ qua file: " + fileName);
                return;
            }

            Path targetFolder = category.getTargetFolder();

            if (targetFolder == null) {
                log("Danh mục chưa có thư mục lưu: " + category.getName());
                return;
            }

            targetFolder = targetFolder.toAbsolutePath().normalize();

            if (!Files.exists(targetFolder)) {
                Files.createDirectories(targetFolder);
            }

            if (!Files.isDirectory(targetFolder)) {
                log("Thư mục đích không hợp lệ: " + targetFolder);
                return;
            }

            /*
             * Nếu file đã nằm đúng thư mục đích rồi thì bỏ qua.
             * Tránh trường hợp Auto Watch bắt lại file trong thư mục đích nếu người dùng theo dõi nhầm.
             */
            Path sourceParent = filePath.getParent();

            if (sourceParent != null
                    && sourceParent.toAbsolutePath().normalize().equals(targetFolder)) {
                log("File đã nằm trong đúng thư mục đích, bỏ qua: " + fileName);
                return;
            }

            Path originalPath = filePath.toAbsolutePath().normalize();

            Path targetPath = getUniquePath(targetFolder.resolve(fileName));
            targetPath = targetPath.toAbsolutePath().normalize();

            Files.move(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            log("Đã chuyển: "
                    + fileName
                    + " → "
                    + category.getName()
                    + " → "
                    + targetPath
            );

            if (historyService != null) {
                historyService.addRecord(new HistoryRecord(
                        fileName,
                        category.getName(),
                        originalPath,
                        targetPath,
                        "Thành công"
                ));
            }

        } catch (Exception e) {
            String name = "Không xác định";

            try {
                if (filePath != null && filePath.getFileName() != null) {
                    name = filePath.getFileName().toString();
                }
            } catch (Exception ignored) {
            }

            log("Lỗi xử lý file: " + name + " | " + e.getMessage());
        }
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");

        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    private boolean isTemporaryDownload(String fileName) {
        String lower = fileName.toLowerCase();

        return lower.endsWith(".crdownload")
                || lower.endsWith(".part")
                || lower.endsWith(".tmp")
                || lower.endsWith(".download")
                || lower.endsWith(".opdownload");
    }

    private Path getUniquePath(Path targetPath) {
        if (!Files.exists(targetPath)) {
            return targetPath;
        }

        String fileName = targetPath.getFileName().toString();

        String name;
        String extension;

        int dotIndex = fileName.lastIndexOf(".");

        if (dotIndex == -1) {
            name = fileName;
            extension = "";
        } else {
            name = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        Path parent = targetPath.getParent();
        int count = 1;
        Path newPath;

        do {
            String newFileName = name + " (" + count + ")" + extension;
            newPath = parent.resolve(newFileName);
            count++;
        } while (Files.exists(newPath));

        return newPath;
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.log(message);
        }
    }
}