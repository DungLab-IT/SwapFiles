package smartdownloadorganizer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Category {

    private String name;
    private List<String> extensions;
    private Path targetFolder;
    private boolean enabled;

    public Category(String name, List<String> extensions, Path targetFolder, boolean enabled) {
        this.name = name;
        this.extensions = extensions;
        this.targetFolder = targetFolder;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public Path getTargetFolder() {
        return targetFolder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setExtensions(List<String> extensions) {
        this.extensions = extensions;
    }

    public void setTargetFolder(Path targetFolder) {
        this.targetFolder = targetFolder;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean containsExtension(String extension) {
        if (extension == null) {
            return false;
        }

        String cleanExtension = extension.toLowerCase().trim();

        for (String ext : extensions) {
            if (ext.equalsIgnoreCase(cleanExtension)) {
                return true;
            }
        }

        return false;
    }

    public String getExtensionsAsText() {
        return String.join(", ", extensions);
    }

    public static List<String> parseExtensions(String text) {
        List<String> result = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        String[] parts = text.split(",");

        for (String part : parts) {
            String ext = part.trim().toLowerCase();

            if (ext.startsWith(".")) {
                ext = ext.substring(1);
            }

            if (!ext.isEmpty()) {
                result.add(ext);
            }
        }

        return result;
    }
}