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
    }