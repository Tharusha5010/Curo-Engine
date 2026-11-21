package inventory;

import auth.UserSession;
import exceptions.UnauthorizedAccessException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import models.Medicine;
import models.StaffMember;
import ui.OwnerAccess;
import ui.SuggestBox;
import ui.Theme;
import ui.UIHelper;

/**
 * InventoryPanel provides full inventory browsing, batch search, manual moving type toggles,
 * and new batch restocking with smart auto-suggest (Owner only).
 */
public class InventoryPanel extends JPanel {

    // Backend Access
    private final InventoryDAO inventoryDAO = new InventoryDAO();

    // 1. Top Search & Filter Components
    private JTextField txtSearch;
    private JButton btnSearch;
    private JButton btnRefresh;
    private JButton btnToggleMovingType;
    private JButton btnUpdateStock;
    private JButton btnDeleteBatch;


    // 2. Inventory Stock Table Components
    private JTable tblInventory;
    private DefaultTableModel tableModel;

    // 3. Add Batch / Restock Form Components (Owner Only)
    private JTextField txtMedicineName;
    private JTextField txtCompanyName;
    private JTextField txtBatchNumber;
    private JTextField txtUnitPrice;
    private JTextField txtStockQuantity;
    private JTextField txtStockDate;
    private JTextField txtExpDate;
    private JCheckBox chkRequiresPrescription;
    private JComboBox<String> cmbMovingType;
    private JButton btnAddBatch;
    private JButton btnClearForm;

    // Auto-suggest drop-downs (after 2 letters) and auto-fill of the Add Batch form
    private SuggestBox<SuggestionLogic.Hit> nameSuggest;
    private SuggestBox<SuggestionLogic.Hit> batchSuggest;
    private SuggestBox<SuggestionLogic.Hit> searchSuggest;
    private String lastFilledName = null;

    public InventoryPanel() {
        initComponents();
        loadInventoryData();
    }

