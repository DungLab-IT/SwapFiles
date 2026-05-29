package smartdownloadorganizer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CategoryManager {

    private final List<Category> categories = new ArrayList<>();

    public CategoryManager() {
        loadDefaultCategories();
    }

    private void loadDefaultCategories() {
        String userHome = System.getProperty("user.home");

        categories.add(new Category(
                "Documents",
                Arrays.asList("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf"),
                Paths.get(userHome, "Documents"),
                true
        ));

        categories.add(new Category(
                "Programs",
                Arrays.asList("exe", "msi", "dmg", "pkg", "app", "deb", "rpm", "apk"),
                Paths.get(userHome, "Downloads", "Programs"),
                true
        ));

        categories.add(new Category(
                "Compressed",
                Arrays.asList("zip", "rar", "7z", "tar", "gz", "iso"),
                Paths.get(userHome, "Downloads", "Compressed"),
                true
        ));

        categories.add(new Category(
                "Images",
                Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"),
                Paths.get(userHome, "Pictures"),
                true
        ));

        categories.add(new Category(
                "Video",
                Arrays.asList("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm"),
                Paths.get(userHome, "Videos"),
                true
        ));

        categories.add(new Category(
                "Music",
                Arrays.asList("mp3", "wav", "flac", "aac", "m4a"),
                Paths.get(userHome, "Music"),
                true
        ));

        categories.add(new Category(
                "Code",
                Arrays.asList("java", "py", "js", "html", "css", "cpp", "c", "cs", "php", "json", "xml"),
                Paths.get(userHome, "Downloads", "Code"),
                true
        ));

        categories.add(new Category(
                "Others",
                new ArrayList<>(),
                Paths.get(userHome, "Downloads", "Others"),
                true
        ));
    }

    public List<Category> getCategories() {
        return categories;
    }

    public Category findCategoryByExtension(String extension) {
        for (Category category : categories) {
            if (category.isEnabled() && category.containsExtension(extension)) {
                return category;
            }
        }

        return getOthersCategory();
    }

    public Category getOthersCategory() {
        for (Category category : categories) {
            if (category.getName().equalsIgnoreCase("Others")) {
                return category;
            }
        }

        return null;
    }

    public void updateCategoryTargetFolder(int index, Path folder) {
        if (index >= 0 && index < categories.size()) {
            categories.get(index).setTargetFolder(folder);
        }
    }

    public void updateCategoryExtensions(int index, String extensionsText) {
        if (index >= 0 && index < categories.size()) {
            categories.get(index).setExtensions(Category.parseExtensions(extensionsText));
        }
    }

    public void updateCategoryEnabled(int index, boolean enabled) {
        if (index >= 0 && index < categories.size()) {
            categories.get(index).setEnabled(enabled);
        }
    }
}