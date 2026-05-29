package smartdownloadorganizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryService {

    private final List<HistoryRecord> historyRecords = new ArrayList<>();

    public interface HistoryChangeCallback {
        void onHistoryChanged();
    }

    private HistoryChangeCallback historyChangeCallback;

    public void setHistoryChangeCallback(HistoryChangeCallback historyChangeCallback) {
        this.historyChangeCallback = historyChangeCallback;
    }

    public void addRecord(HistoryRecord record) {
        historyRecords.add(0, record);
        notifyChanged();
    }

    public List<HistoryRecord> getHistoryRecords() {
        return Collections.unmodifiableList(historyRecords);
    }

    public void clearHistory() {
        historyRecords.clear();
        notifyChanged();
    }

    public boolean undoMove(int index) throws IOException {
        if (index < 0 || index >= historyRecords.size()) {
            return false;
        }

        HistoryRecord record = historyRecords.get(index);

        Path currentPath = record.getTargetPath();
        Path originalPath = record.getSourcePath();

        if (!Files.exists(currentPath)) {
            record.setStatus("Không thể hoàn tác: file đích không còn tồn tại");
            notifyChanged();
            return false;
        }

        Path originalFolder = originalPath.getParent();

        if (originalFolder != null && !Files.exists(originalFolder)) {
            Files.createDirectories(originalFolder);
        }

        Path restoredPath = getUniquePath(originalPath);

        Files.move(currentPath, restoredPath, StandardCopyOption.REPLACE_EXISTING);

        record.setStatus("Đã hoàn tác về: " + restoredPath);
        notifyChanged();

        return true;
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
            String newFileName = name + " - restored (" + count + ")" + extension;
            newPath = parent.resolve(newFileName);
            count++;
        } while (Files.exists(newPath));

        return newPath;
    }

    private void notifyChanged() {
        if (historyChangeCallback != null) {
            historyChangeCallback.onHistoryChanged();
        }
    }
}