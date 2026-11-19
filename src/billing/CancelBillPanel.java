package billing;

import auth.UserSession;
import exceptions.UnauthorizedAccessException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.sql.SQLException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import models.Bill;
import models.BillItem;
import models.CanceledBill;
import models.StaffMember;
import ui.Theme;
import ui.UIHelper;

/**
 * CancelBillPanel allows an active Owner to look up past sales bills, inspect their line items,
 * authorize cancellation with a stated reason, and automatically restore inventory stock.
 */
public class CancelBillPanel extends JPanel {

    // Backend Access
    private final BillingDAO billingDAO = new BillingDAO();

    // 1. Search & Preview Components
    private JTextField txtSearchBillId;
    private JButton btnFindBill;

    private JLabel lblPreviewCashier;
    private JLabel lblPreviewDate;
    private JLabel lblPreviewNetTotal;
    private JLabel lblPreviewStatus;

    private JTable tblPreviewItems;
    private DefaultTableModel previewTableModel;

    // Currently Loaded Bill
    private Bill currentLoadedBill = null;
    private boolean isCurrentBillAlreadyCanceled = false;

    // 2. Cancel Bill Action Components (Owner Only)
    private JTextField txtReason;
    private JButton btnCancelBill;

    // 3. Canceled Bills History Components
    private JTable tblHistory;
    private DefaultTableModel historyTableModel;
    private JButton btnRefreshHistory;

    public CancelBillPanel() {
        initComponents();
        loadCanceledBills();
    }

    private void initComponents() {
        setLayout(new BorderLayout(15, 15));
        setBackground(Theme.MAIN_BG);
        setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // Top Header
        JPanel headerPanel = buildHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Center Split Layout (Left: Search & Preview & Cancel Action, Right: History Log)
        JPanel centerGrid = new JPanel(new GridLayout(1, 2, 15, 0));
        centerGrid.setBackground(Theme.MAIN_BG);

        JPanel leftCol = buildLeftColumn();
        JPanel rightCol = buildRightColumn();

        centerGrid.add(leftCol);
        centerGrid.add(rightCol);

        add(centerGrid, BorderLayout.CENTER);
    }

