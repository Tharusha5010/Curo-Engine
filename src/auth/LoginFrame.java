package auth;

import exceptions.InvalidStaffIdException;
import inventory.InventoryDAO;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.SQLException;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import models.StaffMember;
import ui.DashboardFrame;
import ui.Theme;
import ui.UIHelper;

/**
 * LoginFrame provides the primary authentication GUI for the Curo Pharmacy System.
 * It is styled strictly following the application theme and connects directly
 * to backend DAOs and session managers.
 */
public class LoginFrame extends JFrame {

    private JTextField txtStaffId;
    private JPasswordField txtPassword;
    private JButton btnLogin;

    public LoginFrame() {
        initComponents();
    }

    private void initComponents() {
        setTitle("Curo Engine - Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        UIHelper.fitToScreen(this, 460, 540, 380, 440);
        setResizable(true);

        // Root container styled with Theme.MAIN_BG
        JPanel rootPanel = new JPanel(new GridBagLayout());
        rootPanel.setBackground(Theme.MAIN_BG);

        // Card container styled strictly with Theme.CARD_BG and UIHelper.styleCardPanel
        JPanel cardPanel = new JPanel();
        UIHelper.styleCardPanel(cardPanel);
        cardPanel.setBackground(Theme.CARD_BG);
        cardPanel.setLayout(new GridBagLayout());
        cardPanel.setPreferredSize(new Dimension(380, 420));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Decorative accent bar using Theme.SIDEBAR_BG
        JPanel topAccentBar = new JPanel();
        topAccentBar.setBackground(Theme.SIDEBAR_BG);
        topAccentBar.setPreferredSize(new Dimension(340, 4));
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 15, 0);
        cardPanel.add(topAccentBar, gbc);

        // Header Panel containing Title and Subtitle
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(Theme.CARD_BG);

        // System Title styled with Theme.TITLE_FONT and Theme.SIDEBAR_BG
        JLabel lblTitle = new JLabel("Curo Engine");
        lblTitle.setFont(Theme.TITLE_FONT);
        lblTitle.setForeground(Theme.SIDEBAR_BG);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Subtitle styled with Theme.BODY_FONT and Theme.TEXT_MUTED
        JLabel lblSubtitle = new JLabel("Staff Login");
        lblSubtitle.setFont(Theme.BODY_FONT);
        lblSubtitle.setForeground(Theme.TEXT_MUTED);
        lblSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(lblTitle);
        headerPanel.add(Box.createVerticalStrut(6));
        headerPanel.add(lblSubtitle);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 20, 0);
        cardPanel.add(headerPanel, gbc);

        // Staff ID Label styled with Theme.BODY_FONT
        JLabel lblStaffId = new JLabel("Staff ID");
        lblStaffId.setFont(Theme.BODY_FONT);
        lblStaffId.setForeground(Theme.TEXT_DARK);

        gbc.gridy = 2;
        gbc.insets = new Insets(0, 10, 5, 10);
        cardPanel.add(lblStaffId, gbc);

        // Staff ID Text Field styled with UIHelper.styleTextField()
        txtStaffId = new JTextField();
        UIHelper.styleTextField(txtStaffId);
        txtStaffId.setPreferredSize(new Dimension(320, 36));

        gbc.gridy = 3;
        gbc.insets = new Insets(0, 10, 15, 10);
        cardPanel.add(txtStaffId, gbc);

        // Password Label styled with Theme.BODY_FONT
        JLabel lblPassword = new JLabel("Password");
        lblPassword.setFont(Theme.BODY_FONT);
        lblPassword.setForeground(Theme.TEXT_DARK);

        gbc.gridy = 4;
        gbc.insets = new Insets(0, 10, 5, 10);
        cardPanel.add(lblPassword, gbc);

        // Password Field styled with UIHelper.styleTextField()
        txtPassword = new JPasswordField();
        UIHelper.styleTextField(txtPassword);
        txtPassword.setPreferredSize(new Dimension(320, 36));

        gbc.gridy = 5;
        gbc.insets = new Insets(0, 10, 25, 10);
        cardPanel.add(txtPassword, gbc);

        // Login Button styled with UIHelper.stylePrimaryButton()
        btnLogin = new JButton("Login");
        UIHelper.stylePrimaryButton(btnLogin);
        btnLogin.setPreferredSize(new Dimension(320, 38));

        gbc.gridy = 6;
        gbc.insets = new Insets(0, 10, 10, 10);
        cardPanel.add(btnLogin, gbc);

        // Trigger login on button click
        btnLogin.addActionListener(e -> handleLogin());

        // Trigger login on pressing Enter in the password field
        txtPassword.addActionListener(e -> handleLogin());

        // Pressing Enter in Staff ID field focuses Password field
        txtStaffId.addActionListener(e -> txtPassword.requestFocusInWindow());

        // Set Login button as default for the root pane
        getRootPane().setDefaultButton(btnLogin);

        // Center the card in the frame
        rootPanel.add(cardPanel, new GridBagConstraints());
        setContentPane(rootPanel);
        UIHelper.applyContrast(rootPanel);
    }

    /**
     * Handles the login action:
     * 1. Validates inputs
     * 2. Authenticates through UserDAO
     * 3. Ensures UserSession is set
     * 4. Generates startup inventory alerts
     * 5. Navigates to DashboardFrame
     */
    private void handleLogin() {
        String staffId = txtStaffId.getText().trim();
        String password = new String(txtPassword.getPassword());

        // Validate that fields are not empty
        if (staffId.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Staff ID and Password cannot be empty.",
                "Validation Error",
                JOptionPane.WARNING_MESSAGE
            );
            if (staffId.isEmpty()) {
                txtStaffId.requestFocusInWindow();
            } else {
                txtPassword.requestFocusInWindow();
            }
            return;
        }

        try {
            // Attempt user authentication
            StaffMember user = new UserDAO().loginUser(staffId, password);

            // 1. Ensure UserSession.setCurrentUser(user) is set (check UserDAO if already set, set it if null)
            if (UserSession.getCurrentUser() == null) {
                UserSession.setCurrentUser(user);
            }

            // 2. Populate RAM alerts on startup
            new InventoryDAO().checkAndGenerateStartupAlerts();

            // 3. Open DashboardFrame and dispose LoginFrame
            new DashboardFrame().setVisible(true);
            this.dispose();

        } catch (InvalidStaffIdException ex) {
            JOptionPane.showMessageDialog(
                this,
                ex.getMessage(),
                "Authentication Failed",
                JOptionPane.ERROR_MESSAGE
            );
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Database error: " + ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "An unexpected error occurred: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    public static void main(String[] args) {
        ui.Theme.installLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            new LoginFrame().setVisible(true);
        });
    }
}