    private void initComponents() {
        setLayout(new BorderLayout(15, 15));
        setBackground(Theme.MAIN_BG);
        setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // 1. Top Section: Header + Search & Filter Bar + Actions
        JPanel topSection = buildTopSection();
        add(topSection, BorderLayout.NORTH);

        // Center Area: Inventory table (top) and Add Batch form (bottom).
        // The divider can be dragged so either part can be made bigger.
        JPanel tableCard = buildTableCard();
        JPanel addBatchCard = buildAddBatchCard();

        JScrollPane formScroll = new JScrollPane(addBatchCard,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.getViewport().setBackground(Theme.MAIN_BG);
        formScroll.getVerticalScrollBar().setUnitIncrement(20);
        formScroll.setMinimumSize(new Dimension(200, 120));

        tableCard.setMinimumSize(new Dimension(200, 150));

        JSplitPane centerArea = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableCard, formScroll);
        centerArea.setContinuousLayout(true);
        centerArea.setBorder(null);
        centerArea.setOpaque(false);
        centerArea.setDividerSize(8);
        centerArea.setResizeWeight(0.5);
        // Put the divider in the middle once the panel has its real size
        addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean placed = false;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!placed && centerArea.getHeight() > 100) {
                    placed = true;
                    centerArea.setDividerLocation(0.5);
                }
            }
        });

        add(centerArea, BorderLayout.CENTER);
    }

    // =========================================================================
    // 1. Top Header, Search & Action Bar
    // =========================================================================

    private JPanel buildTopSection() {
        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setBackground(Theme.MAIN_BG);

        // Page Title & Subtitle
        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setBackground(Theme.MAIN_BG);

        JLabel lblTitle = UIHelper.createPageTitle("Inventory Management & Stock Telemetry");
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);


        JLabel lblSub = new JLabel("");
        lblSub.setFont(Theme.BODY_FONT);
        lblSub.setForeground(Theme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);
        container.add(titleBox, BorderLayout.NORTH);

        // Search & Filter Bar + Toggle Moving Type Button
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionRow.setBackground(Theme.MAIN_BG);

        JLabel lblSearch = new JLabel("Search Name / Batch:");
        lblSearch.setFont(Theme.BODY_FONT);
        lblSearch.setForeground(Theme.TEXT_DARK);
        actionRow.add(lblSearch);

        txtSearch = new JTextField();
        UIHelper.styleTextField(txtSearch);
        txtSearch.setPreferredSize(new Dimension(230, 38));
        txtSearch.addActionListener(e -> searchInventory());
        setupSearchSuggest();
        actionRow.add(txtSearch);

        btnSearch = new JButton("Search");
        UIHelper.stylePrimaryButton(btnSearch);
        btnSearch.addActionListener(e -> searchInventory());
        actionRow.add(btnSearch);

        btnRefresh = new JButton("Refresh");
        UIHelper.styleSecondaryButton(btnRefresh);
        btnRefresh.addActionListener(e -> {
            txtSearch.setText("");
            loadInventoryData();
        });
        actionRow.add(btnRefresh);

        // 4. Manual Moving Type Toggle (Owner Only)
        btnToggleMovingType = new JButton("Toggle FAST / SLOW");
        UIHelper.styleSecondaryButton(btnToggleMovingType);
        btnToggleMovingType.addActionListener(e -> toggleMovingType());
        actionRow.add(btnToggleMovingType);

        // Owner: set the exact stock quantity of ONE selected batch
        btnUpdateStock = new JButton("Update Stock Qty");
        UIHelper.styleSecondaryButton(btnUpdateStock);
        btnUpdateStock.addActionListener(e -> updateSelectedBatchStock());
        actionRow.add(btnUpdateStock);

        // Owner: delete a selected batch (if not linked to past bills)
        btnDeleteBatch = new JButton("Delete Batch");
        UIHelper.styleDangerButton(btnDeleteBatch);
        btnDeleteBatch.addActionListener(e -> deleteSelectedBatch());
        actionRow.add(btnDeleteBatch);

        container.add(actionRow, BorderLayout.SOUTH);
        return container;
    }

    // =========================================================================
    // 2. Inventory Stock Table Card (UIHelper.styleTable)
    // =========================================================================

    private JPanel buildTableCard() {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        UIHelper.styleCardPanel(card);

        // Card Header
        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setBackground(Theme.CARD_BG);

        JLabel lblHeader = new JLabel("Current Inventory Batches (FEFO Timeline)");
        lblHeader.setFont(Theme.SUBTITLE_FONT);
        lblHeader.setForeground(Theme.TEXT_DARK);
        cardHeader.add(lblHeader, BorderLayout.WEST);

        JLabel lblHint = new JLabel("Select a batch row to toggle moving speed");
        lblHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblHint.setForeground(Theme.TEXT_MUTED);
        cardHeader.add(lblHint, BorderLayout.EAST);

        card.add(cardHeader, BorderLayout.NORTH);

        // Table Columns: ID, Medicine Name, Company, Batch No, Unit Price (LKR), Stock Qty, Stock Date, Expiry Date, Rx Required, and Moving Type (FAST/SLOW)
        String[] columns = {
            "ID", "Medicine Name", "Company", "Batch No", "Unit Price (LKR)",
            "Stock Qty", "Stock Date", "Expiry Date", "Rx Required", "Moving Type (FAST/SLOW)"
        };

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false; // Read-only table cells
            }
        };

        tblInventory = new JTable(tableModel);
        UIHelper.styleTable(tblInventory);
        tblInventory.setFillsViewportHeight(true);
        tblInventory.setPreferredScrollableViewportSize(new Dimension(400, 170));

        // Column Widths
        tblInventory.getColumnModel().getColumn(0).setPreferredWidth(45);
        tblInventory.getColumnModel().getColumn(1).setPreferredWidth(170);
        tblInventory.getColumnModel().getColumn(2).setPreferredWidth(130);
        tblInventory.getColumnModel().getColumn(3).setPreferredWidth(90);
        tblInventory.getColumnModel().getColumn(4).setPreferredWidth(100);
        tblInventory.getColumnModel().getColumn(5).setPreferredWidth(75);
        tblInventory.getColumnModel().getColumn(6).setPreferredWidth(90);
        tblInventory.getColumnModel().getColumn(7).setPreferredWidth(90);
        tblInventory.getColumnModel().getColumn(8).setPreferredWidth(85);
        tblInventory.getColumnModel().getColumn(9).setPreferredWidth(120);

        // Moving Type badge renderer
        tblInventory.getColumnModel().getColumn(9).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                String valStr = (value != null) ? value.toString().toUpperCase() : "";
                if (!isSelected) {
                    if ("FAST".equals(valStr)) {
                        setForeground(new Color(22, 101, 52)); // Green
                        setFont(Theme.BOLD_BODY_FONT);
                    } else {
                        setForeground(new Color(180, 83, 9)); // Amber
                        setFont(Theme.BODY_FONT);
                    }
                }
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(tblInventory);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        card.add(scrollPane, BorderLayout.CENTER);

        return card;
    }

    // =========================================================================
    // 3. Add New Batch / Restock Form Card (Owner Only)
    // =========================================================================

    private JPanel buildAddBatchCard() {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        UIHelper.styleCardPanel(card);

        // Header
        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setBackground(Theme.CARD_BG);

        JLabel lblTitle = new JLabel("Add New Batch / Restock Medicine (Owner Only)");
        lblTitle.setFont(Theme.SUBTITLE_FONT);
        lblTitle.setForeground(Theme.TEXT_DARK);
        cardHeader.add(lblTitle, BorderLayout.WEST);

        JLabel lblOwnerNotice = new JLabel("Requires active OWNER credentials");
        lblOwnerNotice.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblOwnerNotice.setForeground(Theme.TEXT_MUTED);
        cardHeader.add(lblOwnerNotice, BorderLayout.EAST);

        card.add(cardHeader, BorderLayout.NORTH);

        // Input fields (all created enabled and editable)
        txtMedicineName = newFormField("");
        txtCompanyName = newFormField("");
        txtBatchNumber = newFormField("");
        txtUnitPrice = newFormField("");
        txtStockQuantity = newFormField("");
        txtStockDate = newFormField(LocalDate.now().toString());
        txtExpDate = newFormField(LocalDate.now().plusMonths(12).toString());
        setupFormSuggest();

        cmbMovingType = new JComboBox<>(new String[]{"SLOW", "FAST"});
        cmbMovingType.setSelectedItem("SLOW");
        cmbMovingType.setFont(Theme.BODY_FONT);
        cmbMovingType.setBackground(Color.WHITE);
        cmbMovingType.setForeground(Theme.TEXT_DARK);
        cmbMovingType.setMinimumSize(new Dimension(FIELD_MIN_WIDTH, FIELD_HEIGHT));
        cmbMovingType.setPreferredSize(new Dimension(FIELD_PREF_WIDTH, FIELD_HEIGHT));

        chkRequiresPrescription = new JCheckBox("Rx Required (prescription needed)");
        chkRequiresPrescription.setFont(Theme.BOLD_BODY_FONT);
        chkRequiresPrescription.setBackground(Theme.CARD_BG);
        chkRequiresPrescription.setForeground(Theme.TEXT_DARK);

        // Form grid: 4 equal columns that stretch with the window; label above each field
        JPanel formGrid = new JPanel(new GridBagLayout());
        formGrid.setBackground(Theme.CARD_BG);

        addFormCell(formGrid, "Medicine Name *", txtMedicineName, 0, 0);
        addFormCell(formGrid, "Company Name", txtCompanyName, 1, 0);
        addFormCell(formGrid, "Batch No *", txtBatchNumber, 2, 0);
        addFormCell(formGrid, "Unit Price (LKR) *", txtUnitPrice, 3, 0);

        addFormCell(formGrid, "Stock Qty *", txtStockQuantity, 0, 1);
        addFormCell(formGrid, "Stock Date (YYYY-MM-DD)", txtStockDate, 1, 1);
        addFormCell(formGrid, "Expiry Date (YYYY-MM-DD)", txtExpDate, 2, 1);
        addFormCell(formGrid, "Moving Type", cmbMovingType, 3, 1);

        // Row 3: Rx checkbox + buttons
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 2;
        gbc.insets = new Insets(8, 8, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        gbc.gridx = 0;
        gbc.gridwidth = 2;
        formGrid.add(chkRequiresPrescription, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setBackground(Theme.CARD_BG);

        btnClearForm = new JButton("Clear");
        UIHelper.styleSecondaryButton(btnClearForm);
        btnClearForm.setPreferredSize(new Dimension(100, 40));
        btnClearForm.addActionListener(e -> clearAddBatchForm());
        btnPanel.add(btnClearForm);

        btnAddBatch = new JButton("Add Batch / Restock");
        UIHelper.stylePrimaryButton(btnAddBatch);
        btnAddBatch.setPreferredSize(new Dimension(190, 40));
        btnAddBatch.addActionListener(e -> handleAddBatch());
        btnPanel.add(btnAddBatch);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        formGrid.add(btnPanel, gbc);

        card.add(formGrid, BorderLayout.CENTER);
        return card;
    }

    private static final int FIELD_HEIGHT = 40;
    private static final int FIELD_MIN_WIDTH = 150;
    private static final int FIELD_PREF_WIDTH = 190;

    // Large, always-editable text field
    private JTextField newFormField(String initialText) {
        JTextField field = new JTextField(initialText);
        UIHelper.styleTextField(field);
        field.setEditable(true);
        field.setEnabled(true);
        field.setMinimumSize(new Dimension(FIELD_MIN_WIDTH, FIELD_HEIGHT));
        field.setPreferredSize(new Dimension(FIELD_PREF_WIDTH, FIELD_HEIGHT));
        return field;
    }

    // Label on top, input underneath, in one grid cell
    private void addFormCell(JPanel grid, String labelText, JComponent input, int col, int row) {
        JPanel cell = new JPanel(new BorderLayout(0, 4));
        cell.setBackground(Theme.CARD_BG);
        cell.add(createFieldLabel(labelText), BorderLayout.NORTH);
        cell.add(input, BorderLayout.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = col;
        gbc.gridy = row;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 4, 8);
        grid.add(cell, gbc);
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(Theme.BODY_FONT);
        lbl.setForeground(Theme.TEXT_DARK);
        return lbl;
    }

    // =========================================================================
    // Auto-suggest drop-downs (2+ letters) and auto-fill
    // =========================================================================

    // Search box: type 2+ letters of a medicine name OR a batch number
    private void setupSearchSuggest() {
        searchSuggest = new SuggestBox<>(
            txtSearch,
            SuggestionLogic.MIN_CHARS,
            q -> SuggestionLogic.mixedHits(inventoryDAO.searchInventoryByNameOrBatch(q), q),
            SuggestionLogic.Hit::label,
            hit -> {
                searchSuggest.setTextSilently(hit.text);
                searchInventory();
            }
        );
    }

    // Add Batch form: Medicine Name and Batch No both suggest after 2+ letters
    private void setupFormSuggest() {
        nameSuggest = new SuggestBox<>(
            txtMedicineName,
            SuggestionLogic.MIN_CHARS,
            q -> SuggestionLogic.nameHits(inventoryDAO.searchInventoryByNameOrBatch(q), q),
            SuggestionLogic.Hit::label,
            hit -> {
                fillFromMedicine(hit.med);
                txtBatchNumber.requestFocusInWindow();
            }
        );

        batchSuggest = new SuggestBox<>(
            txtBatchNumber,
            SuggestionLogic.MIN_CHARS,
            q -> SuggestionLogic.batchHits(inventoryDAO.searchInventoryByNameOrBatch(q), q),
            SuggestionLogic.Hit::label,
            hit -> {
                fillFromMedicine(hit.med);
                batchSuggest.setTextSilently(hit.text);
                txtStockQuantity.requestFocusInWindow();
            }
        );

        // Typed a full medicine name and moved on (without picking from the list): still auto-fill
        txtMedicineName.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                autoFillIfKnown();
            }
        });
    }

    // Fills company, unit price, prescription and moving type from an existing medicine.
    // Everything stays editable afterwards.
    private void fillFromMedicine(Medicine m) {
        String name = m.getMedicineName() == null ? "" : m.getMedicineName().trim();
        nameSuggest.setTextSilently(name);

        String company = m.getCompanyName() == null ? "" : m.getCompanyName().trim();
        txtCompanyName.setText("Unknown".equalsIgnoreCase(company) ? "" : company);
        txtUnitPrice.setText(String.format(java.util.Locale.US, "%.2f", m.getUnitPrice()));
        chkRequiresPrescription.setSelected(m.isRequiresPrescription());

        String type = m.getMovingType() == null ? "SLOW" : m.getMovingType().trim().toUpperCase(java.util.Locale.ROOT);
        cmbMovingType.setSelectedItem("FAST".equals(type) ? "FAST" : "SLOW");

        lastFilledName = name;
    }

    private void autoFillIfKnown() {
        String name = txtMedicineName.getText().trim();
        if (name.isEmpty() || name.equalsIgnoreCase(lastFilledName)) {
            return;
        }
        try {
            Medicine m = SuggestionLogic.exactMedicine(inventoryDAO.searchInventoryByNameOrBatch(name), name);
            if (m != null) {
                fillFromMedicine(m);
            }
        } catch (SQLException ignored) {
            // no auto-fill if the database cannot be read right now
        }
    }

    // =========================================================================
    // Data Loading & Search Handlers
    // =========================================================================

    public void loadInventoryData() {
        tableModel.setRowCount(0);
        try {
            List<Medicine> list = inventoryDAO.getAllInventory();
            for (Medicine med : list) {
                tableModel.addRow(new Object[]{
                    med.getMedicineId(),
                    med.getMedicineName(),
                    med.getCompanyName(),
                    med.getBatchNumber(),
                    String.format("%.2f", med.getUnitPrice()),
                    med.getStockQuantity(),
                    med.getStockDate() != null ? med.getStockDate().toString() : "-",
                    med.getExpDate() != null ? med.getExpDate().toString() : "-",
                    med.isRequiresPrescription() ? "Yes" : "No",
                    med.getMovingType() != null ? med.getMovingType() : "SLOW"
                });
            }
        } catch (SQLException ex) {
            System.err.println("Warning: Failed to load inventory data: " + ex.getMessage());
        }
    }

    private void searchInventory() {
        String keyword = txtSearch.getText().trim();
        if (keyword.isEmpty()) {
            loadInventoryData();
            return;
        }

        tableModel.setRowCount(0);
        try {
            List<Medicine> list = inventoryDAO.searchInventoryByNameOrBatch(keyword);
            for (Medicine med : list) {
                tableModel.addRow(new Object[]{
                    med.getMedicineId(),
                    med.getMedicineName(),
                    med.getCompanyName(),
                    med.getBatchNumber(),
                    String.format("%.2f", med.getUnitPrice()),
                    med.getStockQuantity(),
                    med.getStockDate() != null ? med.getStockDate().toString() : "-",
                    med.getExpDate() != null ? med.getExpDate().toString() : "-",
                    med.isRequiresPrescription() ? "Yes" : "No",
                    med.getMovingType() != null ? med.getMovingType() : "SLOW"
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Search error: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // 3. Add Batch / Restock Action (Owner Only)
    // =========================================================================

    private void handleAddBatch() {
        String medName = txtMedicineName.getText().trim();
        String companyName = txtCompanyName.getText().trim();
        String batchNo = txtBatchNumber.getText().trim();
        String unitPriceStr = txtUnitPrice.getText().trim();
        String stockQtyStr = txtStockQuantity.getText().trim();
        String stockDateStr = txtStockDate.getText().trim();
        String expDateStr = txtExpDate.getText().trim();
        boolean requiresRx = chkRequiresPrescription.isSelected();
        String movingType = (String) cmbMovingType.getSelectedItem();

        // 1. Validate inputs
        if (medName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Medicine Name cannot be empty.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtMedicineName.requestFocusInWindow();
            return;
        }

        if (batchNo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Batch Number cannot be empty.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtBatchNumber.requestFocusInWindow();
            return;
        }

        double unitPrice;
        try {
            unitPrice = Double.parseDouble(unitPriceStr);
            if (unitPrice < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Unit Price must be a valid positive number.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtUnitPrice.requestFocusInWindow();
            return;
        }

        int stockQty;
        try {
            stockQty = Integer.parseInt(stockQtyStr);
            if (stockQty < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Stock Quantity must be a valid non-negative integer.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtStockQuantity.requestFocusInWindow();
            return;
        }

        Date stockDate;
        try {
            stockDate = Date.valueOf(stockDateStr);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Invalid Stock Date format. Use YYYY-MM-DD (e.g. " + LocalDate.now() + ").", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtStockDate.requestFocusInWindow();
            return;
        }

        Date expDate;
        try {
            expDate = Date.valueOf(expDateStr);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Invalid Expiry Date format. Use YYYY-MM-DD (e.g. " + LocalDate.now().plusMonths(12) + ").", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtExpDate.requestFocusInWindow();
            return;
        }

        // 2. Check what is already in the inventory (GUI-side rules, no DAO change)
        List<Medicine> all;
        try {
            all = inventoryDAO.getAllInventory();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error reading inventory: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Medicine sameBatch = null;          // same medicine + same batch no  -> restock that row
        Medicine batchUsedByOther = null;   // batch no belongs to another medicine -> not allowed
        List<Medicine> sameMedicine = new ArrayList<>();
        for (Medicine m : all) {
            boolean nameEq = m.getMedicineName() != null && m.getMedicineName().trim().equalsIgnoreCase(medName);
            boolean batchEq = m.getBatchNumber() != null && m.getBatchNumber().trim().equalsIgnoreCase(batchNo);
            if (nameEq) {
                sameMedicine.add(m);
            }
            if (batchEq) {
                if (nameEq) {
                    if (sameBatch == null) {
                        sameBatch = m;
                    }
                } else if (batchUsedByOther == null) {
                    batchUsedByOther = m;
                }
            }
        }

        // Rule: batch number must be unique (one batch no = one medicine)
        if (batchUsedByOther != null) {
            JOptionPane.showMessageDialog(
                this,
                "Batch No [" + batchNo + "] is already used by medicine '" + batchUsedByOther.getMedicineName() + "'.\n"
                + "Batch numbers must be unique. Enter a different batch number.",
                "Duplicate Batch Number",
                JOptionPane.WARNING_MESSAGE
            );
            txtBatchNumber.requestFocusInWindow();
            return;
        }

        // Keep one spelling per medicine (use the name already stored)
        final String finalName = sameMedicine.isEmpty() ? medName : sameMedicine.get(0).getMedicineName().trim();

        boolean movingDiffers = false;
        boolean rxDiffers = false;
        for (Medicine m : sameMedicine) {
            String existingType = m.getMovingType() != null ? m.getMovingType() : "SLOW";
            if (!existingType.equalsIgnoreCase(movingType)) {
                movingDiffers = true;
            }
            if (m.isRequiresPrescription() != requiresRx) {
                rxDiffers = true;
            }
        }

        // 3. Explain what will happen when the medicine already exists
        if (sameBatch != null || movingDiffers || rxDiffers) {
            StringBuilder msg = new StringBuilder();
            msg.append("Medicine '").append(finalName).append("' already exists in the inventory.\n\n");
            if (sameBatch != null) {
                msg.append("- Batch [").append(batchNo).append("] already exists (stock ")
                   .append(sameBatch.getStockQuantity()).append("). The quantity ").append(stockQty)
                   .append(" will be ADDED to it (new total ").append(sameBatch.getStockQuantity() + stockQty)
                   .append("). No new batch is created.\n");
            }
            if (movingDiffers) {
                msg.append("- Moving type of ALL ").append(sameMedicine.size())
                   .append(" existing batch(es) will be changed to ").append(movingType).append(".\n");
            }
            if (rxDiffers) {
                msg.append("- Prescription requirement differs from the existing batches. This version cannot change it for\n")
                   .append("  the existing batches (the backend has no update method for it), so only the new entry uses it.\n");
            }
            msg.append("\nContinue?");
            int choice = JOptionPane.showConfirmDialog(this, msg.toString(), "Medicine Already Exists",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // 4. Owner-only: a cashier gets the temporary Owner login pop-up here
        StaffMember owner = OwnerAccess.requestOwner(this, "Adding or restocking inventory is an Owner-only function.");
        if (owner == null) {
            return;
        }

        // 5. Save: restock the same batch OR insert a new batch, then sync the moving type
        final Medicine existingBatch = sameBatch;
        final boolean syncMoving = movingDiffers;
        final int addQty = stockQty;
        final String movingFinal = (movingType != null) ? movingType : "SLOW";
        final Medicine med = new Medicine(
            0,
            finalName,
            companyName.isEmpty() ? "Unknown" : companyName,
            batchNo,
            unitPrice,
            stockQty,
            stockDate,
            expDate,
            requiresRx,
            movingFinal
        );

        try {
            String summary = OwnerAccess.callAs(owner, () -> {
                StringBuilder sb = new StringBuilder();
                if (existingBatch != null) {
                    int newTotal = existingBatch.getStockQuantity() + addQty;
                    if (!inventoryDAO.updateStockQuantity(existingBatch.getMedicineId(), newTotal)) {
                        throw new IllegalStateException("Stock update failed - the batch no longer exists.");
                    }
                    sb.append("Batch [").append(existingBatch.getBatchNumber()).append("] of '").append(finalName)
                      .append("' already existed, so its stock was increased from ")
                      .append(existingBatch.getStockQuantity()).append(" to ").append(newTotal)
                      .append(". No new batch was created.");
                } else {
                    if (!inventoryDAO.addMedicineBatch(med)) {
                        throw new IllegalStateException("The new batch could not be saved.");
                    }
                    sb.append("Batch [").append(med.getBatchNumber()).append("] for medicine '").append(finalName)
                      .append("' added successfully to inventory!");
                }
                if (syncMoving) {
                    inventoryDAO.updateMovingType(finalName, movingFinal);
                    sb.append("\nMoving type of all batches of '").append(finalName).append("' set to ")
                      .append(movingFinal).append(".");
                }
                return sb.toString();
            });

            JOptionPane.showMessageDialog(this, summary, "Inventory Updated", JOptionPane.INFORMATION_MESSAGE);
            clearAddBatchForm();
            loadInventoryData();
        } catch (UnauthorizedAccessException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Access Denied - Owner Only", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error adding batch: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error adding batch: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearAddBatchForm() {
        txtMedicineName.setText("");
        txtCompanyName.setText("");
        txtBatchNumber.setText("");
        txtUnitPrice.setText("");
        txtStockQuantity.setText("");
        txtStockDate.setText(LocalDate.now().toString());
        txtExpDate.setText(LocalDate.now().plusMonths(12).toString());
        chkRequiresPrescription.setSelected(false);
        cmbMovingType.setSelectedItem("SLOW");
        nameSuggest.hide();
        batchSuggest.hide();
        lastFilledName = null;
    }

    // =========================================================================
    // 4. Manual Moving Type Toggle (Owner Only)
    // =========================================================================

    private void toggleMovingType() {
        int selectedRow = tblInventory.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(
                this,
                "Please select an inventory batch row from the table to toggle its moving type.",
                "No Selection",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        int medId = (int) tableModel.getValueAt(selectedRow, 0);
        String medName = (String) tableModel.getValueAt(selectedRow, 1);
        String currentType = (String) tableModel.getValueAt(selectedRow, 9);
        String newType = "FAST".equalsIgnoreCase(currentType) ? "SLOW" : "FAST";

        StaffMember owner = OwnerAccess.requestOwner(this, "Changing the moving type is an Owner-only function.");
        if (owner == null) {
            return;
        }

        try {
            boolean success = OwnerAccess.callAs(owner, () -> inventoryDAO.updateMovingType(medName, newType));
            if (success) {
                JOptionPane.showMessageDialog(
                    this,
                    "Moving speed for medicine '" + medName + "' successfully updated to " + newType + "!",
                    "Moving Type Updated",
                    JOptionPane.INFORMATION_MESSAGE
                );
                loadInventoryData();
                // Reselect updated row
                for (int i = 0; i < tblInventory.getRowCount(); i++) {
                    if ((int) tblInventory.getValueAt(i, 0) == medId) {
                        tblInventory.setRowSelectionInterval(i, i);
                        break;
                    }
                }
            }
        } catch (UnauthorizedAccessException ex) {
            JOptionPane.showMessageDialog(
                this,
                ex.getMessage(),
                "Access Denied - Owner Privileges Required",
                JOptionPane.WARNING_MESSAGE
            );
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to update moving type: " + ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Error: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // =========================================================================
    // 5. Update Stock Quantity of ONE batch (Owner Only)
    // =========================================================================

    private void updateSelectedBatchStock() {
        int row = tblInventory.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                "Please select the batch row in the table whose stock you want to update.",
                "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int medId = (int) tableModel.getValueAt(row, 0);
        String medName = String.valueOf(tableModel.getValueAt(row, 1));
        String batchNo = String.valueOf(tableModel.getValueAt(row, 3));
        int currentQty = ((Number) tableModel.getValueAt(row, 5)).intValue();

        // Small form: shows the batch and asks for the new quantity
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JLabel lblInfo = new JLabel("<html>Medicine: <b>" + medName + "</b><br>Batch No: <b>" + batchNo
                + "</b><br>Current stock: <b>" + currentQty + "</b></html>");
        lblInfo.setFont(Theme.BODY_FONT);
        lblInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lblNew = new JLabel("New stock quantity (replaces the current stock)");
        lblNew.setFont(Theme.BOLD_BODY_FONT);
        lblNew.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField txtNewQty = new JTextField(String.valueOf(currentQty), 14);
        UIHelper.styleTextField(txtNewQty);
        txtNewQty.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(lblInfo);
        form.add(Box.createVerticalStrut(10));
        form.add(lblNew);
        form.add(Box.createVerticalStrut(4));
        form.add(txtNewQty);
        UIHelper.applyContrast(form);

        SwingUtilities.invokeLater(() -> {
            txtNewQty.requestFocusInWindow();
            txtNewQty.selectAll();
        });
        int choice = JOptionPane.showConfirmDialog(this, form, "Update Stock Quantity",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        int newQty;
        try {
            newQty = Integer.parseInt(txtNewQty.getText().trim());
            if (newQty < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Stock quantity must be a whole number of 0 or more.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (newQty == currentQty) {
            JOptionPane.showMessageDialog(this, "The quantity is unchanged.", "No Change", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StaffMember owner = OwnerAccess.requestOwner(this, "Updating stock quantity is an Owner-only function.");
        if (owner == null) {
            return;
        }

        final int qtyToSet = newQty;
        try {
            boolean updated = OwnerAccess.callAs(owner, () -> inventoryDAO.updateStockQuantity(medId, qtyToSet));
            if (updated) {
                JOptionPane.showMessageDialog(this,
                    "Stock of '" + medName + "' (batch " + batchNo + ") changed from " + currentQty + " to " + qtyToSet + ".",
                    "Stock Updated", JOptionPane.INFORMATION_MESSAGE);
                loadInventoryData();
                for (int i = 0; i < tblInventory.getRowCount(); i++) {
                    if ((int) tblInventory.getValueAt(i, 0) == medId) {
                        tblInventory.setRowSelectionInterval(i, i);
                        break;
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "No batch was updated. It may have been removed.",
                        "Not Updated", JOptionPane.WARNING_MESSAGE);
            }
        } catch (UnauthorizedAccessException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Access Denied - Owner Only", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // 6. Delete Selected Batch (Owner Only)
    // =========================================================================

    private void deleteSelectedBatch() {
        int row = tblInventory.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(
                this,
                "Please select the batch row in the table that you want to delete.",
                "No Selection",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        int medId = (int) tableModel.getValueAt(row, 0);
        String medName = String.valueOf(tableModel.getValueAt(row, 1));
        String batchNo = String.valueOf(tableModel.getValueAt(row, 3));

        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to permanently delete batch [" + batchNo + "] of '" + medName + "'?\n"
            + "(Note: Batches already linked to past sales bills cannot be deleted.)",
            "Confirm Batch Deletion",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        StaffMember owner = OwnerAccess.requestOwner(this, "Deleting an inventory batch is an Owner-only function.");
        if (owner == null) {
            return;
        }

        try {
            boolean deleted = OwnerAccess.callAs(owner, () -> inventoryDAO.deleteMedicineBatch(medId));
            if (deleted) {
                JOptionPane.showMessageDialog(
                    this,
                    "Batch [" + batchNo + "] of '" + medName + "' was deleted successfully.",
                    "Batch Deleted",
                    JOptionPane.INFORMATION_MESSAGE
                );
                loadInventoryData();
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Batch could not be deleted. It may have already been removed.",
                    "Not Deleted",
                    JOptionPane.WARNING_MESSAGE
                );
            }
        } catch (UnauthorizedAccessException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Access Denied - Owner Only", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            // Displays the friendly DAO message if linked to past bills (ErrorCode 547)
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot Delete Batch", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error deleting batch: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
