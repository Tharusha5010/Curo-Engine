package database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnection {

    // Used only when db.properties does not define db.url
    private static final String DEFAULT_URL =
        "jdbc:sqlserver://localhost:1433;databaseName=curo_db;encrypt=true;trustServerCertificate=true;";

    // Reason for the most recent failure (shown to the user by Main)
    private static volatile String lastError = "";

    public static String getLastError() {
        return lastError;
    }

    // Find db.properties no matter which folder the program is started from
    private static File findPropertiesFile() {
        String[] candidates = {
            "db.properties",
            "curo-pharmacy-system/db.properties",
            "src/db.properties",
            "../db.properties"
        };
        for (String path : candidates) {
            File f = new File(path);
            if (f.isFile()) {
                return f;
            }
        }
        try {
            File codeDir = new File(DBConnection.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File dir = codeDir.isFile() ? codeDir.getParentFile() : codeDir;
            for (int i = 0; i < 3 && dir != null; i++, dir = dir.getParentFile()) {
                File f = new File(dir, "db.properties");
                if (f.isFile()) {
                    return f;
                }
            }
        } catch (URISyntaxException | SecurityException | NullPointerException ignored) {
        }
        return null;
    }

    // Returns an open connection, or null if it fails (see getLastError()).
    public static Connection getConnection() {
        lastError = "";

        File propFile = findPropertiesFile();
        if (propFile == null) {
            lastError = "db.properties file was not found. Place it in the project root folder "
                      + "(next to the 'src' and 'lib' folders).";
            System.err.println(lastError);
            return null;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(propFile)) {
            props.load(fis);
        } catch (IOException e) {
            lastError = "Could not read " + propFile.getAbsolutePath() + ": " + e.getMessage();
            System.err.println(lastError);
            return null;
        }

        String url = props.getProperty("db.url", DEFAULT_URL).trim();
        String user = props.getProperty("db.user", "").trim();
        String password = props.getProperty("db.password", "").trim();

        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            lastError = "SQL Server JDBC driver not found. Add lib/mssql-jdbc.jar to the classpath.";
            System.err.println(lastError);
            return null;
        }

        try {
            DriverManager.setLoginTimeout(15);
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            lastError = e.getMessage();
            System.err.println("Failed to establish database connection using "
                    + propFile.getAbsolutePath() + ": " + e.getMessage());
            return null;
        }
    }
}
