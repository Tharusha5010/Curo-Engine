import java.sql.Connection;
import java.sql.SQLException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Main application entry point for Curo Pharmacy Management System.
 * Initializes FlatLaf UI, validates database connectivity, demonstrates BaseDAO runtime polymorphism,
 * and launches the authentication window.
 */
public class Main {

    public static void main(String[] args) {
        // 1. Initialize the look and feel (FlatLaf) before creating any Swing components
        ui.Theme.installLookAndFeel();

        // 2. Verify database connectivity
        Connection conn;
        try {
            conn = database.DBConnection.getConnection();
        } catch (Exception ex) {
            conn = null;
        }

        if (conn == null) {
            JOptionPane.showMessageDialog(
                null,
                "Database Connection Failed!\n\n"
                + "Unable to establish a connection to SQL Server.\n"
                + "Please verify that SQL Server is running and ensure 'db.properties' is correctly configured.\n\n"
                + "Reason: " + database.DBConnection.getLastError(),
                "Database Connection Error",
                JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        } else {
            try {
                conn.close();
            } catch (SQLException ignored) {}
        }

        // 3. Demonstrate Runtime Polymorphism (Dynamic Method Dispatch) using BaseDAO hierarchy
        database.BaseDAO[] daos = {
            new billing.BillingDAO(),
            new inventory.InventoryDAO(),
            new auth.UserDAO()
        };
        for (database.BaseDAO dao : daos) {
            System.out.println("Initialized DAO for table: " + dao.getTableName());
        }

        // 4. Launch Authentication GUI
        SwingUtilities.invokeLater(() -> {
            new auth.LoginFrame().setVisible(true);
        });
    }
}
