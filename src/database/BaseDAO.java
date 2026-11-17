package database;



import java.sql.Connection;
import java.sql.SQLException;



public abstract class BaseDAO {


    //Shared connection validation for all Child DAOs
    protected Connection getValidConnection() throws SQLException {
        Connection conn = DBConnection.getConnection();

        if (conn == null) {
            throw new SQLException ("Database Connection is Null!");
        }

        return conn;
    }

    //Abstract Method each Child DAO must Implement (Runtime Polymorphism)
    public abstract String getTableName();

    
}
