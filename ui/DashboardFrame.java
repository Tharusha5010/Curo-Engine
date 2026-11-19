package ui;

import alerts.AlertsPanel;
import alerts.NotificationManager;
import auth.LoginFrame;
import auth.StaffPanel;
import auth.UserSession;
import billing.BillingDAO;
import billing.BillingPanel;
import billing.CancelBillPanel;
import exceptions.UnauthorizedAccessException;
import inventory.InventoryDAO;
import inventory.InventoryPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import models.Medicine;
import models.Notification;
import models.StaffMember;

/**
 * DashboardFrame is the main administrative and operational application window for Curo Pharmacy System.
 * It integrates a persistent left sidebar with role-based navigation and an embedded CardLayout content area.
 */
public class DashboardFrame extends JFrame {

    // Card identifiers
    private static final String CARD_DASHBOARD = "CARD_DASHBOARD";
    private static final String CARD_BILLING = "CARD_BILLING";
    private static final String CARD_INVENTORY = "CARD_INVENTORY";
    private static final String CARD_ALERTS = "CARD_ALERTS";
    private static final String CARD_STAFF = "CARD_STAFF";
    private static final String CARD_CANCEL_BILLS = "CARD_CANCEL_BILLS";

    // Layout and Navigation
    private CardLayout cardLayout;
    private JPanel contentAreaPanel;
    private final List<JButton> navButtons = new ArrayList<>();
    private JButton activeButton;

    // Embedded Modular Panels
    private BillingPanel billingPanel;
    private InventoryPanel inventoryPanel;
    private AlertsPanel alertsPanel;
    private StaffPanel staffPanel;
    private CancelBillPanel cancelBillPanel;

    // Summary Card Labels
    private JLabel lblTotalMedicines;
    private JLabel lblTodaySales;
    private JLabel lblExpiringSoon;
    private JLabel lblLowStock;

    // Overview Chart and Table
    private SalesBarChartPanel barChartPanel;
    private JTable tblRecentAlerts;
    private DefaultTableModel recentAlertsModel;

    // Backend Access Objects
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final BillingDAO billingDAO = new BillingDAO();
    private final NotificationManager notificationManager = NotificationManager.getInstance();

    // --- Temporary owner access (cashier + Owner pop-up login) ---
    private static final int OWNER_ACCESS_MINUTES = 5;
    private StaffMember elevatedBase;          // the cashier we return to
    private javax.swing.Timer elevationTimer;
    private JPanel ownerBanner;
    private JLabel lblOwnerBanner;
    private JButton navDashboard;

    public DashboardFrame() {
        initComponents();
        subscribeToNotifications();
        refreshDashboardStats();
    }

    private void initComponents() {
        setTitle("Curo Loss-Prevention Engine - Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Windows scaling: size the window to the visible screen so it fits at 100%/125%/150%/200%
        UIHelper.fitToScreen(this, 1240, 780, 820, 560);
        setResizable(true);

        // Main Frame Layout
        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBackground(Theme.MAIN_BG);

        // 1. Build Left Sidebar (~230px)
        JPanel sidebarPanel = buildSidebar();
        rootPanel.add(sidebarPanel, BorderLayout.WEST);

        // 2. Build Right Content Area with CardLayout
        cardLayout = new CardLayout();
        contentAreaPanel = new JPanel(cardLayout);
        contentAreaPanel.setBackground(Theme.MAIN_BG);

        // Instantiate modular panels
        JPanel dashboardHomePanel = buildDashboardHomePanel();
        billingPanel = new BillingPanel();
        inventoryPanel = new InventoryPanel();
        alertsPanel = new AlertsPanel();
        staffPanel = new StaffPanel();
        cancelBillPanel = new CancelBillPanel();

        // Add screens to CardLayout
        // Every tab sits in a scroll wrapper: it stretches in a big window and scrolls in a small one
        contentAreaPanel.add(new FitScrollPane(dashboardHomePanel), CARD_DASHBOARD);
        contentAreaPanel.add(new FitScrollPane(billingPanel), CARD_BILLING);
        contentAreaPanel.add(new FitScrollPane(inventoryPanel), CARD_INVENTORY);
        contentAreaPanel.add(new FitScrollPane(alertsPanel), CARD_ALERTS);
        contentAreaPanel.add(new FitScrollPane(staffPanel), CARD_STAFF);
        contentAreaPanel.add(new FitScrollPane(cancelBillPanel), CARD_CANCEL_BILLS);

        // Banner shown while an Owner is temporarily signed in over a cashier
        ownerBanner = new JPanel(new BorderLayout(12, 0));
        ownerBanner.setBackground(new Color(255, 236, 179));
        ownerBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        lblOwnerBanner = new JLabel(" ");
        lblOwnerBanner.setFont(Theme.BOLD_BODY_FONT);
        lblOwnerBanner.setForeground(Theme.TEXT_DARK);
        JButton btnEndOwner = new JButton("End owner access");
        UIHelper.styleSecondaryButton(btnEndOwner);
        btnEndOwner.addActionListener(e -> {
            endTemporaryOwner();
            showDashboardTab();
        });
        ownerBanner.add(lblOwnerBanner, BorderLayout.CENTER);
        ownerBanner.add(btnEndOwner, BorderLayout.EAST);
        ownerBanner.setVisible(false);

        JPanel contentHolder = new JPanel(new BorderLayout());
        contentHolder.add(ownerBanner, BorderLayout.NORTH);
        contentHolder.add(contentAreaPanel, BorderLayout.CENTER);

        elevationTimer = new javax.swing.Timer(OWNER_ACCESS_MINUTES * 60 * 1000, e -> {
            endTemporaryOwner();
            showDashboardTab();
        });
        elevationTimer.setRepeats(false);

        // Sidebar and content can be resized against each other by dragging the divider
        javax.swing.JSplitPane mainSplit = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, contentHolder);
        mainSplit.setContinuousLayout(true);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(5);
        mainSplit.setDividerLocation(230);
        mainSplit.setResizeWeight(0.0);
        rootPanel.remove(sidebarPanel);
        rootPanel.add(mainSplit, BorderLayout.CENTER);
        setContentPane(rootPanel);

        // Light text on dark backgrounds, dark text on light backgrounds
        UIHelper.applyContrast(rootPanel);
    }
}