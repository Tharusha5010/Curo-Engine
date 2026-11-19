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
