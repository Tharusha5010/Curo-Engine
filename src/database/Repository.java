package database;



import java.sql.SQLException;
import java.util.List;



public interface  Repository <T, ID> {


    boolean save (T entity) throws Exception;
    T findById( ID id ) throws SQLException;
    List <T> findAll () throws SQLException;   

}
