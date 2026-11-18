package auth;

import database.BaseDAO;
import database.DBConnection;
import exceptions.InvalidStaffIdException;
import exceptions.UnauthorizedAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import models.StaffMember;



public class UserDAO extends BaseDAO {

    @Override
    public String getTableName() {
        return "staff";
    }
}