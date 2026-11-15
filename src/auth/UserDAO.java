package auth;

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

public class UserDAO {

    // Helper: Check if user is owner by ID (used for InventoryDAO integration)
    public boolean isOwner(String staffId) {
        String query = "SELECT role_ FROM staff WHERE staff_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, staffId);
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

    // Helper: Retrieve staff name by ID
    public String getStaffName(String staffId) {
        String query = "SELECT staff_name FROM staff WHERE staff_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, staffId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("staff_name");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving staff name: " + e.getMessage());
        }
        return staffId;
    }

    // Helper: Verify active owner for InventoryDAO calls
    public void verifyActiveOwner(String staffId) throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        if (staffId == null || staffId.trim().isEmpty()) {
            throw new InvalidStaffIdException("Staff ID cannot be empty.");
        }
        if (!isOwner(staffId)) {
            String staffName = getStaffName(staffId);
            throw new UnauthorizedAccessException("Unauthorized Access: " + staffName + " is either not an Owner or their account is inactive.");
        }
    }



    // 1. USER LOGIN
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
            throw new InvalidStaffIdException("Staff ID " + staffId + " is inactive.");
        }

        // Create sanitized StaffMember without password for session & return
        StaffMember sessionUser = new StaffMember(
            member.getStaffId(),
            member.getStaffName(),
            member.getRole(),
            member.getContactNumber(),
            "", // Password excluded for session security
            member.getStatus()
        );

        UserSession.setCurrentUser(sessionUser);
        return sessionUser;
    }



    // 2. REGISTER NEW STAFF (OWNER ONLY)
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

        // Default role to 'CASHIER' if left empty, and validate
        String role = staff.getRole();
        if (role == null || role.trim().isEmpty()) {
            role = "CASHIER";
        } else {
            role = role.toUpperCase();
            if (!"OWNER".equals(role) && !"CASHIER".equals(role)) {
                throw new IllegalArgumentException("Role must be strictly OWNER or CASHIER.");
            }
        }



        // Default status to 'ACTIVE' if left empty, and validate
        String status = staff.getStatus();
        if (status == null || status.trim().isEmpty()) {
            status = "ACTIVE";
        } else {
            status = status.toUpperCase();
            if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
                throw new IllegalArgumentException("Status must be strictly ACTIVE or INACTIVE.");
            }
        }

        if (staff.getContactNumber() == null || !staff.getContactNumber().matches("\\d{10}")) {
            throw new IllegalArgumentException("Contact number must be exactly 10 digits.");
        }

        String newId = staff.getStaffId();
        if (newId == null || newId.trim().isEmpty()) {
            newId = generateNextId(role);
        } else if (findByIdWithPassword(newId) != null) {
            throw new IllegalArgumentException("Staff ID " + newId + " already exists.");
        }

        String sql = "INSERT INTO staff (staff_id, staff_name, role_, contact_number, password_, status_) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, newId);
            ps.setString(2, staff.getStaffName().trim());
            ps.setString(3, role);
            ps.setString(4, staff.getContactNumber());
            ps.setString(5, staff.getPassword());
            ps.setString(6, status);
            return ps.executeUpdate() > 0;
        }
    }



    // 3. UPDATE STAFF ACCOUNT STATUS (OWNER ONLY)
    public boolean updateStaffStatus(String staffId, String newStatus) throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        if (!UserSession.isOwner()) {
            throw new UnauthorizedAccessException("Unauthorized: Only an active Owner can update staff status.");
        }
        if (newStatus == null || (!"ACTIVE".equalsIgnoreCase(newStatus) && !"INACTIVE".equalsIgnoreCase(newStatus))) {
            throw new IllegalArgumentException("Status must be strictly ACTIVE or INACTIVE.");
        }

        String normalizedStatus = newStatus.toUpperCase();
        
        // Safeguard: Prevent currently logged-in Owner from deactivating their own account
        StaffMember currentUser = UserSession.getCurrentUser();
        if (currentUser != null && currentUser.getStaffId().equals(staffId) && "INACTIVE".equals(normalizedStatus)) {
            throw new UnauthorizedAccessException("Safeguard Violation: Owners cannot deactivate their own active account.");
        }

        String sql = "UPDATE staff SET status_ = ? WHERE staff_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, normalizedStatus);
            ps.setString(2, staffId);
            return ps.executeUpdate() > 0;
        }
    }

    // 4. GET ALL STAFF MEMBERS (OWNER ONLY)
    public List<StaffMember> getAllStaff() throws SQLException, UnauthorizedAccessException {
        if (!UserSession.isOwner()) {
            throw new UnauthorizedAccessException("Unauthorized: Only an active Owner can view all staff records.");
        }

        List<StaffMember> list = new ArrayList<>();
        String sql = "SELECT staff_id, staff_name, role_, contact_number, status_ FROM staff ORDER BY staff_id";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                // Exclude passwords by passing ""
                list.add(new StaffMember(
                    rs.getString("staff_id"),
                    rs.getString("staff_name"),
                    rs.getString("role_"),
                    rs.getString("contact_number"),
                    "",
                    rs.getString("status_")
                ));
            }
        }
        return list;
    }

    // 5. CHANGE PASSWORD
    public boolean changePassword(String staffId, String oldPassword, String newPassword) 
            throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        if (staffId == null || staffId.trim().isEmpty() || oldPassword == null || newPassword == null) {
            throw new IllegalArgumentException("Staff ID, old password, and new password must be provided.");
        }

        StaffMember currentUser = UserSession.getCurrentUser();
        boolean isSelf = currentUser != null && currentUser.getStaffId().equals(staffId);
        boolean isOwner = UserSession.isOwner();

        if (!isSelf && !isOwner) {
            throw new UnauthorizedAccessException("Unauthorized: You can only change your own password unless you are an Owner.");
        }

        if (newPassword.isEmpty() || newPassword.length() > 50) {
            throw new IllegalArgumentException("New password must be 1 to 50 characters.");
        }

        String sql = "UPDATE staff SET password_ = ? WHERE staff_id = ? AND password_ = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, newPassword);
            ps.setString(2, staffId);
            ps.setString(3, oldPassword);
            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated == 0) {
                throw new InvalidStaffIdException("Password change failed: Incorrect old password or Staff ID does not exist.");
            }
            return true;
        }
    }


    // 6. ONE-TIME OWNER OVERRIDE VERIFICATION
    public StaffMember verifyOwnerOverride(String staffId, String password) throws SQLException, InvalidStaffIdException {
        if (staffId == null || staffId.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return null;
        }

        StaffMember member = findByIdWithPassword(staffId.trim());
        if (member != null && "OWNER".equalsIgnoreCase(member.getRole()) 
            && "ACTIVE".equalsIgnoreCase(member.getStatus()) 
            && member.getPassword().equals(password)) {
            
            // Return verified Owner object without altering current UserSession
            return new StaffMember(
                member.getStaffId(),
                member.getStaffName(),
                member.getRole(),
                member.getContactNumber(),
                "",
                member.getStatus()
            );
        }
        return null;
    }




    // Helper: Generate next ID (O001, C001...)
    public String generateNextId(String role) throws SQLException {
        String prefix = "OWNER".equalsIgnoreCase(role) ? "O" : "C";
        String sql = "SELECT MAX(staff_id) FROM staff WHERE staff_id LIKE ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, prefix + "[0-9]%");
            try (ResultSet rs = ps.executeQuery()) {
                int next = 1;
                if (rs.next() && rs.getString(1) != null) {
                    next = Integer.parseInt(rs.getString(1).substring(1)) + 1;
                }
                return String.format("%s%03d", prefix, next);
            }
        }
    }

    // Helper: Find by ID with password masked for public calls
    public StaffMember findById(String staffId) throws SQLException {
        StaffMember member = findByIdWithPassword(staffId);
        if (member != null) {
            return new StaffMember(
                member.getStaffId(),
                member.getStaffName(),
                member.getRole(),
                member.getContactNumber(),
                "",
                member.getStatus()
            );
        }
        return null;
    }

    

    // Internal Helper: Find by ID including password for authentication checks
    private StaffMember findByIdWithPassword(String staffId) throws SQLException {
        String sql = "SELECT staff_id, staff_name, role_, contact_number, password_, status_ "
                   + "FROM staff WHERE staff_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, staffId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new StaffMember(
                        rs.getString("staff_id"),
                        rs.getString("staff_name"),
                        rs.getString("role_"),
                        rs.getString("contact_number"),
                        rs.getString("password_"),
                        rs.getString("status_")
                    );
                }
            }
        }
        return null;
    }
}