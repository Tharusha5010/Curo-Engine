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

    // =========================================================================
    // Temporary Owner access
    // =========================================================================

    private boolean ensureOwner(String reason) {
        if (UserSession.isOwner()) {
            return true;
        }
        StaffMember owner = OwnerAccess.requestOwner(this, reason);
        if (owner == null) {
            return false;
        }
        beginTemporaryOwner(owner);
        return true;
    }

    private void beginTemporaryOwner(StaffMember owner) {
        if (elevatedBase == null) {
            elevatedBase = UserSession.getCurrentUser();
        }
        UserSession.setCurrentUser(owner);
        String name = owner.getStaffName() != null ? owner.getStaffName() : owner.getStaffId();
        lblOwnerBanner.setText("Temporary Owner access: " + name + "  (" + OWNER_ACCESS_MINUTES
                + " min, or until you leave this tab)");
        UIHelper.applyContrast(ownerBanner);
        ownerBanner.setVisible(true);
        elevationTimer.restart();
    }

    private void endTemporaryOwner() {
        if (elevatedBase != null) {
            UserSession.setCurrentUser(elevatedBase);
            elevatedBase = null;
        }
        if (elevationTimer != null) {
            elevationTimer.stop();
        }
        if (ownerBanner != null) {
            ownerBanner.setVisible(false);
        }
    }

    private void showDashboardTab() {
        if (navDashboard != null) {
            setActiveNavButton(navDashboard);
        }
        cardLayout.show(contentAreaPanel, CARD_DASHBOARD);
        refreshDashboardStats();
    }

    // =========================================================================
    // 1. Fixed Left Sidebar (Theme.SIDEBAR_BG, ~230px)
    // =========================================================================

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(Theme.SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.setMinimumSize(new Dimension(170, 0));

        // Sidebar Header
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(Theme.SIDEBAR_BG);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(25, 20, 20, 20));

        JLabel lblBrand = new JLabel("CURO ENGINE");
        lblBrand.setFont(Theme.TITLE_FONT.deriveFont(18f));
        lblBrand.setForeground(Color.WHITE);
        lblBrand.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSub = new JLabel("");
        lblSub.setFont(Theme.BODY_FONT.deriveFont(12f));
        lblSub.setForeground(Theme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        headerPanel.add(lblBrand);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(lblSub);

        // Accent Separator Line
        JPanel sep = new JPanel();
        sep.setBackground(Theme.SIDEBAR_ACTIVE);
        sep.setPreferredSize(new Dimension(190, 1));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(Box.createVerticalStrut(15));
        headerPanel.add(sep);

        sidebar.add(headerPanel, BorderLayout.NORTH);

        // Sidebar Nav Buttons List
        JPanel navListPanel = new JPanel();
        navListPanel.setLayout(new BoxLayout(navListPanel, BoxLayout.Y_AXIS));
        navListPanel.setBackground(Theme.SIDEBAR_BG);
        navListPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JButton btnDashboard = createNavButton("Dashboard", CARD_DASHBOARD, false);
        JButton btnBilling = createNavButton("Billing (POS)", CARD_BILLING, false);
        JButton btnInventory = createNavButton("Inventory", CARD_INVENTORY, false);
        JButton btnAlerts = createNavButton("Smart Alerts", CARD_ALERTS, false);
        JButton btnStaff = createNavButton("Staff Management", CARD_STAFF, true);
        JButton btnCancelBills = createNavButton("Cancel Bills", CARD_CANCEL_BILLS, true);
        addPopOutSupport(btnBilling, "Billing (POS)", CARD_BILLING, false);
        addPopOutSupport(btnInventory, "Inventory", CARD_INVENTORY, false);
        addPopOutSupport(btnAlerts, "Smart Alerts", CARD_ALERTS, false);
        addPopOutSupport(btnStaff, "Staff Management", CARD_STAFF, true);
        addPopOutSupport(btnCancelBills, "Cancel Bills", CARD_CANCEL_BILLS, true);

        navListPanel.add(btnDashboard);
        navListPanel.add(Box.createVerticalStrut(6));
        navListPanel.add(btnBilling);
        navListPanel.add(Box.createVerticalStrut(6));
        navListPanel.add(btnInventory);
        navListPanel.add(Box.createVerticalStrut(6));
        navListPanel.add(btnAlerts);
        navListPanel.add(Box.createVerticalStrut(6));
        navListPanel.add(btnStaff);
        navListPanel.add(Box.createVerticalStrut(6));
        navListPanel.add(btnCancelBills);
        navListPanel.add(Box.createVerticalGlue());

        sidebar.add(navListPanel, BorderLayout.CENTER);

        // Pinned Logout Button at Bottom
        JPanel logoutPanel = new JPanel(new BorderLayout());
        logoutPanel.setBackground(Theme.SIDEBAR_BG);
        logoutPanel.setBorder(BorderFactory.createEmptyBorder(15, 12, 20, 12));

        JButton btnLogout = new JButton("Logout");
        styleSidebarButton(btnLogout, false);
        btnLogout.setHorizontalAlignment(SwingConstants.LEFT);
        btnLogout.setIconTextGap(10);
        btnLogout.addActionListener(e -> handleLogout());

        logoutPanel.add(btnLogout, BorderLayout.CENTER);
        sidebar.add(logoutPanel, BorderLayout.SOUTH);

        // Default to Dashboard tab
        navDashboard = btnDashboard;
        setActiveNavButton(btnDashboard);

        return sidebar;
    }

    private JButton createNavButton(String text, String cardName, boolean requiresOwner) {
        JButton btn = new JButton(text);
        styleSidebarButton(btn, false);
        navButtons.add(btn);

        btn.addActionListener(e -> {
            // Role-Based Access Enforcement
            if (requiresOwner) {
                // Cashier -> pop-up where an Owner signs in temporarily
                if (!ensureOwner("\"" + text + "\" is an Owner-only function.")) {
                    return; // Do NOT switch panel or update button highlight
                }
            } else {
                endTemporaryOwner();   // leaving the owner tabs ends the temporary access
            }

            // Switch to requested card
            setActiveNavButton(btn);
            cardLayout.show(contentAreaPanel, cardName);

            // Refresh stats if returning to Home Dashboard
            if (CARD_DASHBOARD.equals(cardName)) {
                refreshDashboardStats();
            } else if (CARD_BILLING.equals(cardName) && billingPanel != null) {
                billingPanel.loadAvailableMedicines();
            } else if (CARD_INVENTORY.equals(cardName) && inventoryPanel != null) {
                inventoryPanel.loadInventoryData();
            } else if (CARD_ALERTS.equals(cardName) && alertsPanel != null) {
                alertsPanel.loadAlerts();
            } else if (CARD_STAFF.equals(cardName) && staffPanel != null) {
                staffPanel.loadStaffData();
            } else if (CARD_CANCEL_BILLS.equals(cardName) && cancelBillPanel != null) {
                cancelBillPanel.loadCanceledBills();
            }
        });

        return btn;
    }

    // Right-click (or double-click) a tab button to open that tab in its own resizable window
    private void addPopOutSupport(JButton btn, String title, String cardName, boolean requiresOwner) {
        btn.setToolTipText("Double-click or right-click to open in a separate window");

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem item = new javax.swing.JMenuItem("Open in separate window");
        item.addActionListener(e -> openInSeparateWindow(title, cardName, requiresOwner));
        menu.add(item);

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menu.show(btn, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menu.show(btn, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                   // openInSeparateWindow(title, cardName, requiresOwner);
                }
            }
        });
    }

    private void openInSeparateWindow(String title, String cardName, boolean requiresOwner) {
        boolean startedElevation = false;
        if (requiresOwner && !UserSession.isOwner()) {
            if (!ensureOwner("\"" + title + "\" is an Owner-only function.")) {
                return;
            }
            startedElevation = true;
        }

        JPanel panel;
        switch (cardName) {
            case CARD_BILLING -> panel = new BillingPanel();
            case CARD_INVENTORY -> panel = new InventoryPanel();
            case CARD_ALERTS -> panel = new AlertsPanel();
            case CARD_STAFF -> panel = new StaffPanel();
            case CARD_CANCEL_BILLS -> panel = new CancelBillPanel();
            default -> {
                return;
            }
        }

        JFrame window = new JFrame("Curo Loss-Prevention Engine - " + title);
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setContentPane(new FitScrollPane(panel));
        UIHelper.fitToScreen(window, 1000, 700, 640, 460);
        window.setResizable(true);
        UIHelper.applyContrast(window.getContentPane());
        if (startedElevation) {
            window.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    endTemporaryOwner();
                }
            });
        }
        window.setVisible(true);
    }

    private void styleSidebarButton(JButton btn, boolean isActive) {
        btn.setFont(Theme.BOLD_BODY_FONT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btn.setPreferredSize(new Dimension(150, 42));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        if (isActive) {
            btn.setBackground(Theme.SIDEBAR_ACTIVE);
            btn.setForeground(Color.WHITE);
        } else {
            btn.setBackground(Theme.SIDEBAR_BG);
            btn.setForeground(Theme.SIDEBAR_TEXT);
        }

        // Hover Effect
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn != activeButton) {
                    btn.setBackground(Theme.SIDEBAR_ACTIVE);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (btn != activeButton) {
                    btn.setBackground(Theme.SIDEBAR_BG);
                }
            }
        });
    }

    private void setActiveNavButton(JButton targetBtn) {
        this.activeButton = targetBtn;
        for (JButton b : navButtons) {
            if (b == targetBtn) {
                b.setBackground(Theme.SIDEBAR_ACTIVE);
                b.setForeground(UIHelper.readableOn(Theme.SIDEBAR_ACTIVE));
            } else {
                b.setBackground(Theme.SIDEBAR_BG);
                b.setForeground(Theme.SIDEBAR_TEXT);
            }
        }
    }

    // =========================================================================
    // 3. Dashboard Overview Panel (Home View)
    // =========================================================================

    private JPanel buildDashboardHomePanel() {
        JPanel homePanel = new JPanel(new BorderLayout(0, 20));
        homePanel.setBackground(Theme.MAIN_BG);
        homePanel.setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        // 3.1 Top Header: System Title & Logged-In User with Role Badge
        JPanel headerPanel = buildTopHeader();
        homePanel.add(headerPanel, BorderLayout.NORTH);

        // Center Content Area: 4 Summary Cards + Bottom Split
        JPanel centerContainer = new JPanel(new BorderLayout(0, 20));
        centerContainer.setBackground(Theme.MAIN_BG);

        // 3.2 Row of 4 Summary Cards
        JPanel summaryRow = buildSummaryCardsRow();
        centerContainer.add(summaryRow, BorderLayout.NORTH);

        // 3.3 Bottom Split: Left Bar Chart + Right Recent Alerts Table
        JPanel bottomSplit = buildBottomSplit();
        centerContainer.add(bottomSplit, BorderLayout.CENTER);

        homePanel.add(centerContainer, BorderLayout.CENTER);
        return homePanel;
    }

    private JPanel buildTopHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MAIN_BG);

        // Left: Title & Subtitle
        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setBackground(Theme.MAIN_BG);

        JLabel lblTitle = UIHelper.createPageTitle("Curo Loss-Prevention Engine");
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);


        JLabel lblSubtitle = new JLabel("");
        lblSubtitle.setFont(Theme.BODY_FONT);
        lblSubtitle.setForeground(Theme.TEXT_MUTED);
        lblSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSubtitle);
        header.add(titleBox, BorderLayout.WEST);

        // Right: Current User Info & Role Badge
        StaffMember currentUser = UserSession.getCurrentUser();
        String staffName = (currentUser != null && currentUser.getStaffName() != null)
                ? currentUser.getStaffName()
                : "Staff User";
        String role = (currentUser != null && currentUser.getRole() != null)
                ? currentUser.getRole().toUpperCase()
                : "GUEST";

        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        userPanel.setBackground(Theme.MAIN_BG);

        JLabel lblUserName = new JLabel(staffName);
        lblUserName.setFont(Theme.BOLD_BODY_FONT);
        lblUserName.setForeground(Theme.TEXT_DARK);

        JLabel lblRoleBadge = createRoleBadge(role);

        userPanel.add(lblUserName);
        userPanel.add(lblRoleBadge);
        header.add(userPanel, BorderLayout.EAST);

        return header;
    }

    private JLabel createRoleBadge(String role) {
        JLabel badge = new JLabel("  " + role + "  ");
        badge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        badge.setOpaque(true);

        if ("OWNER".equalsIgnoreCase(role)) {
            badge.setBackground(Theme.SIDEBAR_BG);
            badge.setForeground(Color.WHITE);
        } else {
            badge.setBackground(new Color(224, 231, 255));
            badge.setForeground(new Color(30, 58, 138));
        }

        badge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        return badge;
    }

    private JPanel buildSummaryCardsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 15, 0));
        row.setBackground(Theme.MAIN_BG);

        // 1. Total Medicines Card
        lblTotalMedicines = new JLabel("0");
        row.add(createSummaryCard("Total Medicines", lblTotalMedicines, "Registered in inventory"));

        // 2. Today's Sales Card
        lblTodaySales = new JLabel("LKR 0.00");
        row.add(createSummaryCard("Today's Sales", lblTodaySales, "Net revenue today"));

        // 3. Expiring Soon (7 Months) Card
        lblExpiringSoon = new JLabel("0");
        row.add(createSummaryCard("Expiring Soon (7 Months)", lblExpiringSoon, "Batches <= 7 months"));

        // 4. Low Stock Alerts Card
        lblLowStock = new JLabel("0");
        row.add(createSummaryCard("Low Stock Alerts", lblLowStock, "Below 50% threshold"));

        return row;
    }

    private JPanel createSummaryCard(String title, JLabel valueLabel, String subtext) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        UIHelper.styleCardPanel(card);

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(Theme.BODY_FONT);
        lblTitle.setForeground(Theme.TEXT_MUTED);
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        valueLabel.setFont(Theme.STAT_NUMBER_FONT);
        valueLabel.setForeground(Theme.TEXT_DARK);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSub = new JLabel(subtext);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(Theme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(lblTitle);
        card.add(Box.createVerticalStrut(8));
        card.add(valueLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(lblSub);

        return card;
    }

    private JPanel buildBottomSplit() {
        JPanel bottomSplit = new JPanel(new GridLayout(1, 2, 15, 0));
        bottomSplit.setBackground(Theme.MAIN_BG);

        // Left Card: Last 7 Days Sales Bar Chart
        JPanel leftCard = new JPanel(new BorderLayout(10, 10));
        UIHelper.styleCardPanel(leftCard);

        JPanel chartHeader = new JPanel(new BorderLayout());
        chartHeader.setBackground(Theme.CARD_BG);

        JLabel lblChartTitle = new JLabel("Last 7 Days Sales");
        lblChartTitle.setFont(Theme.SUBTITLE_FONT);
        lblChartTitle.setForeground(Theme.TEXT_DARK);

        JLabel lblChartDays = new JLabel("Mon - Sun");
        lblChartDays.setFont(Theme.BODY_FONT.deriveFont(12f));
        lblChartDays.setForeground(Theme.TEXT_MUTED);

        chartHeader.add(lblChartTitle, BorderLayout.WEST);
        chartHeader.add(lblChartDays, BorderLayout.EAST);
        leftCard.add(chartHeader, BorderLayout.NORTH);

        barChartPanel = new SalesBarChartPanel();
        leftCard.add(barChartPanel, BorderLayout.CENTER);

        bottomSplit.add(leftCard);

        // Right Card: Recent Alerts Read-only JTable
        JPanel rightCard = new JPanel(new BorderLayout(10, 10));
        UIHelper.styleCardPanel(rightCard);

        JPanel alertsHeader = new JPanel(new BorderLayout());
        alertsHeader.setBackground(Theme.CARD_BG);

        JLabel lblAlertsTitle = new JLabel("Recent Alerts");
        lblAlertsTitle.setFont(Theme.SUBTITLE_FONT);
        lblAlertsTitle.setForeground(Theme.TEXT_DARK);

        JLabel lblAlertsFeed = new JLabel("Live Updates");
        lblAlertsFeed.setFont(Theme.BODY_FONT.deriveFont(12f));
        lblAlertsFeed.setForeground(Theme.TEXT_MUTED);

        alertsHeader.add(lblAlertsTitle, BorderLayout.WEST);
        alertsHeader.add(lblAlertsFeed, BorderLayout.EAST);
        rightCard.add(alertsHeader, BorderLayout.NORTH);

        String[] cols = {"Type", "Subject", "Description"};
        recentAlertsModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false; // Read-only table
            }
        };

        tblRecentAlerts = new JTable(recentAlertsModel);
        UIHelper.styleTable(tblRecentAlerts);
        tblRecentAlerts.getColumnModel().getColumn(0).setPreferredWidth(110);
        tblRecentAlerts.getColumnModel().getColumn(1).setPreferredWidth(140);
        tblRecentAlerts.getColumnModel().getColumn(2).setPreferredWidth(260);

        // Badge-style cell renderer for Alert Type
        tblRecentAlerts.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                String valStr = (value != null) ? value.toString().toUpperCase() : "";
                if (!isSelected) {
                    if (valStr.contains("EXPIRED") || valStr.contains("OUT OF STOCK")) {
                        setForeground(new Color(185, 28, 28));
                    } else if (valStr.contains("EXPIR") || valStr.contains("LOW_STOCK") || valStr.contains("LOW STOCK")) {
                        setForeground(new Color(161, 72, 6));
                    } else if (valStr.contains("SECURITY")) {
                        setForeground(new Color(126, 34, 206));
                    } else {
                        setForeground(Theme.TEXT_DARK);
                    }
                    setFont(Theme.BOLD_BODY_FONT.deriveFont(12f));
                }
                return c;
            }
        });

        JScrollPane alertsScrollPane = new JScrollPane(tblRecentAlerts);
        alertsScrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1));
        alertsScrollPane.getViewport().setBackground(Theme.CARD_BG);
        rightCard.add(alertsScrollPane, BorderLayout.CENTER);

        bottomSplit.add(rightCard);

        return bottomSplit;
    }

    // =========================================================================
    // Refresh Logic & Notification Subscriptions
    // =========================================================================

    private void subscribeToNotifications() {
        notificationManager.subscribe(() -> {
            SwingUtilities.invokeLater(this::refreshAlertsTelemetry);
        });
    }

    /**
     * Refreshes the Dashboard statistics when switching back to the "Dashboard" tab
     * or upon startup.
     */
    private void refreshDashboardStats() {
        // 1. Fetch Total Medicines
        int totalMeds;
        try {
            totalMeds = inventoryDAO.getTotalMedicineCount();
            if (totalMeds == 0) {
                List<Medicine> all = inventoryDAO.getAllInventory();
                totalMeds = (all != null) ? all.size() : 0;
            }
        } catch (SQLException ex) {
            try {
                List<Medicine> all = inventoryDAO.getAllInventory();
                totalMeds = (all != null) ? all.size() : 0;
            } catch (SQLException e) {
                totalMeds = 0;
            }
        }
        if (lblTotalMedicines != null) {
            lblTotalMedicines.setText(String.valueOf(totalMeds));
        }

        // 2. Fetch Today's Sales
        double todaySales;
        try {
            todaySales = billingDAO.getTodaySales();
        } catch (SQLException ex) {
            todaySales = 0.0; // Graceful fallback
        }
        if (lblTodaySales != null) {
            lblTodaySales.setText(String.format("LKR %,.2f", todaySales));
        }

        // 3. Fetch Last 7 Days Sales for Bar Chart
        Map<String, Double> last7Days = new LinkedHashMap<>();
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (String d : days) {
            last7Days.put(d, 0.0);
        }

        try {
            Map<String, Double> daoSales = billingDAO.getLast7DaysSales();
            if (daoSales != null) {
                for (String d : days) {
                    if (daoSales.containsKey(d)) {
                        last7Days.put(d, daoSales.get(d));
                    }
                }
            }
        } catch (SQLException ex) {
            // Graceful fallback to 0.0
        }

        if (barChartPanel != null) {
            barChartPanel.setSalesData(last7Days);
        }

        // 4. Refresh Alerts telemetry
        refreshAlertsTelemetry();
    }

    /**
     * Updates alert counts in summary cards and reloads recent alerts table.
     */
    private void refreshAlertsTelemetry() {
        List<Notification> allAlerts = notificationManager.getAllAlerts();

        long expiryCount = 0;
        long lowStockCount = 0;

        for (Notification n : allAlerts) {
            if (n.getType() != null) {
                String type = n.getType().toUpperCase();
                if (type.contains("EXPIR")) {
                    expiryCount++;
                }
                if (type.contains("STOCK")) {
                    lowStockCount++;
                }
            }
        }

        if (lblExpiringSoon != null) {
            lblExpiringSoon.setText(String.valueOf(expiryCount));
        }
        if (lblLowStock != null) {
            lblLowStock.setText(String.valueOf(lowStockCount));
        }

        // Populate Recent Alerts table
        if (recentAlertsModel != null) {
            recentAlertsModel.setRowCount(0);
            for (Notification n : allAlerts) {
                recentAlertsModel.addRow(new Object[]{
                    n.getType() != null ? n.getType() : "INFO",
                    n.getTitle() != null ? n.getTitle() : "",
                    n.getMessage() != null ? n.getMessage() : ""
                });
            }
        }
    }

    // =========================================================================
    // 4. Logout Action
    // =========================================================================

    private void handleLogout() {
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to log out of Curo Engine?",
            "Confirm Logout",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            endTemporaryOwner();
            UserSession.logout();
            this.dispose();
            new LoginFrame().setVisible(true);
        }
    }

    // =========================================================================
    // Custom Graphics2D Bar Chart Component
    // =========================================================================

    /**
     * SalesBarChartPanel renders a clean, modern antialiased bar chart for 7-day sales.
     */
    private static class SalesBarChartPanel extends JPanel {

        private final Map<String, Double> salesData = new LinkedHashMap<>();

        public SalesBarChartPanel() {
            setBackground(Theme.CARD_BG);
            String[] defaultDays = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            for (String d : defaultDays) {
                salesData.put(d, 0.0);
            }
        }

        public void setSalesData(Map<String, Double> data) {
            salesData.clear();
            if (data != null) {
                salesData.putAll(data);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            // Enable smooth antialiased rendering
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            int padTop = 30;
            int padBottom = 35;
            int padLeft = 25;
            int padRight = 25;

            int chartWidth = width - padLeft - padRight;
            int chartHeight = height - padTop - padBottom;

            if (chartWidth <= 0 || chartHeight <= 0) {
                g2.dispose();
                return;
            }

            // Find maximum sales value (graceful fallback if all 0.0)
            double maxSales = 0.0;
            for (double val : salesData.values()) {
                if (val > maxSales) {
                    maxSales = val;
                }
            }
            if (maxSales <= 0.0) {
                maxSales = 1000.0; // Benchmark scale when no sales exist
            }

            // Draw subtle horizontal grid lines (4 divisions)
            g2.setColor(new Color(241, 245, 249));
            for (int i = 1; i <= 3; i++) {
                int gridY = padTop + (int) (chartHeight * (i / 4.0));
                g2.drawLine(padLeft, gridY, padLeft + chartWidth, gridY);
            }

            // Draw Bottom Baseline
            int baselineY = padTop + chartHeight;
            g2.setColor(Theme.BORDER_COLOR);
            g2.drawLine(padLeft, baselineY, padLeft + chartWidth, baselineY);

            // Draw Bars for Mon - Sun
            int dayCount = Math.max(1, salesData.size());
            int slotWidth = chartWidth / dayCount;
            int barWidth = Math.min(36, Math.max(16, slotWidth - 18));

            int index = 0;
            for (Map.Entry<String, Double> entry : salesData.entrySet()) {
                String day = entry.getKey();
                double amount = entry.getValue();

                int slotCenterX = padLeft + (index * slotWidth) + (slotWidth / 2);
                int barX = slotCenterX - (barWidth / 2);

                int barHeight = (int) Math.round((amount / maxSales) * (chartHeight - 15));
                if (amount > 0 && barHeight < 6) {
                    barHeight = 6;
                }
                int barY = baselineY - barHeight;

                if (amount > 0.0) {
                    // Modern gradient bar (Theme.SIDEBAR_BG to Theme.SIDEBAR_ACTIVE)
                    GradientPaint gradient = new GradientPaint(
                        barX, barY, Theme.SIDEBAR_ACTIVE,
                        barX, baselineY, Theme.SIDEBAR_BG
                    );
                    g2.setPaint(gradient);
                    g2.fillRoundRect(barX, barY, barWidth, barHeight, 6, 6);

                    // Formatted value above bar
                    g2.setColor(Theme.TEXT_MUTED);
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                    String valText = formatShortCurrency(amount);
                    int textWidth = g2.getFontMetrics().stringWidth(valText);
                    g2.drawString(valText, slotCenterX - (textWidth / 2), Math.max(12, barY - 4));
                } else {
                    // Zero sales indicator
                    g2.setColor(Theme.BORDER_COLOR);
                    g2.fillRect(barX, baselineY - 2, barWidth, 2);

                    g2.setColor(Theme.TEXT_MUTED);
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                    String valText = "0";
                    int textWidth = g2.getFontMetrics().stringWidth(valText);
                    g2.drawString(valText, slotCenterX - (textWidth / 2), baselineY - 6);
                }

                // Day Label below baseline
                g2.setColor(Theme.TEXT_DARK);
                g2.setFont(Theme.BOLD_BODY_FONT.deriveFont(12f));
                int labelWidth = g2.getFontMetrics().stringWidth(day);
                g2.drawString(day, slotCenterX - (labelWidth / 2), baselineY + 18);

                index++;
            }

            g2.dispose();
        }

        private String formatShortCurrency(double value) {
            if (value >= 1_000_000) {
                return String.format("%.1fM", value / 1_000_000.0);
            } else if (value >= 1_000) {
                return String.format("%.1fk", value / 1_000.0);
            } else {
                return String.format("%.0f", value);
            }
        }
    }
}
