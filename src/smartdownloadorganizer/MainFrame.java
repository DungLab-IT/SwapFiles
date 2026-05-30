package smartdownloadorganizer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.Desktop;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.File;
import java.io.IOException;

public class MainFrame extends JFrame {

    private final CategoryManager categoryManager;
    private final FileOrganizerService organizerService;
    private final ConfigService configService;
    private final HistoryService historyService;

    private JTextField sourceFolderField;

    private JTable categoryTable;
    private DefaultTableModel categoryTableModel;

    private JTable historyTable;
    private DefaultTableModel historyTableModel;

    private JTextArea logArea;

    private FolderWatcherService folderWatcherService;
    private Thread watcherThread;

    private JButton startWatchButton;
    private JButton stopWatchButton;

    private TrayService trayService;

    private AutoStartService autoStartService;
    private JCheckBox autoStartCheckBox;

    public MainFrame() {
        categoryManager = new CategoryManager();

        configService = new ConfigService();
        configService.loadSettings(categoryManager);

        historyService = new HistoryService();
        historyService.setHistoryChangeCallback(this::loadHistoryToTable);

        autoStartService = new AutoStartService();

        organizerService = new FileOrganizerService(
                categoryManager,
                historyService,
                this::log,
                this::showNotification);

        setTitle("DungLab_SwapFiles");
        setSize(1200, 750);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setFont(new Font("Segoe UI", Font.PLAIN, 12));

        initUI();

        String savedSourceFolder = configService.getSourceFolderOrDefault(getDefaultDownloadFolder());
        sourceFolderField.setText(savedSourceFolder);

        loadCategoriesToTable();
        loadHistoryToTable();

        setupSystemTray();
        setupWindowBehavior();
    }

    private void initUI() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabbedPane.addTab("Dashboard", createDashboardPanel());
        tabbedPane.addTab("Categories", createCategoriesPanel());
        tabbedPane.addTab("History", createHistoryPanel());
        tabbedPane.addTab("Settings", createSettingsPanel());

