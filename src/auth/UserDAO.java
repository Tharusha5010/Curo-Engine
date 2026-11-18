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
//Remove password before saving user in session
        StaffMember sessionUser = new StaffMember(
            member.getStaffId(),
            member.getStaffName(),
            member.getRole(),
            member.getContactNumber(),
            "", //Password excluded for session security
            member.getStatus()
        );

        UserSession.setCurrentUser(sessionUser);

        return sessionUser;
    }
    //Add new staff member (Owner only)
    public boolean registerStaff(StaffMember staff) throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        if (!UserSession.isOwner()) {
            throw new UnauthorizedAccessException("Unauthorized: Only an active Owner can register new staff.");
        }
        if (staff == null) {
            throw new IllegalArgumentException("Staff object cannot be null.");
        }
        if (staff.getStaffName() == null || staff.getStaffName().trim().isEmpty()) {
            throw new IllegalArgumentException("Staff name cannot be empty.");
        }
        if (staff.getPassword() == null || staff.getPassword().isEmpty() || staff.getPassword().length() > 50) {
            throw new IllegalArgumentException("Password must be 1 to 50 characters.");
        }

        //Set default role to CASHIER and check valid values
        String role = staff.getRole();
        if (role == null || role.trim().isEmpty()) {
            role = "CASHIER";
        } else {
            role = role.trim().toUpperCase();
            if (!"OWNER".equals(role) && !"CASHIER".equals(role)) {
                throw new IllegalArgumentException("Role must be strictly OWNER or CASHIER.");
            }
        }

}