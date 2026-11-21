package billing;

import auth.UserSession;
import exceptions.ExpiredMedicineException;
import exceptions.OutOfStockException;
import exceptions.UnauthorizedAccessException;
import inventory.InventoryDAO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import models.Bill;
import models.BillItem;
import models.Medicine;
import models.StaffMember;
import ui.OwnerAccess;
import ui.Theme;
import ui.UIHelper;

/**
 * BillingPanel provides an interactive Point of Sale (POS) interface for dispensing medicines.
 * Features smart auto-suggest medicine search, real-time cart-aware FEFO stock & multi-batch pricing,
 * single-choice discount calculation, dual-path checkout (Normal vs. Free Issue),
 * prescription verification rules, and thermal receipt printing.
 */
public class BillingPanel extends JPanel {

    // Backend DAOs
    private final BillingDAO billingDAO = new BillingDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();

    // Auto-Suggest Search Components
    private JTextField txtSearch;
    private JPopupMenu suggestionPopup;
    private DefaultListModel<String> suggestionModel;
    private JList<String> suggestionList;
    private boolean isSelectingSuggestion = false;

    // FEFO Batch Telemetry Display Labels
    private JLabel lblBatchNo;
    private JLabel lblStock;
    private JLabel lblUnitPrice;
    private JLabel lblExpiryDate;
    private JLabel lblRxStatus;

    // Active Selected Medicine & Available Stock
    private Medicine currentSelectedMedicine = null;
    private String currentSelectedMedicineName = null;
    private List<Medicine> currentMedicineBatches = new ArrayList<>();
    private int currentTotalDbStock = 0;
    private int currentAvailableStock = 0;

    // Cart Input Components
    private JTextField txtQuantity;
    private JButton btnAddToCart;
    private JTable tblCart;
    private DefaultTableModel cartTableModel;
    private JButton btnRemoveItem;
    private JButton btnClearCart;

    // In-Memory Cart State
    private final List<CartEntry> cartItems = new ArrayList<>();

    // Billing Options Components
    private JCheckBox chkPrescription;
    private JCheckBox chkFreeIssue;
    private ButtonGroup discountGroup;
    private JRadioButton rbNoDiscount;
    private JRadioButton rbOver1000;
    private JRadioButton rbSenior;
    private JRadioButton rbRegular;
    private JRadioButton rbBulk;

    // Totals & Checkout
    private JLabel lblSubtotal;
    private JLabel lblDiscount;
    private JLabel lblNetTotal;
    private JButton btnCompleteBill;

