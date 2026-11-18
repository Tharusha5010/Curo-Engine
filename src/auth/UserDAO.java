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
    //Check if staff ID belongs to an owner
    public boolean isOwner(String staffId) {
        if (staffId == null || staffId.trim().isEmpty()) {
            return false;
        }
        String query = "SELECT role_ FROM staff WHERE staff_id = ? AND status_ = 'ACTIVE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, staffId.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return "OWNER".equalsIgnoreCase(rs.getString("role_"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error verifying user role: " + e.getMessage());
        }
        return false;
    }
//Check if staff ID is an active owner
    public void verifyActiveOwner(String staffId) throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        if (staffId == null || staffId.trim().isEmpty()) {
            throw new InvalidStaffIdException("Staff ID cannot be empty.");
        }
        if (!isOwner(staffId)) {
            String staffName = getStaffName(staffId);
            throw new UnauthorizedAccessException("Unauthorized Access: " + staffName + " is either not an Owner or their account is inactive.");
        }
    }
//Log in user and save session
    public StaffMember loginUser(String staffId, String password)
            throws SQLException, InvalidStaffIdException {
        if (staffId == null || staffId.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new InvalidStaffIdException("Staff ID and password cannot be empty.");
        }
        
        StaffMember member = findByIdWithPassword(staffId.trim());

        if (member == null || !member.getPassword().equals(password)) {
            throw new InvalidStaffIdException("Invalid Staff ID or password.");
        }
        if (!"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            throw new InvalidStaffIdException("Staff ID " + staffId.trim() + " is inactive.");
        }
    }

}