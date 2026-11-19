package auth;

import exceptions.InvalidStaffIdException;
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
import java.sql.SQLException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import models.StaffMember;
import ui.Theme;
import ui.UIHelper;

/**
 * StaffPanel provides staff directory browsing, account status toggles,
 * and registration of new staff members (Owner Only).
 */
public class StaffPanel extends JPanel {

    // Backend Access
    private final UserDAO userDAO = new UserDAO();

    // 1. Register Staff Form Components (Owner Only)
    private JTextField txtStaffId;
    private JTextField txtStaffName;
    private JComboBox<String> cmbRole;
    private JTextField txtContactNumber;
    private JPasswordField txtPassword;
    private JComboBox<String> cmbStatus;
    private JButton btnRegisterStaff;
    private JButton btnAutoGenerateId;
    private JButton btnClearForm;

    // 2. Staff Directory Table Components
    private JTable tblStaff;
    private DefaultTableModel tableModel;
    private JButton btnToggleStatus;
    private JButton btnRefresh;

    public StaffPanel() {
        initComponents();
        loadStaffData();
        autoSuggestNextId();
    }

    private void initComponents() {
        setLayout(new BorderLayout(15, 15));
        setBackground(Theme.MAIN_BG);
        setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // Top Header
        JPanel headerPanel = buildHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Center Split (Left: Register Form Card, Right: Directory Table Card)
        JPanel centerGrid = new JPanel(new GridLayout(1, 2, 15, 0));
        centerGrid.setBackground(Theme.MAIN_BG);

        JPanel registerCard = buildRegisterStaffCard();
        JPanel directoryCard = buildDirectoryTableCard();

        centerGrid.add(registerCard);
        centerGrid.add(directoryCard);

        add(centerGrid, BorderLayout.CENTER);
    }

