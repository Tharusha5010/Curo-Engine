package alerts;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import models.Notification;
import ui.Theme;
import ui.UIHelper;


/**
 * AlertsPanel renders live in-memory telemetry alerts (Expiry Warnings, 50% Low Stock thresholds, and Security).
 * Strictly read-only per system architecture; automatically updates in real time via NotificationManager subscription.
 */
public class AlertsPanel extends JPanel {

    // Filter modes
    private enum FilterMode {
        ALL, EXPIRY, LOW_STOCK
    }

    private FilterMode currentFilter = FilterMode.ALL;

    // UI Components
    private JTable tblAlerts;
    private DefaultTableModel tableModel;
    private JLabel lblAlertCountBadge;

    private JButton btnFilterAll;
    private JButton btnFilterExpiry;
    private JButton btnFilterStock;
    private JButton btnRefresh;

    // Backend Notification Manager
    private final NotificationManager notificationManager = NotificationManager.getInstance();

    public AlertsPanel() {
        initComponents();
        registerLiveUpdates();
        refreshAlertsTable();
    }

    private void initComponents() {
        setLayout(new BorderLayout(15, 15));
        setBackground(Theme.MAIN_BG);
        setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // 1. Top Header & Summary
        JPanel topSection = buildTopSection();
        add(topSection, BorderLayout.NORTH);

        // 2. Read-Only Alerts Table Card (STRICT RULE: No Delete/Dismiss/Clear/Edit buttons)
        JPanel tableCard = buildTableCard();
        add(tableCard, BorderLayout.CENTER);
    }


    // =========================================================================
    // 1. Header & Summary Section
    // =========================================================================

    private JPanel buildTopSection() {
        JPanel container = new JPanel(new BorderLayout(0, 12));
        container.setBackground(Theme.MAIN_BG);

        // Header Title Box
        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setBackground(Theme.MAIN_BG);

        JLabel lblTitle = UIHelper.createPageTitle("Smart Alerts (In-Memory Notifications)");
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);


        JLabel lblSub = new JLabel(
            ""
        );
        lblSub.setFont(Theme.BODY_FONT);
        lblSub.setForeground(Theme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);
        container.add(titleBox, BorderLayout.WEST);