        add(tabbedPane);
    }

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);

        JPanel sourcePanel = new JPanel(new BorderLayout(8, 8));
        sourcePanel.setBackground(Color.WHITE);
        sourcePanel.setBorder(BorderFactory.createTitledBorder("Source Folder"));

        JLabel sourceLabel = new JLabel("Select folder to organize:");
        sourceLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sourceFolderField = new JTextField(getDefaultDownloadFolder());
        sourceFolderField.setFont(new Font("Monospaced", Font.PLAIN, 11));
        sourceFolderField.setEditable(false);
        JButton chooseButton = new JButton("Browse...");
        chooseButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        sourcePanel.add(sourceLabel, BorderLayout.WEST);
        sourcePanel.add(sourceFolderField, BorderLayout.CENTER);
        sourcePanel.add(chooseButton, BorderLayout.EAST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        actionPanel.setBackground(Color.WHITE);
        actionPanel.setBorder(BorderFactory.createTitledBorder("Actions"));

        JButton organizeNowButton = new JButton("Organize Now");
        startWatchButton = new JButton("▶ Start Auto Watch");
        stopWatchButton = new JButton("⏹ Stop Auto Watch");
        JButton clearLogButton = new JButton("Clear Log");

        for (JButton btn : new JButton[]{organizeNowButton, startWatchButton, stopWatchButton, clearLogButton}) {
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            btn.setPreferredSize(new Dimension(140, 35));
        }

        stopWatchButton.setEnabled(false);

        actionPanel.add(organizeNowButton);
        actionPanel.add(startWatchButton);
        actionPanel.add(stopWatchButton);
        actionPanel.add(clearLogButton);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.WHITE);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Log"));

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(sourcePanel, BorderLayout.NORTH);
        topPanel.add(actionPanel, BorderLayout.SOUTH);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(logScrollPane, BorderLayout.CENTER);

        chooseButton.addActionListener(e -> chooseSourceFolder());
        organizeNowButton.addActionListener(e -> organizeNow());
        startWatchButton.addActionListener(e -> startAutoWatch());
        stopWatchButton.addActionListener(e -> stopAutoWatch());
        clearLogButton.addActionListener(e -> logArea.setText(""));

        return panel;
    }

    private JPanel createCategoriesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);

        categoryTableModel = new DefaultTableModel(
                new Object[] { "Category", "Extensions", "Target Folder", "Enabled" },
                0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1 || column == 3;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 3) {
                    return Boolean.class;
                }
                return String.class;
            }
        };

        categoryTable = new JTable(categoryTableModel);
        categoryTable.setRowHeight(32);
        categoryTable.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        categoryTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        JScrollPane scrollPane = new JScrollPane(categoryTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Categories"));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JButton chooseTargetButton = new JButton("📁 Choose Target Folder");
        JButton saveCategoryButton = new JButton("💾 Save Changes");

        for (JButton btn : new JButton[]{chooseTargetButton, saveCategoryButton}) {
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            btn.setPreferredSize(new Dimension(150, 32));
        }

        buttonPanel.add(chooseTargetButton);
        buttonPanel.add(saveCategoryButton);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        chooseTargetButton.addActionListener(e -> chooseTargetFolder());
        saveCategoryButton.addActionListener(e -> saveCategoryChanges());

        return panel;
    }

    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        historyTableModel = new DefaultTableModel(
                new Object[] { "Thời gian", "Tên file", "Danh mục", "Nguồn cũ", "Đích mới", "Trạng thái" },
                0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        historyTable = new JTable(historyTableModel);
        historyTable.setRowHeight(32);
        historyTable.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        historyTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        historyTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        // Double-click to open file
        historyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && historyTable.getSelectedRow() != -1) {
                    openSelectedFile();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("History"));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JButton openFileButton = new JButton("📁 Open File");
        JButton openFolderButton = new JButton("📂 Open Folder");
        JButton undoButton = new JButton("↶ Undo");
        JButton clearHistoryButton = new JButton("🗑 Clear");

        for (JButton btn : new JButton[]{openFileButton, openFolderButton, undoButton, clearHistoryButton}) {
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            btn.setPreferredSize(new Dimension(110, 32));
        }

        buttonPanel.add(openFileButton);
        buttonPanel.add(openFolderButton);
        buttonPanel.add(undoButton);
        buttonPanel.add(clearHistoryButton);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        openFileButton.addActionListener(e -> openSelectedFile());
        openFolderButton.addActionListener(e -> openSelectedFolder());
        undoButton.addActionListener(e -> undoSelectedHistory());

        clearHistoryButton.addActionListener(e -> {
            historyService.clearHistory();
            log("History cleared.");
        });

        return panel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);

        JCheckBox autoRename = new JCheckBox("Auto-rename when filename exists", true);
        JCheckBox skipTemp = new JCheckBox("Skip incomplete downloads (.crdownload, .part, .tmp)", true);
        JCheckBox askBeforeMove = new JCheckBox("Ask before moving files", false);
        JCheckBox notification = new JCheckBox("Show notification when file is moved", false);
        autoStartCheckBox = new JCheckBox("Auto-start with system", configService.getAutoStartSetting());

        for (JCheckBox cb : new JCheckBox[]{autoRename, skipTemp, askBeforeMove, notification, autoStartCheckBox}) {
            cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            cb.setBackground(Color.WHITE);
        }

        JButton saveSettingsButton = new JButton("💾 Save Settings");
        JButton exportSettingsButton = new JButton("📤 Export Config");
        JButton importSettingsButton = new JButton("📥 Import Config");

        for (JButton btn : new JButton[]{saveSettingsButton, exportSettingsButton, importSettingsButton}) {
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            btn.setPreferredSize(new Dimension(140, 35));
        }

        panel.add(autoRename);
        panel.add(skipTemp);
        panel.add(askBeforeMove);
        panel.add(notification);
        panel.add(autoStartCheckBox);
        panel.add(saveSettingsButton);
        panel.add(exportSettingsButton);
        panel.add(importSettingsButton);

        saveSettingsButton.addActionListener(e -> {
            saveSettingsToFile();
            log("Settings saved to: " + configService.getConfigFile());
        });

        autoStartCheckBox.addActionListener(e -> toggleAutoStart());

        exportSettingsButton.addActionListener(e -> exportSettingsToFile());
        importSettingsButton.addActionListener(e -> importSettingsFromFile());

        return panel;
    }

    private void loadCategoriesToTable() {
        if (categoryTableModel == null) {
            return;
        }

        categoryTableModel.setRowCount(0);

        for (Category category : categoryManager.getCategories()) {
            categoryTableModel.addRow(new Object[] {
                    category.getName(),
                    category.getExtensionsAsText(),
                    category.getTargetFolder().toString(),
                    category.isEnabled()
            });
        }
    }

    private void loadHistoryToTable() {
        if (historyTableModel == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            historyTableModel.setRowCount(0);

            for (HistoryRecord record : historyService.getHistoryRecords()) {
                historyTableModel.addRow(new Object[] {
                        record.getTimeText(),
                        record.getFileName(),
                        record.getCategoryName(),
                        record.getSourcePath().toString(),
                        record.getTargetPath().toString(),
                        record.getStatus()
                });
            }
        });
    }

    private void saveCategoryChanges() {
        for (int i = 0; i < categoryTableModel.getRowCount(); i++) {
            String extensions = String.valueOf(categoryTableModel.getValueAt(i, 1));
            Boolean enabled = Boolean.valueOf(String.valueOf(categoryTableModel.getValueAt(i, 3)));

            categoryManager.updateCategoryExtensions(i, extensions);
            categoryManager.updateCategoryEnabled(i, enabled);
        }

        loadCategoriesToTable();
        saveSettingsToFile();
        log("Đã lưu chỉnh sửa danh mục.");
    }

    private void chooseSourceFolder() {
        File selectedFolder = chooseFolderNative("Chọn thư mục cần sắp xếp");

        if (selectedFolder != null) {
            sourceFolderField.setText(selectedFolder.getAbsolutePath());
            saveSettingsToFile();
            log("Đã chọn thư mục nguồn: " + selectedFolder.getAbsolutePath());
        }
    }

    private void chooseTargetFolder() {
        int selectedRow = categoryTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Bạn cần chọn một danh mục trước.");
            return;
        }

        File selectedFolder = chooseFolderNative("Chọn thư mục lưu");

        if (selectedFolder != null) {
            Path selectedPath = selectedFolder.toPath();

            categoryManager.updateCategoryTargetFolder(selectedRow, selectedPath);
            loadCategoriesToTable();
            saveSettingsToFile();

            log("Đã đổi thư mục lưu cho danh mục: "
                    + categoryManager.getCategories().get(selectedRow).getName()
                    + " → "
                    + selectedPath);
        }
    }

    private void organizeNow() {
        Path sourceFolder = Paths.get(sourceFolderField.getText());
        organizerService.organizeFolder(sourceFolder);
    }

    private void startAutoWatch() {
        Path sourceFolder = Paths.get(sourceFolderField.getText());

        if (watcherThread != null && watcherThread.isAlive()) {
            log("Auto Watch đang chạy rồi.");
            return;
        }

        saveSettingsToFile();

        folderWatcherService = new FolderWatcherService(
                sourceFolder,
                organizerService,
                this::log);

        watcherThread = new Thread(folderWatcherService);
        watcherThread.setDaemon(true);
        watcherThread.start();

        startWatchButton.setEnabled(false);
        stopWatchButton.setEnabled(true);

        if (trayService != null) {
            trayService.setAutoWatchState(true);
        }

        log("Đã bật chế độ tự động sắp xếp.");
    }

    private void stopAutoWatch() {
        if (folderWatcherService != null) {
            folderWatcherService.stopWatching();
        }

        if (watcherThread != null) {
            watcherThread.interrupt();
        }

        startWatchButton.setEnabled(true);
        stopWatchButton.setEnabled(false);

        if (trayService != null) {
            trayService.setAutoWatchState(false);
        }

        log("Đang dừng chế độ tự động sắp xếp...");
    }

    private void undoSelectedHistory() {
        int selectedRow = historyTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Bạn cần chọn một dòng lịch sử để hoàn tác.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc muốn hoàn tác file này về thư mục ban đầu không?",
                "Xác nhận hoàn tác",
                JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            boolean success = historyService.undoMove(selectedRow);

            if (success) {
                log("Đã hoàn tác file đã chọn.");
            } else {
                log("Không thể hoàn tác file đã chọn.");
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Lỗi khi hoàn tác: " + e.getMessage());
        }
    }

    private void saveSettingsToFile() {
        if (sourceFolderField == null) {
            return;
        }

        configService.saveSettings(sourceFolderField.getText(), categoryManager);
    }

    private void exportSettingsToFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Xuất file cài đặt");
        chooser.setSelectedFile(new java.io.File("DungLab_SwapFiles-Settings.properties"));

        int result = chooser.showSaveDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                saveSettingsToFile();

                Path exportPath = chooser.getSelectedFile().toPath();

                if (!exportPath.toString().toLowerCase().endsWith(".properties")) {
                    exportPath = Paths.get(exportPath.toString() + ".properties");
                }

                configService.exportSettings(exportPath);

                log("Đã xuất cài đặt ra file: " + exportPath);

                JOptionPane.showMessageDialog(
                        this,
                        "Đã xuất cài đặt thành công:\n" + exportPath);

            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        this,
                        "Lỗi khi xuất cài đặt: " + e.getMessage());
            }
        }
    }

    private void importSettingsFromFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Nhập file cài đặt");

        int result = chooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                Path importPath = chooser.getSelectedFile().toPath();

                configService.importSettings(importPath, categoryManager);

                String savedSourceFolder = configService.getSourceFolderOrDefault(getDefaultDownloadFolder());
                sourceFolderField.setText(savedSourceFolder);

                loadCategoriesToTable();

                log("Đã nhập cài đặt từ file: " + importPath);

                JOptionPane.showMessageDialog(
                        this,
                        "Đã nhập cài đặt thành công:\n" + importPath);

            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        this,
                        "Lỗi khi nhập cài đặt: " + e.getMessage());
            }
        }
    }

    private String getDefaultDownloadFolder() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, "Downloads").toString();
    }

    private void setupSystemTray() {
        trayService = new TrayService(
                this,
                this::startAutoWatch,
                this::stopAutoWatch,
                this::exitApplication);

        trayService.setupTray();
    }

    private void setupWindowBehavior() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                int choice = JOptionPane.showConfirmDialog(
                        MainFrame.this,
                        "Bạn muốn ẩn ứng dụng xuống khay hệ thống không?\n\n"
                                + "Yes: Ẩn xuống khay\n"
                                + "No: Thoát ứng dụng\n"
                                + "Cancel: Hủy",
                        "DungLab_SwapFiles",
                        JOptionPane.YES_NO_CANCEL_OPTION);

                if (choice == JOptionPane.YES_OPTION) {
                    hideToTray();
                } else if (choice == JOptionPane.NO_OPTION) {
                    exitApplication();
                }
            }

            @Override
            public void windowIconified(java.awt.event.WindowEvent e) {
                hideToTray();
            }
        });
    }

    private void hideToTray() {
        if (trayService != null) {
            trayService.hideToTray();
        } else {
            setState(JFrame.ICONIFIED);
        }
    }

    private void exitApplication() {
        stopAutoWatch();

        if (trayService != null) {
            trayService.removeTrayIcon();
        }

        dispose();
        System.exit(0);
    }

    private void toggleAutoStart() {
        if (autoStartService == null) {
            return;
        }

        if (!autoStartService.isWindows()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Tính năng tự chạy hiện chỉ hỗ trợ Windows.");
            autoStartCheckBox.setSelected(false);
            return;
        }

        boolean wantEnable = autoStartCheckBox.isSelected();

        if (wantEnable) {
            boolean success = autoStartService.enableAutoStart();

            if (success) {
                configService.saveAutoStartSetting(true);
                log("Đã bật tự chạy cùng Windows.");
                JOptionPane.showMessageDialog(
                        this,
                        "Đã bật tự chạy cùng Windows.\nFile Startup:\n"
                                + autoStartService.getAutoStartFilePath());
            } else {
                autoStartCheckBox.setSelected(false);
                configService.saveAutoStartSetting(false);
                JOptionPane.showMessageDialog(
                        this,
                        "Không thể bật tự chạy.\n"
                                + "Lưu ý: tính năng này chỉ hoạt động khi app chạy từ file .jar đã build.");
            }

        } else {
            boolean success = autoStartService.disableAutoStart();

            if (success) {
                configService.saveAutoStartSetting(false);
                log("Đã tắt tự chạy cùng Windows.");
                JOptionPane.showMessageDialog(
                        this,
                        "Đã tắt tự chạy cùng Windows.");
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "Không thể tắt tự chạy cùng Windows.");
            }
        }
    }

    private JFileChooser createFolderChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        /*
         * Trên macOS, ổ rời nằm trong /Volumes.
         * Cho mở thẳng vào đây để dễ chọn USB/SSD ngoài.
         */
        if (isMacOS()) {
            File volumesFolder = new File("/Volumes");

            if (volumesFolder.exists()) {
                chooser.setCurrentDirectory(volumesFolder);
            }
        }

        return chooser;
    }

    private boolean isMacOS() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("mac");
    }

    private File chooseFolderNative(String title) {
        if (isMacOS()) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true");

            FileDialog dialog = new FileDialog(
                    (Frame) null,
                    title,
                    FileDialog.LOAD);

            File volumesFolder = new File("/Volumes");

            if (volumesFolder.exists()) {
                dialog.setDirectory("/Volumes");
            }

            dialog.setVisible(true);

            String directory = dialog.getDirectory();
            String file = dialog.getFile();

            System.setProperty("apple.awt.fileDialogForDirectories", "false");

            if (directory == null || file == null) {
                return null;
            }

            return new File(directory, file);
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle(title);

        int result = chooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }

        return null;
    }

    private void openSelectedFile() {
        int selectedRow = historyTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Bạn cần chọn một dòng lịch sử để mở file.");
            return;
        }

        try {
            HistoryRecord record = historyService.getHistoryRecords().get(selectedRow);
            Path targetPath = record.getTargetPath();
            File file = targetPath.toFile();

            if (!file.exists()) {
                JOptionPane.showMessageDialog(
                        this,
                        "File không còn tồn tại tại:\n" + targetPath);
                return;
            }

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
                log("Đã mở file: " + file.getName());
            } else {
                JOptionPane.showMessageDialog(this, "Hệ thống không hỗ trợ mở file.");
            }

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Lỗi khi mở file: " + ex.getMessage());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Lỗi không xác định: " + ex.getMessage());
        }
    }

    private void openSelectedFolder() {
        int selectedRow = historyTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Bạn cần chọn một dòng lịch sử để mở thư mục.");
            return;
        }

        try {
            HistoryRecord record = historyService.getHistoryRecords().get(selectedRow);
            Path targetPath = record.getTargetPath();
            Path parentFolder = targetPath.getParent();

            if (parentFolder == null || !Files.exists(parentFolder)) {
                JOptionPane.showMessageDialog(
                        this,
                        "Thư mục không còn tồn tại tại:\n" + (parentFolder != null ? parentFolder : targetPath));
                return;
            }

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(parentFolder.toFile());
                log("Đã mở thư mục: " + parentFolder);
            } else {
                JOptionPane.showMessageDialog(this, "Hệ thống không hỗ trợ mở thư mục.");
            }

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Lỗi khi mở thư mục: " + ex.getMessage());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error: " + ex.getMessage());
        }
    }

    private void showNotification(String fileName, String category) {
        try {
            if (!Desktop.isDesktopSupported()) {
                return;
            }

            // Hiển thị notification trên system tray
            if (trayService != null && trayService.getTrayIcon() != null) {
                SwingUtilities.invokeLater(() -> {
                    trayService.getTrayIcon().displayMessage(
                            "File Organized",
                            fileName + " → " + category,
                            TrayIcon.MessageType.INFO
                    );
                });
            }
        } catch (Exception ignored) {
        }
    }

    private void log(String message) {
        if (logArea == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