    // =========================================================================
    // Top Header
    // =========================================================================

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MAIN_BG);

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setBackground(Theme.MAIN_BG);

        JLabel lblTitle = UIHelper.createPageTitle("Staff Management (Owner Privileges Required)");
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);


        JLabel lblSub = new JLabel("");
        lblSub.setFont(Theme.BODY_FONT);
        lblSub.setForeground(Theme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);
        header.add(titleBox, BorderLayout.WEST);

        // Active user status badge
        StaffMember currentUser = UserSession.getCurrentUser();
        String role = (currentUser != null && currentUser.getRole() != null)
                ? currentUser.getRole().toUpperCase()
                : "OWNER";
        JLabel lblRoleBadge = new JLabel("Authorized As: " + role);
        lblRoleBadge.setFont(Theme.BOLD_BODY_FONT);
        lblRoleBadge.setForeground(Theme.TEXT_DARK);
        lblRoleBadge.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        lblRoleBadge.setOpaque(true);
        lblRoleBadge.setBackground(Theme.CARD_BG);
        header.add(lblRoleBadge, BorderLayout.EAST);

        return header;
    }

    // =========================================================================
    // 1. Register New Staff Form (UIHelper.styleCardPanel - Owner Only)
    // =========================================================================

    private JPanel buildRegisterStaffCard() {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        UIHelper.styleCardPanel(card);

        // Card Header
        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setBackground(Theme.CARD_BG);

        JLabel lblTitle = new JLabel("1. Register New Staff Member");
        lblTitle.setFont(Theme.SUBTITLE_FONT);
        lblTitle.setForeground(Theme.TEXT_DARK);
        cardHeader.add(lblTitle, BorderLayout.WEST);

        JLabel lblOwnerOnly = new JLabel("Owner Authority");
        lblOwnerOnly.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblOwnerOnly.setForeground(Theme.TEXT_MUTED);
        cardHeader.add(lblOwnerOnly, BorderLayout.EAST);

        card.add(cardHeader, BorderLayout.NORTH);

        // Form Fields Container
        JPanel formGrid = new JPanel(new GridBagLayout());
        formGrid.setBackground(Theme.CARD_BG);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 1. Role (JComboBox: "CASHIER", "OWNER")
        gbc.gridx = 0; gbc.gridy = 0;
        formGrid.add(createFieldLabel("Operational Role:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        gbc.gridwidth = 2;
        cmbRole = new JComboBox<>(new String[]{"CASHIER", "OWNER"});
        cmbRole.setFont(Theme.BODY_FONT);
        cmbRole.setBackground(Color.WHITE);
        cmbRole.setPreferredSize(new Dimension(240, 36));
        cmbRole.addActionListener(e -> autoSuggestNextId());
        formGrid.add(cmbRole, gbc);

        // 2. Staff ID with Auto-Generate Helper
        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        formGrid.add(createFieldLabel("Staff ID:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        txtStaffId = new JTextField();
        UIHelper.styleTextField(txtStaffId);
        txtStaffId.setPreferredSize(new Dimension(150, 36));
        formGrid.add(txtStaffId, gbc);

        gbc.gridx = 2; gbc.gridy = 1;
        btnAutoGenerateId = new JButton("Generate ID");
        UIHelper.styleSecondaryButton(btnAutoGenerateId);
        btnAutoGenerateId.setPreferredSize(new Dimension(110, 36));
        btnAutoGenerateId.addActionListener(e -> autoSuggestNextId());
        formGrid.add(btnAutoGenerateId, gbc);

        // 3. Full Name
        gbc.gridx = 0; gbc.gridy = 2;
        formGrid.add(createFieldLabel("Full Name:"), gbc);

        gbc.gridx = 1; gbc.gridy = 2;
        gbc.gridwidth = 2;
        txtStaffName = new JTextField();
        UIHelper.styleTextField(txtStaffName);
        txtStaffName.setPreferredSize(new Dimension(240, 36));
        formGrid.add(txtStaffName, gbc);

        // 4. Contact Number (10 digits)
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 1;
        formGrid.add(createFieldLabel("Contact Number:"), gbc);

        gbc.gridx = 1; gbc.gridy = 3;
        gbc.gridwidth = 2;
        txtContactNumber = new JTextField();
        UIHelper.styleTextField(txtContactNumber);
        txtContactNumber.setPreferredSize(new Dimension(240, 36));
        formGrid.add(txtContactNumber, gbc);

        // 5. Password (JPasswordField)
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 1;
        formGrid.add(createFieldLabel("Account Password:"), gbc);

        gbc.gridx = 1; gbc.gridy = 4;
        gbc.gridwidth = 2;
        txtPassword = new JPasswordField();
        UIHelper.styleTextField(txtPassword);
        txtPassword.setPreferredSize(new Dimension(240, 36));
        formGrid.add(txtPassword, gbc);

        // 6. Status (JComboBox: "ACTIVE", "INACTIVE")
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.gridwidth = 1;
        formGrid.add(createFieldLabel("Initial Status:"), gbc);

        gbc.gridx = 1; gbc.gridy = 5;
        gbc.gridwidth = 2;
        cmbStatus = new JComboBox<>(new String[]{"ACTIVE", "INACTIVE"});
        cmbStatus.setFont(Theme.BODY_FONT);
        cmbStatus.setBackground(Color.WHITE);
        cmbStatus.setPreferredSize(new Dimension(240, 36));
        formGrid.add(cmbStatus, gbc);

        // 7. Buttons: "Register Staff" & "Clear Form"
        gbc.gridx = 0; gbc.gridy = 6;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(16, 6, 6, 6);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setBackground(Theme.CARD_BG);

        btnClearForm = new JButton("Clear Form");
        UIHelper.styleSecondaryButton(btnClearForm);
        btnClearForm.addActionListener(e -> clearForm());
        btnPanel.add(btnClearForm);

        btnRegisterStaff = new JButton("Register Staff");
        UIHelper.stylePrimaryButton(btnRegisterStaff);
        btnRegisterStaff.addActionListener(e -> handleRegisterStaff());
        btnPanel.add(btnRegisterStaff);

        formGrid.add(btnPanel, gbc);

        card.add(formGrid, BorderLayout.CENTER);
        return card;
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(Theme.BOLD_BODY_FONT);
        lbl.setForeground(Theme.TEXT_DARK);
        return lbl;
    }

    private void autoSuggestNextId() {
        String role = (String) cmbRole.getSelectedItem();
        if (role == null) role = "CASHIER";

        try {
            String nextId = userDAO.generateNextId(role);
            if (nextId != null && !nextId.isEmpty()) {
                txtStaffId.setText(nextId);
            }
        } catch (SQLException ex) {
            // Graceful fallback
        }
    }

    private void clearForm() {
        txtStaffName.setText("");
        txtContactNumber.setText("");
        txtPassword.setText("");
        cmbStatus.setSelectedItem("ACTIVE");
        autoSuggestNextId();
        txtStaffName.requestFocusInWindow();
    }

    // =========================================================================
    // 2. Staff Directory Table (UIHelper.styleTable inside UIHelper.styleCardPanel)
    // =========================================================================

    private JPanel buildDirectoryTableCard() {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        UIHelper.styleCardPanel(card);

        // Card Header
        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setBackground(Theme.CARD_BG);

        JLabel lblTitle = new JLabel("2. Staff Directory");
        lblTitle.setFont(Theme.SUBTITLE_FONT);
        lblTitle.setForeground(Theme.TEXT_DARK);
        cardHeader.add(lblTitle, BorderLayout.WEST);

        // Actions: Toggle Status & Refresh
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setBackground(Theme.CARD_BG);

        btnToggleStatus = new JButton("Activate / Deactivate Selected Staff");
        UIHelper.styleSecondaryButton(btnToggleStatus);
        btnToggleStatus.addActionListener(e -> handleToggleStatus());
        actionPanel.add(btnToggleStatus);

        btnRefresh = new JButton("Refresh");
        UIHelper.styleSecondaryButton(btnRefresh);
        btnRefresh.addActionListener(e -> loadStaffData());
        actionPanel.add(btnRefresh);

        cardHeader.add(actionPanel, BorderLayout.EAST);
        card.add(cardHeader, BorderLayout.NORTH);

        // Table Columns: Staff ID, Full Name, Role, Contact Number, Status
        String[] columns = {"Staff ID", "Full Name", "Role", "Contact Number", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false; // Read-only directory
            }
        };

        tblStaff = new JTable(tableModel);
        UIHelper.styleTable(tblStaff);

        tblStaff.getColumnModel().getColumn(0).setPreferredWidth(80);
        tblStaff.getColumnModel().getColumn(1).setPreferredWidth(170);
        tblStaff.getColumnModel().getColumn(2).setPreferredWidth(90);
        tblStaff.getColumnModel().getColumn(3).setPreferredWidth(120);
        tblStaff.getColumnModel().getColumn(4).setPreferredWidth(90);

        // Custom status renderer (Green for ACTIVE, Red for INACTIVE)
        tblStaff.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                String status = (value != null) ? value.toString().toUpperCase() : "";

                if (!isSelected) {
                    if ("ACTIVE".equals(status)) {
                        setForeground(new Color(22, 101, 52)); // Dark green
                        setFont(Theme.BOLD_BODY_FONT);
                    } else {
                        setForeground(new Color(185, 28, 28)); // Red
                        setFont(Theme.BOLD_BODY_FONT);
                    }
                }
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(tblStaff);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        card.add(scrollPane, BorderLayout.CENTER);

        return card;
    }

    // =========================================================================
    // Action Handlers
    // =========================================================================

    public void loadStaffData() {
        if (tableModel == null) return;
        tableModel.setRowCount(0);

        try {
            List<StaffMember> list = userDAO.getAllStaff();
            if (list != null) {
                for (StaffMember staff : list) {
                    tableModel.addRow(new Object[]{
                        staff.getStaffId(),
                        staff.getStaffName(),
                        staff.getRole(),
                        staff.getContactNumber(),
                        staff.getStatus()
                    });
                }
            }
        } catch (UnauthorizedAccessException ex) {
            // Non-owners do not load staff list
        } catch (SQLException ex) {
            System.err.println("Warning: Failed to load staff list: " + ex.getMessage());
        }
    }

    private void handleRegisterStaff() {
        String staffId = txtStaffId.getText().trim();
        String staffName = txtStaffName.getText().trim();
        String role = (String) cmbRole.getSelectedItem();
        String contact = txtContactNumber.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();
        String status = (String) cmbStatus.getSelectedItem();

        // 1. Validation checks
        if (staffId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Staff ID cannot be empty.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtStaffId.requestFocusInWindow();
            return;
        }

        if (staffName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Full Name cannot be empty.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtStaffName.requestFocusInWindow();
            return;
        }

        // Contact Number validation (10 digits)
        if (!contact.matches("\\d{10}")) {
            JOptionPane.showMessageDialog(
                this,
                "Contact Number must be exactly 10 digits (e.g., 0771234567).",
                "Validation Error",
                JOptionPane.WARNING_MESSAGE
            );
            txtContactNumber.requestFocusInWindow();
            return;
        }

        // Password validation
        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Password cannot be empty.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            txtPassword.requestFocusInWindow();
            return;
        }

        // 2. Construct StaffMember object
        StaffMember newStaff = new StaffMember(
            staffId,
            staffName,
            role != null ? role : "CASHIER",
            contact,
            password,
            status != null ? status : "ACTIVE"
        );

        // 3. Call UserDAO.registerStaff(newStaff)
        try {
            boolean registered = userDAO.registerStaff(newStaff);
            if (registered) {
                JOptionPane.showMessageDialog(
                    this,
                    "Staff member '" + staffName + "' (" + staffId + ") registered successfully as " + role + "!",
                    "Staff Registered",
                    JOptionPane.INFORMATION_MESSAGE
                );
                clearForm();
                loadStaffData();
            }
        } catch (UnauthorizedAccessException ex) {
            JOptionPane.showMessageDialog(
                this,
                ex.getMessage(),
                "Access Denied - Owner Only",
                JOptionPane.ERROR_MESSAGE
            );
        } catch (InvalidStaffIdException ex) {
            JOptionPane.showMessageDialog(
                this,
                ex.getMessage(),
                "Invalid Staff ID",
                JOptionPane.WARNING_MESSAGE
            );
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Database error registering staff: " + ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Error: " + ex.getMessage(),
                "Registration Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void handleToggleStatus() {
        int selectedRow = tblStaff.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(
                this,
                "Please select a staff member from the directory table to toggle their status.",
                "No Selection",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        String staffId = (String) tableModel.getValueAt(selectedRow, 0);
        String staffName = (String) tableModel.getValueAt(selectedRow, 1);
        String currentStatus = (String) tableModel.getValueAt(selectedRow, 4);

        // Prevent self-deactivation
        StaffMember currentUser = UserSession.getCurrentUser();
        if (currentUser != null && staffId.equalsIgnoreCase(currentUser.getStaffId())) {
            JOptionPane.showMessageDialog(
                this,
                "You cannot deactivate your own currently active account.",
                "Action Prohibited",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        String newStatus = "ACTIVE".equalsIgnoreCase(currentStatus) ? "INACTIVE" : "ACTIVE";

        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to change the status of:\n" +
            "Staff ID: " + staffId + " (" + staffName + ")\n" +
            "Current Status: " + currentStatus + "\n" +
            "New Status: " + newStatus + "?",
            "Confirm Status Change",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            boolean success = userDAO.updateStaffStatus(staffId, newStatus);
            if (success) {
                JOptionPane.showMessageDialog(
                    this,
                    "Staff member [" + staffId + "] status successfully changed to " + newStatus + "!",
                    "Status Updated",
                    JOptionPane.INFORMATION_MESSAGE
                );
                loadStaffData();
                // Reselect row
                for (int i = 0; i < tblStaff.getRowCount(); i++) {
                    if (staffId.equalsIgnoreCase((String) tblStaff.getValueAt(i, 0))) {
                        tblStaff.setRowSelectionInterval(i, i);
                        break;
                    }
                }
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
                "Database error updating staff status: " + ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Error updating status: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
