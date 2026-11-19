package ui;

import auth.UserDAO;
import auth.UserSession;
import exceptions.InvalidStaffIdException;
import java.awt.*;
import java.sql.SQLException;
import javax.swing.*;
import models.StaffMember;

/**
 * Temporary Owner login for Owner-only actions.
 * When a cashier tries an Owner-only function, a pop-up asks for an Owner's staff ID and
 * password. The existing UserDAO.verifyOwnerOverride() checks them. The session is switched to
 * the owner only while the action runs (callAs) and is restored to the cashier afterwards.
 * No DAO / model / SQL is changed.
 */
public final class OwnerAccess {

    private OwnerAccess() {
    }

    public interface Action<T> {
        T run() throws Exception;
    }

    /** Returns the owner to act as, or null if cancelled. Current owner -> no pop-up. */
    public static StaffMember requestOwner(Component parent, String reason) {
        if (UserSession.isOwner()) {
            return UserSession.getCurrentUser();
        }
        return showDialog(parent, reason);
    }

    /** Runs the action with the owner as the active user, then restores the previous user. */
    public static <T> T callAs(StaffMember owner, Action<T> action) throws Exception {
        StaffMember previous = UserSession.getCurrentUser();
        if (owner != null && owner != previous) {
            UserSession.setCurrentUser(owner);
        }
        try {
            return action.run();
        } finally {
            UserSession.setCurrentUser(previous);
        }
    }

    private static StaffMember showDialog(Component parent, String reason) {
        Window parentWindow = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(parentWindow, "Owner Authorization", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        StaffMember[] result = new StaffMember[1];

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.MAIN_BG);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        UIHelper.styleCardPanel(card);

        JLabel title = new JLabel("Owner Login Required");
        title.setFont(Theme.TITLE_FONT);
        title.setForeground(Theme.TEXT_DARK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel msg = new JLabel("<html><body style='width:300px'>" + escape(reason)
                + "<br>An Owner can sign in here to approve this action. "
                + "You will stay signed in as yourself.</body></html>");
        msg.setFont(Theme.BODY_FONT);
        msg.setForeground(Theme.TEXT_DARK);
        msg.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblId = label("Owner Staff ID");
        JTextField txtId = new JTextField();
        UIHelper.styleTextField(txtId);
        txtId.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblPw = label("Password");
        JPasswordField txtPw = new JPasswordField();
        UIHelper.styleTextField(txtPw);
        txtPw.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblError = new JLabel(" ");
        lblError.setFont(Theme.BOLD_BODY_FONT);
        lblError.setForeground(new Color(185, 28, 28));
        lblError.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton btnOk = new JButton("Authorize");
        UIHelper.stylePrimaryButton(btnOk);
        JButton btnCancel = new JButton("Cancel");
        UIHelper.styleSecondaryButton(btnCancel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(Theme.CARD_BG);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(btnCancel);
        buttons.add(btnOk);

        Runnable submit = () -> {
            String id = txtId.getText().trim();
            String pw = new String(txtPw.getPassword());
            if (id.isEmpty() || pw.isEmpty()) {
                lblError.setText("Enter the Owner staff ID and password.");
                return;
            }
            try {
                StaffMember verified = new UserDAO().verifyOwnerOverride(id, pw);
                if (verified == null) {
                    lblError.setText("Not an active Owner account, or wrong password.");
                    txtPw.setText("");
                    txtPw.requestFocusInWindow();
                    return;
                }
                result[0] = verified;
                dialog.dispose();
            } catch (InvalidStaffIdException ex) {
                lblError.setText(ex.getMessage());
            } catch (SQLException ex) {
                lblError.setText("Database error: " + ex.getMessage());
            }
        };
        btnOk.addActionListener(e -> submit.run());
        btnCancel.addActionListener(e -> dialog.dispose());
        txtId.addActionListener(e -> txtPw.requestFocusInWindow());
        txtPw.addActionListener(e -> submit.run());

        card.add(title);
        card.add(Box.createVerticalStrut(8));
        card.add(msg);
        card.add(Box.createVerticalStrut(12));
        card.add(lblId);
        card.add(Box.createVerticalStrut(4));
        card.add(txtId);
        card.add(Box.createVerticalStrut(8));
        card.add(lblPw);
        card.add(Box.createVerticalStrut(4));
        card.add(txtPw);
        card.add(Box.createVerticalStrut(6));
        card.add(lblError);
        card.add(Box.createVerticalStrut(6));
        card.add(buttons);

        root.add(card, new GridBagConstraints());
        dialog.setContentPane(root);
        UIHelper.applyContrast(root);
        dialog.getRootPane().setDefaultButton(btnOk);
        dialog.pack();
        dialog.setMinimumSize(dialog.getSize());
        dialog.setLocationRelativeTo(parent);
        SwingUtilities.invokeLater(txtId::requestFocusInWindow);
        dialog.setVisible(true);
        return result[0];
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.BOLD_BODY_FONT);
        l.setForeground(Theme.TEXT_DARK);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
