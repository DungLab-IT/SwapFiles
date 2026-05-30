package smartdownloadorganizer;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class AutoStartService {

    private static final String APP_NAME = "DungLab_SwapFiles";
    private static final String STARTUP_BAT_NAME = APP_NAME + ".bat";

    public boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }

    public boolean enableAutoStart() {
        if (!isWindows()) {
            return false;
        }

        try {
            String launchCommand = buildLaunchCommand();

            if (launchCommand == null || launchCommand.isBlank()) {
                System.out.println("Không tạo được lệnh khởi động app.");
                return false;
            }

            Path startupFolder = getStartupFolder();

            if (startupFolder == null) {
                System.out.println("Không tìm thấy thư mục Startup.");
                return false;
            }

            if (!Files.exists(startupFolder)) {
                Files.createDirectories(startupFolder);
            }

            Path batFile = startupFolder.resolve(STARTUP_BAT_NAME);

            String batContent =
                    "@echo off\r\n"
                    + "start \"\" " + launchCommand + "\r\n";

            try (FileWriter writer = new FileWriter(batFile.toFile(), false)) {
                writer.write(batContent);
            }

            return Files.exists(batFile);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean disableAutoStart() {
        if (!isWindows()) {
            return false;
        }

        try {
            Path startupFolder = getStartupFolder();

            if (startupFolder == null) {
                return false;
            }

            Path batFile = startupFolder.resolve(STARTUP_BAT_NAME);

            if (Files.exists(batFile)) {
                Files.delete(batFile);
            }

            return !Files.exists(batFile);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isAutoStartEnabled() {
        if (!isWindows()) {
            return false;
        }

        try {
            Path startupFolder = getStartupFolder();

            if (startupFolder == null) {
                return false;
            }

            Path batFile = startupFolder.resolve(STARTUP_BAT_NAME);

            return Files.exists(batFile);

        } catch (Exception e) {
            return false;
        }
    }

    public Path getAutoStartFilePath() {
        Path startupFolder = getStartupFolder();

        if (startupFolder == null) {
            return null;
        }

        return startupFolder.resolve(STARTUP_BAT_NAME);
    }

    private String buildLaunchCommand() {
        /*
         * Trường hợp 1:
         * App đang chạy từ bản đã cài bằng jpackage.
         * Khi đó ProcessHandle thường trả về đường dẫn file .exe.
         */
        String exePath = getCurrentProcessCommand();

        if (exePath != null && exePath.toLowerCase().endsWith(".exe")) {
            File exeFile = new File(exePath);

            if (exeFile.exists() && exeFile.isFile()) {
                return "\"" + exeFile.getAbsolutePath() + "\" --minimized";
            }
        }

        /*
         * Trường hợp 2:
         * App đang chạy bằng file .jar.
         */
        String jarPath = getRunningJarPath();

        if (jarPath != null) {
            String javawPath = getJavawPath();

            if (javawPath != null) {
                return "\"" + javawPath + "\" -jar \"" + jarPath + "\" --minimized";
            }
        }

        return null;
    }

    private String getCurrentProcessCommand() {
        try {
            Optional<String> command = ProcessHandle.current().info().command();

            if (command.isPresent()) {
                return command.get();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private Path getStartupFolder() {
        try {
            String appData = System.getenv("APPDATA");

            if (appData == null || appData.isBlank()) {
                return null;
            }

            return Paths.get(
                    appData,
                    "Microsoft",
                    "Windows",
                    "Start Menu",
                    "Programs",
                    "Startup"
            );

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getJavawPath() {
        try {
            String javaHome = System.getProperty("java.home");

            Path javawPath = Paths.get(javaHome, "bin", "javaw.exe");

            if (Files.exists(javawPath)) {
                return javawPath.toAbsolutePath().toString();
            }

            Path javaPath = Paths.get(javaHome, "bin", "java.exe");

            if (Files.exists(javaPath)) {
                return javaPath.toAbsolutePath().toString();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private String getRunningJarPath() {
        try {
            File file = new File(
                    AutoStartService.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );

            if (file.exists()
                    && file.isFile()
                    && file.getName().toLowerCase().endsWith(".jar")) {
                return file.getAbsolutePath();
            }

            System.out.println("App hiện không chạy từ file .jar: " + file.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}