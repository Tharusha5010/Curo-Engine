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
                    openInSeparateWindow(title, cardName, requiresOwner);
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

}