    // =========================================================================
    // Header Panel
    // =========================================================================

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MAIN_BG);

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setBackground(Theme.MAIN_BG);

        JLabel lblTitle = UIHelper.createPageTitle("Cancel Bills & Stock Restoration (Owner Only)");
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);


        JLabel lblSub = new JLabel("");
        lblSub.setFont(Theme.BODY_FONT);
        lblSub.setForeground(Theme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);
        header.add(titleBox, BorderLayout.WEST);

        // Owner status pill
        StaffMember user = UserSession.getCurrentUser();
        String role = (user != null && user.getRole() != null) ? user.getRole().toUpperCase() : "OWNER";
        JLabel lblOwnerBadge = new JLabel("Authorized Role: " + role);
        lblOwnerBadge.setFont(Theme.BOLD_BODY_FONT);
        lblOwnerBadge.setForeground(Theme.TEXT_DARK);
        lblOwnerBadge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        lblOwnerBadge.setOpaque(true);
        lblOwnerBadge.setBackground(Theme.CARD_BG);
        header.add(lblOwnerBadge, BorderLayout.EAST);

        return header;
    }

    // =========================================================================
    // Left Column: 1. Search Bill & Preview + 2. Cancel Action
    // =========================================================================

    private JPanel buildLeftColumn() {
        JPanel left = new JPanel(new BorderLayout(0, 15));
        left.setBackground(Theme.MAIN_BG);

        // 1. Search & Preview Card (UIHelper.styleCardPanel)
        JPanel searchPreviewCard = buildSearchAndPreviewCard();
        left.add(searchPreviewCard, BorderLayout.CENTER);

        // 2. Cancel Bill Action Card (UIHelper.styleCardPanel)
        JPanel cancelActionCard = buildCancelActionCard();
        left.add(cancelActionCard, BorderLayout.SOUTH);

        return left;
    }

    private JPanel buildSearchAndPreviewCard() {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        UIHelper.styleCardPanel(card);

        // Header Title
        JLabel lblTitle = new JLabel("1. Search Bill & Verify Items");
        lblTitle.setFont(Theme.SUBTITLE_FONT);
        lblTitle.setForeground(Theme.TEXT_DARK);
        card.add(lblTitle, BorderLayout.NORTH);

        // Center Content Container
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(Theme.CARD_BG);

        // Search Input Bar
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchBar.setBackground(Theme.CARD_BG);

        JLabel lblBillId = new JLabel("Enter Bill ID:");
        lblBillId.setFont(Theme.BOLD_BODY_FONT);
        lblBillId.setForeground(Theme.TEXT_DARK);
        searchBar.add(lblBillId);

        txtSearchBillId = new JTextField(8);
        UIHelper.styleTextField(txtSearchBillId);
        txtSearchBillId.setPreferredSize(new Dimension(100, 36));
        txtSearchBillId.addActionListener(e -> findBill());
        searchBar.add(txtSearchBillId);

        btnFindBill = new JButton("Find Bill");
        UIHelper.stylePrimaryButton(btnFindBill);
        btnFindBill.addActionListener(e -> findBill());
        searchBar.add(btnFindBill);

        content.add(searchBar, BorderLayout.NORTH);

        // Bill Metadata Preview Banner
        JPanel previewBanner = new JPanel(new GridLayout(2, 2, 8, 6));
        previewBanner.setBackground(Theme.CARD_BG);
        previewBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        lblPreviewCashier = new JLabel("Cashier ID: -");
        lblPreviewCashier.setFont(Theme.BODY_FONT);

        lblPreviewDate = new JLabel("Date/Time: -");
        lblPreviewDate.setFont(Theme.BODY_FONT);

        lblPreviewNetTotal = new JLabel("Net Total: LKR 0.00");
        lblPreviewNetTotal.setFont(Theme.BOLD_BODY_FONT);
        lblPreviewNetTotal.setForeground(Theme.TEXT_DARK);

        lblPreviewStatus = new JLabel("Status: Awaiting Search");
        lblPreviewStatus.setFont(Theme.BOLD_BODY_FONT);
        lblPreviewStatus.setForeground(Theme.TEXT_MUTED);

        previewBanner.add(lblPreviewCashier);
        previewBanner.add(lblPreviewDate);
        previewBanner.add(lblPreviewNetTotal);
        previewBanner.add(lblPreviewStatus);

        // Line Items Table
        String[] cols = {"Medicine Name", "Unit Price (LKR)", "Qty", "Line Total (LKR)"};
        previewTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false; // Read-only
            }
        };

        tblPreviewItems = new JTable(previewTableModel);
        UIHelper.styleTable(tblPreviewItems);
        tblPreviewItems.getColumnModel().getColumn(0).setPreferredWidth(170);
        tblPreviewItems.getColumnModel().getColumn(1).setPreferredWidth(100);
        tblPreviewItems.getColumnModel().getColumn(2).setPreferredWidth(60);
        tblPreviewItems.getColumnModel().getColumn(3).setPreferredWidth(100);

        JScrollPane scrollPane = new JScrollPane(tblPreviewItems);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);

        // Combine Banner + Table in Center
        JPanel previewCenter = new JPanel(new BorderLayout(0, 8));
        previewCenter.setBackground(Theme.CARD_BG);
        previewCenter.add(previewBanner, BorderLayout.NORTH);
        previewCenter.add(scrollPane, BorderLayout.CENTER);

        content.add(previewCenter, BorderLayout.CENTER);
        card.add(content, BorderLayout.CENTER);

        return card;
    }

    private JPanel buildCancelActionCard() {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        UIHelper.styleCardPanel(card);

        JLabel lblTitle = new JLabel("2. Authorize Bill Cancellation");
        lblTitle.setFont(Theme.SUBTITLE_FONT);
        lblTitle.setForeground(Theme.TEXT_DARK);
        card.add(lblTitle, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.CARD_BG);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Reason input
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel lblReason = new JLabel("Cancellation Reason:");
        lblReason.setFont(Theme.BOLD_BODY_FONT);
        lblReason.setForeground(Theme.TEXT_DARK);
        form.add(lblReason, gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        gbc.weightx = 1.0;
        txtReason = new JTextField();
        UIHelper.styleTextField(txtReason);
        txtReason.setPreferredSize(new Dimension(220, 36));
        form.add(txtReason, gbc);

        // Cancel button
        gbc.gridx = 2; gbc.gridy = 0;
        gbc.weightx = 0.0;
        btnCancelBill = new JButton("Cancel Bill & Restore Stock");
        UIHelper.styleDangerButton(btnCancelBill);
        btnCancelBill.setEnabled(false); // Disabled until an active bill is found
        btnCancelBill.addActionListener(e -> handleCancelBillAction());
        form.add(btnCancelBill, gbc);

        card.add(form, BorderLayout.CENTER);
        return card;
    }

    // =========================================================================
    // Right Column: 3. Canceled Bills History Table
    // =========================================================================

    private JPanel buildRightColumn() {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        UIHelper.styleCardPanel(card);

        // History Card Header
        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setBackground(Theme.CARD_BG);

        JLabel lblTitle = new JLabel("3. Canceled Bills Audit Log");
        lblTitle.setFont(Theme.SUBTITLE_FONT);
        lblTitle.setForeground(Theme.TEXT_DARK);
        cardHeader.add(lblTitle, BorderLayout.WEST);

        btnRefreshHistory = new JButton("Refresh History");
        UIHelper.styleSecondaryButton(btnRefreshHistory);
        btnRefreshHistory.addActionListener(e -> loadCanceledBills());
        cardHeader.add(btnRefreshHistory, BorderLayout.EAST);

        card.add(cardHeader, BorderLayout.NORTH);

        // Canceled Bills Table: Cancel ID, Bill ID, Canceled By, Canceled At, Reason
        String[] cols = {"Cancel ID", "Bill ID", "Canceled By", "Canceled At", "Reason"};
        historyTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false; // Read-only history
            }
        };

        tblHistory = new JTable(historyTableModel);
        UIHelper.styleTable(tblHistory);
        tblHistory.getColumnModel().getColumn(0).setPreferredWidth(65);
        tblHistory.getColumnModel().getColumn(1).setPreferredWidth(65);
        tblHistory.getColumnModel().getColumn(2).setPreferredWidth(90);
        tblHistory.getColumnModel().getColumn(3).setPreferredWidth(140);
        tblHistory.getColumnModel().getColumn(4).setPreferredWidth(180);

        JScrollPane scrollPane = new JScrollPane(tblHistory);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        card.add(scrollPane, BorderLayout.CENTER);

        return card;
    }

    // =========================================================================
    // 1. Search Bill Action
    // =========================================================================

    private void findBill() {
        String idText = txtSearchBillId.getText().trim();
        if (idText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a valid Bill ID.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtSearchBillId.requestFocusInWindow();
            return;
        }

        int billId;
        try {
            billId = Integer.parseInt(idText);
            if (billId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Bill ID must be a positive integer.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtSearchBillId.requestFocusInWindow();
            return;
        }

        try {
            // Check if already canceled
            isCurrentBillAlreadyCanceled = billingDAO.isBillCanceled(billId);

            // Fetch Bill details and line items
            Bill bill = billingDAO.getBillById(billId);
            if (bill == null) {
                resetPreview("Bill #" + billId + " not found in database.");
                JOptionPane.showMessageDialog(this, "No record found for Bill #" + billId + ".", "Bill Not Found", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            currentLoadedBill = bill;

            // Populate Metadata Banner
            lblPreviewCashier.setText("Cashier ID: " + bill.getCashierId());
            lblPreviewDate.setText("Date: " + (bill.getCreateAt() != null ? bill.getCreateAt().toString() : "-"));
            lblPreviewNetTotal.setText(String.format("Net Total: LKR %,.2f", bill.getNetTotal()));

            if (isCurrentBillAlreadyCanceled) {
                lblPreviewStatus.setText("STATUS: ALREADY CANCELED");
                lblPreviewStatus.setForeground(Theme.DANGER_BTN);
                btnCancelBill.setEnabled(false);
            } else {
                lblPreviewStatus.setText("STATUS: ACTIVE (Eligible to Cancel)");
                lblPreviewStatus.setForeground(new Color(22, 101, 52)); // Green
                btnCancelBill.setEnabled(true);
            }

            // Populate Line Items Table
            previewTableModel.setRowCount(0);
            List<BillItem> items = bill.getItems();
            if (items != null) {
                for (BillItem it : items) {
                    previewTableModel.addRow(new Object[]{
                        it.getMedicineName(),
                        String.format("%.2f", it.getSoldPrice()),
                        it.getQuantity(),
                        String.format("%.2f", it.getLineTotal())
                    });
                }
            }

            txtReason.requestFocusInWindow();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error looking up bill: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resetPreview(String statusMsg) {
        currentLoadedBill = null;
        isCurrentBillAlreadyCanceled = false;
        lblPreviewCashier.setText("Cashier ID: -");
        lblPreviewDate.setText("Date/Time: -");
        lblPreviewNetTotal.setText("Net Total: LKR 0.00");
        lblPreviewStatus.setText("Status: " + statusMsg);
        lblPreviewStatus.setForeground(Theme.TEXT_MUTED);
        previewTableModel.setRowCount(0);
        btnCancelBill.setEnabled(false);
    }

    // =========================================================================
    // 2. Cancel Bill Action (Owner Only)
    // =========================================================================

    private void handleCancelBillAction() {
        if (currentLoadedBill == null) {
            JOptionPane.showMessageDialog(this, "Please search and load an active bill first.", "No Bill Loaded", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int billId = currentLoadedBill.getBillId();

        if (isCurrentBillAlreadyCanceled) {
            JOptionPane.showMessageDialog(this, "Bill #" + billId + " has already been canceled.", "Cannot Cancel", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String reason = txtReason.getText().trim();
        if (reason.isEmpty()) {
            reason = "Customer return / wrong billing";
        }

        // Confirmation prompt
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to cancel Bill #" + billId + "?\n" +
            "Net Total: LKR " + String.format("%.2f", currentLoadedBill.getNetTotal()) + "\n\n" +
            "All dispensed medicine quantities will be immediately restored to inventory stock.",
            "Confirm Cancellation & Stock Restoration",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        StaffMember currentStaff = UserSession.getCurrentUser();
        String staffId = (currentStaff != null && currentStaff.getStaffId() != null)
                ? currentStaff.getStaffId()
                : "OWNER";

        try {
            // Call billingDAO.cancelBill(billId, UserSession.getCurrentUser().getStaffId(), reason)
            boolean success = billingDAO.cancelBill(billId, staffId, reason);

            if (success) {
                JOptionPane.showMessageDialog(
                    this,
                    "Bill #" + billId + " has been successfully canceled!\n" +
                    "All inventory stock for this bill's line items has been restored.",
                    "Cancellation Confirmed",
                    JOptionPane.INFORMATION_MESSAGE
                );

                txtReason.setText("");
                txtSearchBillId.setText("");
                resetPreview("Canceled successfully.");
                loadCanceledBills();
            }

        } catch (UnauthorizedAccessException ex) {
            JOptionPane.showMessageDialog(
                this,
                ex.getMessage(),
                "Access Denied - Owner Only",
                JOptionPane.ERROR_MESSAGE
            );
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Database error while canceling bill: " + ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(
                this,
                ex.getMessage(),
                "State Error",
                JOptionPane.WARNING_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Error during cancellation: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // =========================================================================
    // 3. Canceled Bills History Table
    // =========================================================================

    public void loadCanceledBills() {
        if (historyTableModel == null) return;
        historyTableModel.setRowCount(0);

        try {
            List<CanceledBill> list = billingDAO.getAllCanceledBills();
            if (list != null) {
                for (CanceledBill cb : list) {
                    historyTableModel.addRow(new Object[]{
                        cb.getCancelId(),
                        cb.getBillId(),
                        cb.getCanceledBy(),
                        cb.getCanceledAt() != null ? cb.getCanceledAt().toString() : "-",
                        cb.getReason()
                    });
                }
            }
        } catch (UnauthorizedAccessException ex) {
            // Non-owners do not load canceled bills
        } catch (SQLException ex) {
            System.err.println("Warning: Failed to load canceled bills: " + ex.getMessage());
        }
    }
}