    public BillingPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(15, 15));
        setBackground(Theme.MAIN_BG);
        setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // 1. Top Header
        JPanel headerPanel = buildHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // 2. Main Content Split: Left (Search, Batch Info, Cart Table) & Right (Discount, Rules, Totals, Checkout)
        JPanel mainSplit = new JPanel(new BorderLayout(15, 15));
        mainSplit.setBackground(Theme.MAIN_BG);

        JPanel leftPanel = buildLeftPanel();
        JPanel rightPanel = buildRightPanel();

        mainSplit.add(leftPanel, BorderLayout.CENTER);
        mainSplit.add(rightPanel, BorderLayout.EAST);

        add(mainSplit, BorderLayout.CENTER);
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

        JLabel lblTitle = UIHelper.createPageTitle("Billing & Point of Sale (POS)");
        lblTitle.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lblSub = new JLabel("");
        lblSub.setFont(Theme.BODY_FONT);
        lblSub.setForeground(Theme.TEXT_MUTED);
        lblSub.setAlignmentX(LEFT_ALIGNMENT);

        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);
        header.add(titleBox, BorderLayout.WEST);

        // Right side: Active cashier info
        StaffMember user = UserSession.getCurrentUser();
        String cashierName = (user != null && user.getStaffName() != null) ? user.getStaffName() : "System Cashier";
        String role = (user != null && user.getRole() != null) ? user.getRole().toUpperCase() : "CASHIER";

        JPanel cashierBadge = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        cashierBadge.setBackground(Theme.CARD_BG);
        cashierBadge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));

        JLabel lblCashier = new JLabel("Cashier: " + cashierName + " [" + role + "]");
        lblCashier.setFont(Theme.BOLD_BODY_FONT);
        lblCashier.setForeground(Theme.TEXT_DARK);
        cashierBadge.add(lblCashier);

        header.add(cashierBadge, BorderLayout.EAST);
        return header;
    }

    // =========================================================================
    // Left Panel: Search + FEFO Telemetry + Cart Table
    // =========================================================================

    private JPanel buildLeftPanel() {
        JPanel left = new JPanel(new BorderLayout(0, 15));
        left.setBackground(Theme.MAIN_BG);

        // Card 1: Medicine Search & FEFO Batch Info
        JPanel searchCard = buildMedicineSelectionCard();
        left.add(searchCard, BorderLayout.NORTH);

        // Card 2: Cart Items Table
        JPanel cartCard = buildCartTableCard();
        left.add(cartCard, BorderLayout.CENTER);

        return left;
    }

    private JPanel buildMedicineSelectionCard() {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        UIHelper.styleCardPanel(card);

        JLabel lblCardTitle = new JLabel("1. Select Medicine (FEFO Smart Batch Allocation)");
        lblCardTitle.setFont(Theme.SUBTITLE_FONT);
        lblCardTitle.setForeground(Theme.TEXT_DARK);
        card.add(lblCardTitle, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBackground(Theme.CARD_BG);

        // Search Bar with Auto-Suggest
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setBackground(Theme.CARD_BG);

        JLabel lblSearch = new JLabel("Search Medicine:");
        lblSearch.setFont(Theme.BOLD_BODY_FONT);
        lblSearch.setForeground(Theme.TEXT_DARK);
        searchRow.add(lblSearch, BorderLayout.WEST);

        txtSearch = new JTextField();
        UIHelper.styleTextField(txtSearch);
        txtSearch.setPreferredSize(new Dimension(300, 36));
        setupAutoSuggest();
        searchRow.add(txtSearch, BorderLayout.CENTER);

        content.add(searchRow, BorderLayout.NORTH);

        // FEFO Telemetry Display Grid
        JPanel telemetryGrid = new JPanel(new GridLayout(2, 3, 10, 8));
        telemetryGrid.setBackground(Theme.CARD_BG);
        telemetryGrid.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        lblBatchNo = createTelemetryValue("-");
        lblStock = createTelemetryValue("-");
        lblUnitPrice = createTelemetryValue("LKR 0.00");
        lblExpiryDate = createTelemetryValue("-");
        lblRxStatus = createTelemetryValue("-");

        telemetryGrid.add(createTelemetryBox("Earliest Batch # (FEFO):", lblBatchNo));
        telemetryGrid.add(createTelemetryBox("Available Stock:", lblStock));
        telemetryGrid.add(createTelemetryBox("Unit Price:", lblUnitPrice));
        telemetryGrid.add(createTelemetryBox("Expiry Date:", lblExpiryDate));
        telemetryGrid.add(createTelemetryBox("Prescription Status:", lblRxStatus));

        content.add(telemetryGrid, BorderLayout.CENTER);

        // Quantity Input & Add to Cart Action
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        addRow.setBackground(Theme.CARD_BG);

        JLabel lblQty = new JLabel("Quantity to Dispense:");
        lblQty.setFont(Theme.BOLD_BODY_FONT);
        lblQty.setForeground(Theme.TEXT_DARK);
        addRow.add(lblQty);

        txtQuantity = new JTextField("1", 6);
        UIHelper.styleTextField(txtQuantity);
        txtQuantity.setPreferredSize(new Dimension(80, 36));
        txtQuantity.addActionListener(e -> addItemToCart());
        addRow.add(txtQuantity);

        btnAddToCart = new JButton("Add to Cart");
        UIHelper.stylePrimaryButton(btnAddToCart);
        btnAddToCart.addActionListener(e -> addItemToCart());
        addRow.add(btnAddToCart);

        content.add(addRow, BorderLayout.SOUTH);

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel createTelemetryBox(String labelText, JLabel valueLabel) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(Theme.CARD_BG);

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(Theme.TEXT_MUTED);

        box.add(lbl);
        box.add(Box.createVerticalStrut(2));
        box.add(valueLabel);
        return box;
    }

    private JLabel createTelemetryValue(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(Theme.BOLD_BODY_FONT);
        lbl.setForeground(Theme.TEXT_DARK);
        return lbl;
    }

    // =========================================================================
    // Auto-Suggest Dropdown Setup
    // =========================================================================

    private void setupAutoSuggest() {
        suggestionPopup = new JPopupMenu();
        suggestionPopup.setFocusable(false);

        suggestionModel = new DefaultListModel<>();
        suggestionList = new JList<>(suggestionModel);
        suggestionList.setFont(Theme.BODY_FONT);
        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setCursor(new Cursor(Cursor.HAND_CURSOR));
        suggestionList.setBackground(Color.WHITE);
        suggestionList.setSelectionBackground(Theme.SIDEBAR_ACTIVE);
        suggestionList.setSelectionForeground(Color.WHITE);

        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) {
                    String selected = suggestionList.getSelectedValue();
                    if (selected != null) {
                        applySelectedMedicine(selected);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(suggestionList);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1));
        scrollPane.setPreferredSize(new Dimension(320, 140));
        suggestionPopup.add(scrollPane);

        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { triggerSearch(); }
            @Override
            public void removeUpdate(DocumentEvent e) { triggerSearch(); }
            @Override
            public void changedUpdate(DocumentEvent e) { triggerSearch(); }
        });

        txtSearch.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && suggestionPopup.isVisible()) {
                    int next = Math.min(suggestionList.getSelectedIndex() + 1, suggestionModel.size() - 1);
                    suggestionList.setSelectedIndex(next);
                    suggestionList.ensureIndexIsVisible(next);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_UP && suggestionPopup.isVisible()) {
                    int prev = Math.max(suggestionList.getSelectedIndex() - 1, 0);
                    suggestionList.setSelectedIndex(prev);
                    suggestionList.ensureIndexIsVisible(prev);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (suggestionPopup.isVisible() && suggestionList.getSelectedValue() != null) {
                        applySelectedMedicine(suggestionList.getSelectedValue());
                        e.consume();
                    } else {
                        String query = txtSearch.getText().trim();
                        if (!query.isEmpty()) {
                            applySelectedMedicine(query);
                        }
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    suggestionPopup.setVisible(false);
                }
            }
        });
    }

    private void triggerSearch() {
        if (isSelectingSuggestion) return;

        String query = txtSearch.getText().trim();
        if (query.length() < 2) {
            suggestionPopup.setVisible(false);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                List<String> results = inventoryDAO.searchMedicineNamesAutoSuggest(query);
                if (results != null && !results.isEmpty()) {
                    suggestionModel.clear();
                    for (String name : results) {
                        suggestionModel.addElement(name);
                    }
                    suggestionList.setSelectedIndex(0);
                    if (!suggestionPopup.isVisible() && txtSearch.isShowing()) {
                        suggestionPopup.show(txtSearch, 0, txtSearch.getHeight());
                    } else {
                        suggestionPopup.revalidate();
                        suggestionPopup.repaint();
                    }
                    txtSearch.requestFocusInWindow();
                } else {
                    suggestionPopup.setVisible(false);
                }
            } catch (SQLException ex) {
                suggestionPopup.setVisible(false);
            }
        });
    }

    private void applySelectedMedicine(String medicineName) {
        if (medicineName == null || medicineName.trim().isEmpty()) return;

        String cleanName = medicineName.trim();
        isSelectingSuggestion = true;
        txtSearch.setText(cleanName);
        suggestionPopup.setVisible(false);
        isSelectingSuggestion = false;

        try {
            List<Medicine> batches = inventoryDAO.getBatchesByMedicineNameFEFO(cleanName);
            if (batches != null && !batches.isEmpty()) {
                currentSelectedMedicineName = cleanName;
                currentMedicineBatches = batches;

                int totalStock = 0;
                for (Medicine m : batches) {
                    totalStock += m.getStockQuantity();
                }
                currentTotalDbStock = totalStock;

                refreshSelectedMedicineTelemetry();

                txtQuantity.requestFocusInWindow();
                txtQuantity.selectAll();
            } else {
                clearMedicineDetails("No active stock available for this medicine.");
            }
        } catch (SQLException ex) {
            clearMedicineDetails("Error loading batch details: " + ex.getMessage());
        }
    }

    private int getCartQuantityForMedicine(String medicineName) {
        if (medicineName == null) return 0;
        int qtyInCart = 0;
        for (CartEntry entry : cartItems) {
            if (entry.getMedicineName().equalsIgnoreCase(medicineName.trim())) {
                qtyInCart += entry.getQuantity();
            }
        }
        return qtyInCart;
    }

    /**
     * Updates displayed Batch, Available Stock, Unit Price, and Expiry Date
     * taking into account quantities already placed in the current cart (FEFO).
     */
    private void refreshSelectedMedicineTelemetry() {
        if (currentMedicineBatches == null || currentMedicineBatches.isEmpty() || currentSelectedMedicineName == null) {
            return;
        }

        int alreadyInCart = getCartQuantityForMedicine(currentSelectedMedicineName);
        int remainingAvailable = Math.max(0, currentTotalDbStock - alreadyInCart);
        currentAvailableStock = remainingAvailable;

        // Find the next active batch after accounting for alreadyInCart
        int remainingToSkip = alreadyInCart;
        Medicine activeBatch = null;
        int remainingInActiveBatch = 0;

        for (Medicine batch : currentMedicineBatches) {
            int batchStock = batch.getStockQuantity();
            if (remainingToSkip >= batchStock) {
                remainingToSkip -= batchStock;
            } else {
                activeBatch = batch;
                remainingInActiveBatch = batchStock - remainingToSkip;
                break;
            }
        }

        if (activeBatch == null) {
            activeBatch = currentMedicineBatches.get(0);
            remainingInActiveBatch = 0;
        }

        currentSelectedMedicine = activeBatch;

        lblBatchNo.setText(activeBatch.getBatchNumber());
        if (alreadyInCart > 0) {
            lblStock.setText(remainingAvailable + " units avail (" + remainingInActiveBatch + " in batch, " + alreadyInCart + " in cart)");
        } else {
            lblStock.setText(remainingAvailable + " units (" + remainingInActiveBatch + " in batch)");
        }
        lblUnitPrice.setText(String.format("LKR %.2f", activeBatch.getUnitPrice()));
        lblExpiryDate.setText(activeBatch.getExpDate() != null ? activeBatch.getExpDate().toString() : "N/A");

        if (activeBatch.isRequiresPrescription()) {
            lblRxStatus.setText("Yes (Prescription Required)");
            lblRxStatus.setForeground(Theme.DANGER_BTN);
        } else {
            lblRxStatus.setText("No (OTC Medicine)");
            lblRxStatus.setForeground(new Color(22, 101, 52));
        }
    }

    private void clearMedicineDetails(String statusMessage) {
        currentSelectedMedicine = null;
        currentSelectedMedicineName = null;
        currentMedicineBatches = new ArrayList<>();
        currentTotalDbStock = 0;
        currentAvailableStock = 0;
        lblBatchNo.setText("-");
        lblStock.setText(statusMessage);
        lblUnitPrice.setText("LKR 0.00");
        lblExpiryDate.setText("-");
        lblRxStatus.setText("-");
        lblRxStatus.setForeground(Theme.TEXT_DARK);
    }

    // =========================================================================
    // Card 2: Cart Items Table
    // =========================================================================

    private JPanel buildCartTableCard() {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        UIHelper.styleCardPanel(card);

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setBackground(Theme.CARD_BG);

        JLabel lblTitle = new JLabel("2. Current Bill Cart");
        lblTitle.setFont(Theme.SUBTITLE_FONT);
        lblTitle.setForeground(Theme.TEXT_DARK);
        headerRow.add(lblTitle, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setBackground(Theme.CARD_BG);

        btnRemoveItem = new JButton("Remove Item");
        UIHelper.styleDangerButton(btnRemoveItem);
        btnRemoveItem.addActionListener(e -> removeSelectedCartItem());
        actions.add(btnRemoveItem);

        btnClearCart = new JButton("Clear Cart");
        UIHelper.styleSecondaryButton(btnClearCart);
        btnClearCart.addActionListener(e -> clearCart());
        actions.add(btnClearCart);

        headerRow.add(actions, BorderLayout.EAST);
        card.add(headerRow, BorderLayout.NORTH);

        String[] cols = {"Medicine Name", "Batch No", "Unit Price (LKR)", "Quantity", "Line Total (LKR)"};
        cartTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        tblCart = new JTable(cartTableModel);
        UIHelper.styleTable(tblCart);
        tblCart.getColumnModel().getColumn(0).setPreferredWidth(180);
        tblCart.getColumnModel().getColumn(1).setPreferredWidth(90);
        tblCart.getColumnModel().getColumn(2).setPreferredWidth(100);
        tblCart.getColumnModel().getColumn(3).setPreferredWidth(70);
        tblCart.getColumnModel().getColumn(4).setPreferredWidth(110);

        JScrollPane scrollPane = new JScrollPane(tblCart);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        card.add(scrollPane, BorderLayout.CENTER);

        return card;
    }

    private void addItemToCart() {
        if (currentSelectedMedicine == null || currentMedicineBatches == null || currentMedicineBatches.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an available medicine first.", "No Medicine Selected", JOptionPane.WARNING_MESSAGE);
            txtSearch.requestFocusInWindow();
            return;
        }

        int qty;
        try {
            qty = Integer.parseInt(txtQuantity.getText().trim());
            if (qty <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Quantity must be a positive whole number greater than 0.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtQuantity.requestFocusInWindow();
            return;
        }

        int alreadyInCart = getCartQuantityForMedicine(currentSelectedMedicineName);
        int realTimeAvailable = Math.max(0, currentTotalDbStock - alreadyInCart);

        if (qty > realTimeAvailable) {
            JOptionPane.showMessageDialog(
                this,
                "Requested quantity (" + qty + ") exceeds available stock (" + realTimeAvailable + " remaining; " + alreadyInCart + " already in cart).",
                "Insufficient Stock",
                JOptionPane.WARNING_MESSAGE
            );
            txtQuantity.requestFocusInWindow();
            return;
        }

        // Allocate requested quantity across FEFO batches, skipping units already in cart
        int skipRemaining = alreadyInCart;
        int qtyToAllocate = qty;

        for (Medicine batch : currentMedicineBatches) {
            if (qtyToAllocate <= 0) break;

            int batchStock = batch.getStockQuantity();
            if (skipRemaining >= batchStock) {
                skipRemaining -= batchStock;
                continue;
            }

            int usableInBatch = batchStock - skipRemaining;
            skipRemaining = 0;

            int takeFromBatch = Math.min(usableInBatch, qtyToAllocate);
            if (takeFromBatch <= 0) continue;

            addOrIncrementCartEntry(batch, takeFromBatch);
            qtyToAllocate -= takeFromBatch;
        }

        // Refresh telemetry immediately so Available Stock reflects newly added cart items
        refreshSelectedMedicineTelemetry();
        txtQuantity.setText("1");
        txtQuantity.requestFocusInWindow();
        txtQuantity.selectAll();

        calculateTotals();
    }

    private void addOrIncrementCartEntry(Medicine batch, int qtyToAdd) {
        for (int i = 0; i < cartItems.size(); i++) {
            CartEntry entry = cartItems.get(i);
            if (entry.getMedicineId() == batch.getMedicineId()
                    && entry.getBatchNumber().equalsIgnoreCase(batch.getBatchNumber())) {
                entry.setQuantity(entry.getQuantity() + qtyToAdd);
                cartTableModel.setValueAt(entry.getQuantity(), i, 3);
                cartTableModel.setValueAt(String.format("%.2f", entry.getLineTotal()), i, 4);
                return;
            }
        }

        CartEntry newEntry = new CartEntry(
            batch.getMedicineId(),
            batch.getMedicineName(),
            batch.getBatchNumber(),
            batch.getUnitPrice(),
            qtyToAdd,
            batch.isRequiresPrescription()
        );
        cartItems.add(newEntry);
        cartTableModel.addRow(new Object[]{
            newEntry.getMedicineName(),
            newEntry.getBatchNumber(),
            String.format("%.2f", newEntry.getUnitPrice()),
            newEntry.getQuantity(),
            String.format("%.2f", newEntry.getLineTotal())
        });
    }

    private void removeSelectedCartItem() {
        int selectedRow = tblCart.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select an item from the cart to remove.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        cartItems.remove(selectedRow);
        cartTableModel.removeRow(selectedRow);
        refreshSelectedMedicineTelemetry();
        calculateTotals();
    }

    private void clearCart() {
        cartItems.clear();
        cartTableModel.setRowCount(0);
        refreshSelectedMedicineTelemetry();
        calculateTotals();
    }

    // =========================================================================
    // Right Panel: Rules, Discount Options, Totals, and Checkout
    // =========================================================================

    private JPanel buildRightPanel() {
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setPreferredSize(new Dimension(360, 0));
        right.setBackground(Theme.MAIN_BG);

        // Container Card
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        UIHelper.styleCardPanel(card);

        // Section Title
        JLabel lblSecTitle = new JLabel("3. Billing & Checkout Options");
        lblSecTitle.setFont(Theme.SUBTITLE_FONT);
        lblSecTitle.setForeground(Theme.TEXT_DARK);
        lblSecTitle.setAlignmentX(LEFT_ALIGNMENT);
        card.add(lblSecTitle);
        card.add(Box.createVerticalStrut(12));

        // 3.1 Prescription Verification Rule
        JPanel rxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        rxPanel.setBackground(Theme.CARD_BG);
        rxPanel.setAlignmentX(LEFT_ALIGNMENT);

        chkPrescription = new JCheckBox("Prescription Checked");
        chkPrescription.setFont(Theme.BOLD_BODY_FONT);
        chkPrescription.setBackground(Theme.CARD_BG);
        rxPanel.add(chkPrescription);
        card.add(rxPanel);

        JLabel lblRxHint = new JLabel("Enforced for Cashiers if any cart item requires Rx.");
        lblRxHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblRxHint.setForeground(Theme.TEXT_MUTED);
        lblRxHint.setAlignmentX(LEFT_ALIGNMENT);
        card.add(lblRxHint);
        card.add(Box.createVerticalStrut(14));

        card.add(createDivider());
        card.add(Box.createVerticalStrut(10));

        // 3.2 5% Discount (Single Selection Only via ButtonGroup)
        JLabel lblDiscountTitle = new JLabel("Discount Selection (5% Off):");
        lblDiscountTitle.setFont(Theme.BOLD_BODY_FONT);
        lblDiscountTitle.setForeground(Theme.TEXT_DARK);
        lblDiscountTitle.setAlignmentX(LEFT_ALIGNMENT);
        card.add(lblDiscountTitle);
        card.add(Box.createVerticalStrut(6));

        discountGroup = new ButtonGroup();
        rbNoDiscount = createDiscountRadio("No Discount", true);
        rbOver1000 = createDiscountRadio("Bill > Rs. 1000", false);
        rbSenior = createDiscountRadio("Senior Citizen (Age > 55)", false);
        rbRegular = createDiscountRadio("Regular Customer", false);
        rbBulk = createDiscountRadio("Bulk Order", false);

        discountGroup.add(rbNoDiscount);
        discountGroup.add(rbOver1000);
        discountGroup.add(rbSenior);
        discountGroup.add(rbRegular);
        discountGroup.add(rbBulk);

        card.add(rbNoDiscount);
        card.add(rbOver1000);
        card.add(rbSenior);
        card.add(rbRegular);
        card.add(rbBulk);
        card.add(Box.createVerticalStrut(14));

        card.add(createDivider());
        card.add(Box.createVerticalStrut(10));

        // 3.3 Dual-Path Billing (Free Issue)
        JPanel freeIssuePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        freeIssuePanel.setBackground(Theme.CARD_BG);
        freeIssuePanel.setAlignmentX(LEFT_ALIGNMENT);

        chkFreeIssue = new JCheckBox("Free Issue (Owner Auth)");
        chkFreeIssue.setFont(Theme.BOLD_BODY_FONT);
        chkFreeIssue.setBackground(Theme.CARD_BG);
        chkFreeIssue.addItemListener(e -> calculateTotals());
        freeIssuePanel.add(chkFreeIssue);
        card.add(freeIssuePanel);

        JLabel lblFreeHint = new JLabel("Sets Net Total to LKR 0.00 (Owner authority required).");
        lblFreeHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblFreeHint.setForeground(Theme.TEXT_MUTED);
        lblFreeHint.setAlignmentX(LEFT_ALIGNMENT);
        card.add(lblFreeHint);
        card.add(Box.createVerticalStrut(14));

        card.add(createDivider());
        card.add(Box.createVerticalStrut(10));

        // 3.4 Financial Summary
        lblSubtotal = new JLabel("Subtotal: LKR 0.00");
        lblSubtotal.setFont(Theme.BODY_FONT);
        lblSubtotal.setForeground(Theme.TEXT_DARK);
        lblSubtotal.setAlignmentX(LEFT_ALIGNMENT);
        card.add(lblSubtotal);
        card.add(Box.createVerticalStrut(4));

        lblDiscount = new JLabel("Discount: LKR 0.00");
        lblDiscount.setFont(Theme.BODY_FONT);
        lblDiscount.setForeground(new Color(22, 101, 52));
        lblDiscount.setAlignmentX(LEFT_ALIGNMENT);
        card.add(lblDiscount);
        card.add(Box.createVerticalStrut(8));

        lblNetTotal = new JLabel("Net Total: LKR 0.00");
        lblNetTotal.setFont(Theme.STAT_NUMBER_FONT);
        lblNetTotal.setForeground(Theme.TEXT_DARK);
        lblNetTotal.setAlignmentX(LEFT_ALIGNMENT);
        card.add(lblNetTotal);
        card.add(Box.createVerticalStrut(16));

        // 3.5 Checkout Button
        btnCompleteBill = new JButton("Complete & Print Bill");
        UIHelper.stylePrimaryButton(btnCompleteBill);
        btnCompleteBill.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btnCompleteBill.setAlignmentX(LEFT_ALIGNMENT);
        btnCompleteBill.addActionListener(e -> processCheckout());
        card.add(btnCompleteBill);

        right.add(card);
        return right;
    }

    private JRadioButton createDiscountRadio(String text, boolean selected) {
        JRadioButton rb = new JRadioButton(text, selected);
        rb.setFont(Theme.BODY_FONT);
        rb.setBackground(Theme.CARD_BG);
        rb.setCursor(new Cursor(Cursor.HAND_CURSOR));
        rb.setAlignmentX(LEFT_ALIGNMENT);
        rb.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                calculateTotals();
            }
        });
        return rb;
    }

    private JSeparator createDivider() {
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(Theme.BORDER_COLOR);
        sep.setAlignmentX(LEFT_ALIGNMENT);
        return sep;
    }

    // =========================================================================
    // Calculations & Totals
    // =========================================================================

    private void calculateTotals() {
        double subtotal = 0.0;
        for (CartEntry entry : cartItems) {
            subtotal += entry.getLineTotal();
        }

        double discount = 0.0;

        if (chkFreeIssue.isSelected()) {
            discount = subtotal;
            lblSubtotal.setText(String.format("Subtotal: LKR %,.2f", subtotal));
            lblDiscount.setText(String.format("Discount (100%%): LKR %,.2f", discount));
            lblNetTotal.setText("Net Total: LKR 0.00");
            return;
        }

        String discountReason = getSelectedDiscountReason();
        if (!"No Discount".equalsIgnoreCase(discountReason) && !"None".equalsIgnoreCase(discountReason)) {
            // Apply 5% discount
            discount = Math.round((subtotal * 0.05) * 100.0) / 100.0;
        }

        double netTotal = Math.max(0.0, subtotal - discount);

        lblSubtotal.setText(String.format("Subtotal: LKR %,.2f", subtotal));
        lblDiscount.setText(String.format("Discount (5%%): LKR %,.2f", discount));
        lblNetTotal.setText(String.format("Net Total: LKR %,.2f", netTotal));
    }

    private String getSelectedDiscountReason() {
        if (rbOver1000.isSelected()) return "Bill > Rs. 1000";
        if (rbSenior.isSelected()) return "Senior Citizen (Age > 55)";
        if (rbRegular.isSelected()) return "Regular Customer";
        if (rbBulk.isSelected()) return "Bulk Order";
        return "None";
    }

    // =========================================================================
    // Checkout, File Handling & Virtual Thermal Receipt
    // =========================================================================

    private void processCheckout() {
        // 1. Validate cart has items
        if (cartItems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "The cart is empty. Please add medicines before checkout.", "Empty Cart", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Owner who approved an Owner-only part of this sale (temporary pop-up login)
        StaffMember authOwner = null;

        // 2. Prescription Verification Rule
        boolean requiresRx = false;
        String rxMedicineName = "";
        for (CartEntry item : cartItems) {
            if (item.isRequiresPrescription()) {
                requiresRx = true;
                rxMedicineName = item.getMedicineName();
                break;
            }
        }

        if (requiresRx && !chkPrescription.isSelected()) {
            if (!UserSession.isOwner()) {
                // Cashier: an Owner may approve this sale by signing in temporarily
                authOwner = OwnerAccess.requestOwner(this,
                    "Medicine [" + rxMedicineName + "] requires a prescription, but 'Prescription Checked' is not ticked. "
                    + "Tick it, or have an Owner authorize this sale.");
                if (authOwner == null) {
                    chkPrescription.requestFocusInWindow();
                    return;
                }
            } else {
                int overrideChoice = JOptionPane.showConfirmDialog(
                    this,
                    "Medicine [" + rxMedicineName + "] requires a prescription, but 'Prescription Checked' is unchecked.\n" +
                    "Do you wish to authorize this sale as an Owner override?",
                    "Owner Prescription Override",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                if (overrideChoice != JOptionPane.YES_OPTION) {
                    return;
                }
            }
        }

        // 3. Construct Bill
        StaffMember user = UserSession.getCurrentUser();
        String cashierId = (user != null && user.getStaffId() != null) ? user.getStaffId() : "C001";

        double subtotal = 0.0;
        for (CartEntry entry : cartItems) {
            subtotal += entry.getLineTotal();
        }

        boolean isFree = chkFreeIssue.isSelected();
        if (isFree && !UserSession.isOwner() && authOwner == null) {
            authOwner = OwnerAccess.requestOwner(this, "Free Issue bills are an Owner-only function.");
            if (authOwner == null) {
                return;
            }
        }
        final StaffMember approvingOwner = authOwner;
        double discountAmount = 0.0;
        String discountReason;

        if (isFree) {
            discountAmount = subtotal;
            discountReason = "Free Issue (Owner Authorized)";
        } else {
            discountReason = getSelectedDiscountReason();
            if (!"None".equalsIgnoreCase(discountReason) && !"No Discount".equalsIgnoreCase(discountReason)) {
                discountAmount = Math.round((subtotal * 0.05) * 100.0) / 100.0;
            }
        }

        double netTotal = isFree ? 0.0 : Math.max(0.0, subtotal - discountAmount);

        Bill bill = new Bill(
            cashierId,
            subtotal,
            discountAmount,
            discountReason,
            netTotal,
            chkPrescription.isSelected(),
            isFree,
            new Timestamp(System.currentTimeMillis())
        );

        List<BillItem> billItemList = new ArrayList<>();
        for (CartEntry entry : cartItems) {
            billItemList.add(new BillItem(
                entry.getMedicineId(),
                entry.getMedicineName(),
                entry.getUnitPrice(),
                entry.getQuantity()
            ));
        }
        bill.setItems(billItemList);

        // 4. Call BillingDAO.createBill(bill)
        try {
            int billId = (approvingOwner != null)
                    ? OwnerAccess.callAs(approvingOwner, () -> billingDAO.createBill(bill))
                    : billingDAO.createBill(bill);
            bill.setBillId(billId);

            // 5. Save receipt file & read back
            String filePath = ReceiptWriter.saveReceiptToFile(bill);
            String receiptText = ReceiptWriter.readReceiptFromFile(filePath);

            // 6. Display Virtual Thermal Receipt
            UIHelper.showVirtualThermalReceipt(this, receiptText, billId);

            // 7. Reset cart and form for next customer
            resetBillingForm();

        } catch (UnauthorizedAccessException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Unauthorized Access", JOptionPane.ERROR_MESSAGE);
        } catch (OutOfStockException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Out of Stock", JOptionPane.WARNING_MESSAGE);
        } catch (ExpiredMedicineException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Expired Medicine", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error creating bill: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "An error occurred while creating the bill: " + ex.getMessage(), "Billing Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void resetBillingForm() {
        cartItems.clear();
        cartTableModel.setRowCount(0);
        txtSearch.setText("");
        clearMedicineDetails("-");
        txtQuantity.setText("1");
        chkPrescription.setSelected(false);
        chkFreeIssue.setSelected(false);
        rbNoDiscount.setSelected(true);
        calculateTotals();
    }

    /**
     * Interface compatibility method for DashboardFrame navigation refresh.
     */
    public void loadAvailableMedicines() {
        if (currentSelectedMedicineName != null && !currentSelectedMedicineName.trim().isEmpty()) {
            applySelectedMedicine(currentSelectedMedicineName);
        } else {
            clearMedicineDetails("-");
        }
    }

    // =========================================================================
    // Internal Cart Entry Model
    // =========================================================================

    private static class CartEntry {
        private final int medicineId;
        private final String medicineName;
        private final String batchNumber;
        private final double unitPrice;
        private int quantity;
        private double lineTotal;
        private final boolean requiresPrescription;

        public CartEntry(int medicineId, String medicineName, String batchNumber, double unitPrice, int quantity, boolean requiresPrescription) {
            this.medicineId = medicineId;
            this.medicineName = medicineName;
            this.batchNumber = batchNumber;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
            this.lineTotal = unitPrice * quantity;
            this.requiresPrescription = requiresPrescription;
        }

        public int getMedicineId() { return medicineId; }
        public String getMedicineName() { return medicineName; }
        public String getBatchNumber() { return batchNumber; }
        public double getUnitPrice() { return unitPrice; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) {
            this.quantity = quantity;
            this.lineTotal = this.unitPrice * this.quantity;
        }
        public double getLineTotal() { return lineTotal; }
        public boolean isRequiresPrescription() { return requiresPrescription; }
    }
}
