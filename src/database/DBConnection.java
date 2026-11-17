package database;



import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;



public class DBConnection {

    
    public static Connection getConnection() {
        Connection conn = null;
        Properties props = new Properties();

        //Load database configuration from external file
        try (FileInputStream fis = new FileInputStream("db.properties")) {
            props.load(fis);

            String url = props.getProperty("db.url");
            String user = props.getProperty("db.user");
            String password = props.getProperty("db.password");

            //Load JDBC Driver
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

            //Get Connection
            conn = DriverManager.getConnection(url, user, password);

        } catch (IOException e) {
            System.err.println("Database config file (db.properties) not found!");
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.err.println("JDBC Driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Failed to establish database connection!");
            e.printStackTrace();
        }

        return conn;
    }
}