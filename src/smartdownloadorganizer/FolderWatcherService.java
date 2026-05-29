package smartdownloadorganizer;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class FolderWatcherService implements Runnable {

    private static final long DEBOUNCE_DELAY_MS = 900;
    private static final long STABLE_CHECK_INTERVAL_MS = 500;
    private static final int REQUIRED_STABLE_COUNT = 2;
    private static final int MAX_WAIT_SECONDS = 20;

    private final Path watchFolder;
    private final FileOrganizerService organizerService;
    private final LogCallback logCallback;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private WatchService watchService;

    private final ScheduledExecutorService executorService =
            Executors.newSingleThreadScheduledExecutor();

    private final Map<Path, ScheduledFuture<?>> pendingFiles =
            new ConcurrentHashMap<>();

    public interface LogCallback {
        void log(String message);
    }

    public FolderWatcherService(
            Path watchFolder,
            FileOrganizerService organizerService,
            LogCallback logCallback
    ) {
        this.watchFolder = watchFolder.toAbsolutePath().normalize();
        this.organizerService = organizerService;
        this.logCallback = logCallback;
    }

    @Override
    public void run() {
        try {
            if (!Files.exists(watchFolder)) {
                log("Thư mục theo dõi không tồn tại: " + watchFolder);
                return;
            }

            if (!Files.isDirectory(watchFolder)) {
                log("Đường dẫn theo dõi không phải thư mục: " + watchFolder);
                return;
            }

            watchService = FileSystems.getDefault().newWatchService();

            watchFolder.register(
                    watchService,
                    ENTRY_CREATE,
                    ENTRY_MODIFY
            );

            log("Đã bật tự động theo dõi thư mục: " + watchFolder);

            while (running.get()) {
                WatchKey key;

                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException e) {
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == ENTRY_CREATE || kind == ENTRY_MODIFY) {
                        Path fileName = (Path) event.context();

                        Path filePath = watchFolder
                                .resolve(fileName)
                                .toAbsolutePath()
                                .normalize();

                        scheduleFileProcessing(filePath);
                    }
                }

                boolean valid = key.reset();

                if (!valid) {
                    log("WatchKey không còn hợp lệ. Đã dừng theo dõi.");
                    break;
                }
            }

        } catch (IOException e) {
            log("Lỗi khi theo dõi thư mục: " + e.getMessage());
        } finally {
            shutdownExecutor();
            log("Đã dừng tự động theo dõi.");
        }
    }

    private void scheduleFileProcessing(Path filePath) {
        ScheduledFuture<?> oldTask = pendingFiles.get(filePath);

        if (oldTask != null) {
            oldTask.cancel(false);
        }

        ScheduledFuture<?> newTask = executorService.schedule(() -> {
            try {
                processFileSafely(filePath);
            } finally {
                pendingFiles.remove(filePath);
            }
        }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);

        pendingFiles.put(filePath, newTask);
    }

    private void processFileSafely(Path filePath) {
        try {
            if (!running.get()) {
                return;
            }

            if (!Files.exists(filePath)) {
                return;
            }

            if (!Files.isRegularFile(filePath)) {
                return;
            }

            String fileName = filePath.getFileName().toString();

            if (isTemporaryDownloadFile(fileName)) {
                log("Bỏ qua file đang tải dở: " + fileName);
                return;
            }

            boolean ready = waitUntilFileReady(filePath);

            if (!ready) {
                log("File chưa sẵn sàng hoặc vẫn đang tải: " + fileName);
                return;
            }

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return;
            }

            log("Phát hiện file mới: " + fileName);

            organizerService.organizeFile(filePath);

        } catch (Exception e) {
            log("Lỗi xử lý file mới: " + e.getMessage());
        }
    }

    private boolean waitUntilFileReady(Path filePath) {
        try {
            long previousSize = -1;
            int stableCount = 0;

            int maxLoop = (int) ((MAX_WAIT_SECONDS * 1000L) / STABLE_CHECK_INTERVAL_MS);

            for (int i = 0; i < maxLoop; i++) {
                if (!running.get()) {
                    return false;
                }

                if (!Files.exists(filePath)) {
                    return false;
                }

                long currentSize = Files.size(filePath);

                if (currentSize == previousSize) {
                    stableCount++;

                    if (stableCount >= REQUIRED_STABLE_COUNT) {
                        return true;
                    }
                } else {
                    stableCount = 0;
                }

                previousSize = currentSize;

                Thread.sleep(STABLE_CHECK_INTERVAL_MS);
            }

        } catch (Exception e) {
            log("Không thể kiểm tra trạng thái file: " + e.getMessage());
        }

        return false;
    }

    private boolean isTemporaryDownloadFile(String fileName) {
        String lower = fileName.toLowerCase();

        return lower.endsWith(".crdownload")
                || lower.endsWith(".part")
                || lower.endsWith(".tmp")
                || lower.endsWith(".download")
                || lower.endsWith(".opdownload");
    }

    public void stopWatching() {
        running.set(false);

        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            log("Lỗi khi dừng WatchService: " + e.getMessage());
        }

        shutdownExecutor();
    }

    private void shutdownExecutor() {
        for (ScheduledFuture<?> task : pendingFiles.values()) {
            task.cancel(false);
        }

        pendingFiles.clear();

        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.log(message);
        }
    }
}