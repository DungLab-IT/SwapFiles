package smartdownloadorganizer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public class ConfigService {

    private final Properties properties = new Properties();
    private final Path appConfigFolder;
    private final Path configFile;

    public ConfigService() {
        String userHome = System.getProperty("user.home");

        appConfigFolder = Paths.get(userHome, ".dunglab-swapfiles");
        configFile = appConfigFolder.resolve("config.properties");

        createConfigFolderIfNeeded();
        loadInternalConfig();
    }

    private void createConfigFolderIfNeeded() {
        try {
            if (!Files.exists(appConfigFolder)) {
                Files.createDirectories(appConfigFolder);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadInternalConfig() {
        if (!Files.exists(configFile)) {
            return;
        }

        try (InputStream inputStream = Files.newInputStream(configFile)) {
            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveSettings(String sourceFolder, CategoryManager categoryManager) {
        properties.clear();

        properties.setProperty("app.name", "DungLab_SwapFiles");
        properties.setProperty("config.version", "1");
        properties.setProperty("source.folder", sourceFolder);

        for (Category category : categoryManager.getCategories()) {
            String name = category.getName();

            properties.setProperty("category." + name + ".extensions", category.getExtensionsAsText());
            properties.setProperty("category." + name + ".targetFolder", category.getTargetFolder().toString());
            properties.setProperty("category." + name + ".enabled", String.valueOf(category.isEnabled()));
        }

        saveToFile(configFile);
    }

    public void loadSettings(CategoryManager categoryManager) {
        for (Category category : categoryManager.getCategories()) {
            String name = category.getName();

            String extensions = properties.getProperty("category." + name + ".extensions");
            String targetFolder = properties.getProperty("category." + name + ".targetFolder");
            String enabled = properties.getProperty("category." + name + ".enabled");

            if (extensions != null) {
                category.setExtensions(Category.parseExtensions(extensions));
            }

            if (targetFolder != null && !targetFolder.trim().isEmpty()) {
                category.setTargetFolder(Paths.get(targetFolder));
            }

            if (enabled != null) {
                category.setEnabled(Boolean.parseBoolean(enabled));
            }
        }
    }

    public String getSourceFolderOrDefault(String defaultFolder) {
        return properties.getProperty("source.folder", defaultFolder);
    }

    public void exportSettings(Path exportFile) throws IOException {
        if (!Files.exists(configFile)) {
            saveToFile(configFile);
        }

        Files.copy(configFile, exportFile, StandardCopyOption.REPLACE_EXISTING);
    }

    public void importSettings(Path importFile, CategoryManager categoryManager) throws IOException {
        if (!Files.exists(importFile)) {
            throw new IOException("File cài đặt không tồn tại.");
        }

        properties.clear();

        try (InputStream inputStream = Files.newInputStream(importFile)) {
            properties.load(inputStream);
        }

        loadSettings(categoryManager);

        saveToFile(configFile);
    }

    private void saveToFile(Path file) {
        try (OutputStream outputStream = Files.newOutputStream(file)) {
            properties.store(outputStream, "DungLab_SwapFiles Settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Path getConfigFile() {
        return configFile;
    }

    public void saveAutoStartSetting(boolean enabled) {
        properties.setProperty("auto.start.windows", String.valueOf(enabled));
        saveToFile(configFile);
    }

    public boolean getAutoStartSetting() {
        return Boolean.parseBoolean(
                properties.getProperty("auto.start.windows", "false")
        );
    }
}
