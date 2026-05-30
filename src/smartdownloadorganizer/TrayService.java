package smartdownloadorganizer;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class TrayService {

    private final JFrame frame;
    private final Runnable onStartWatch;
    private final Runnable onStopWatch;
    private final Runnable onExitApp;

    private TrayIcon trayIcon;
    private CheckboxMenuItem autoWatchItem;

    public TrayService(
            JFrame frame,
            Runnable onStartWatch,
            Runnable onStopWatch,
            Runnable onExitApp
    ) {
        this.frame = frame;
        this.onStartWatch = onStartWatch;
        this.onStopWatch = onStopWatch;
        this.onExitApp = onExitApp;
    }

    public void setupTray() {
        if (!SystemTray.isSupported()) {
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();

        PopupMenu popupMenu = new PopupMenu();

        MenuItem openItem = new MenuItem("Open");
        autoWatchItem = new CheckboxMenuItem("Auto Watch", false);
        MenuItem startWatchItem = new MenuItem("Start Auto Watch");
        MenuItem stopWatchItem = new MenuItem("Stop Auto Watch");
        MenuItem exitItem = new MenuItem("Exit");

        popupMenu.add(openItem);
        popupMenu.addSeparator();
        popupMenu.add(autoWatchItem);
        popupMenu.add(startWatchItem);
        popupMenu.add(stopWatchItem);
        popupMenu.addSeparator();
        popupMenu.add(exitItem);

        Image trayImage = createTrayImage();

        trayIcon = new TrayIcon(trayImage, "DungLab_SwapFiles", popupMenu);
        trayIcon.setImageAutoSize(true);

        openItem.addActionListener(e -> showFrame());

        trayIcon.addActionListener(e -> showFrame());

        startWatchItem.addActionListener(e -> {
            if (onStartWatch != null) {
                onStartWatch.run();
            }
        });

        stopWatchItem.addActionListener(e -> {
            if (onStopWatch != null) {
                onStopWatch.run();
            }
        });

        autoWatchItem.addItemListener(e -> {
            if (autoWatchItem.getState()) {
                if (onStartWatch != null) {
                    onStartWatch.run();
                }
            } else {
                if (onStopWatch != null) {
                    onStopWatch.run();
                }
            }
        });

        exitItem.addActionListener(e -> {
            if (onExitApp != null) {
                onExitApp.run();
            }
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            JOptionPane.showMessageDialog(
                    frame,
                    "Không thể tạo System Tray: " + e.getMessage()
            );
        }
    }

    public void hideToTray() {
        if (!SystemTray.isSupported()) {
            frame.setState(Frame.ICONIFIED);
            return;
        }

        frame.setVisible(false);

        if (trayIcon != null) {
                trayIcon.displayMessage(
                    "DungLab_SwapFiles",
                    "Ứng dụng đang chạy nền trong khay hệ thống.",
                    TrayIcon.MessageType.INFO
                );
        }
    }

    public void showFrame() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            frame.setState(Frame.NORMAL);
            frame.toFront();
            frame.requestFocus();
        });
    }

    public void removeTrayIcon() {
        if (SystemTray.isSupported() && trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    public void setAutoWatchState(boolean running) {
        if (autoWatchItem != null) {
            autoWatchItem.setState(running);
        }
    }

    public TrayIcon getTrayIcon() {
        return trayIcon;
    }

    private Image createTrayImage() {
        int size = 16;

        BufferedImage image = new BufferedImage(
                size,
                size,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = image.createGraphics();

        g.fillOval(1, 1, 14, 14);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString("S", 5, 12);

        g.dispose();

        return image;
    }
}