package auth;



import database.BaseDAO;
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
        try (Connection conn = getValidConnection();
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

    //Get staff name by ID
    public String getStaffName(String staffId) {
        if (staffId == null || staffId.trim().isEmpty()) {
            return "";
        }
        String query = "SELECT staff_name FROM staff WHERE staff_id = ?";
        try (Connection conn = getValidConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, staffId.trim());
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



        //Set default status to ACTIVE and check valid values
        String status = staff.getStatus();
        if (status == null || status.trim().isEmpty()) {
            status = "ACTIVE";
        } else {
            status = status.trim().toUpperCase();
            if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
                throw new IllegalArgumentException("Status must be strictly ACTIVE or INACTIVE.");
            }
        }

        if (staff.getContactNumber() == null || !staff.getContactNumber().trim().matches("\\d{10}")) {
            throw new IllegalArgumentException("Contact number must be exactly 10 digits.");
        }

        String newId = staff.getStaffId();
        if (newId == null || newId.trim().isEmpty()) {
            newId = generateNextId(role);
        } else {
            newId = newId.trim().toUpperCase();
            if (findByIdWithPassword(newId) != null) {
                throw new IllegalArgumentException("Staff ID " + newId + " already exists.");
            }
        }

        String sql = "INSERT INTO staff (staff_id, staff_name, role_, contact_number, password_, status_) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = getValidConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, newId);
                ps.setString(2, staff.getStaffName().trim());
                ps.setString(3, role);
                ps.setString(4, staff.getContactNumber().trim());
                ps.setString(5, staff.getPassword());
                ps.setString(6, status);
                return ps.executeUpdate() > 0;
        }
    }

    


    //Change staff account status to ACTIVE or INACTIVE (Owner only)
    public boolean updateStaffStatus(String staffId, String newStatus) throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        if (!UserSession.isOwner()) {
            throw new UnauthorizedAccessException("Unauthorized: Only an active Owner can update staff status.");
        }
        if (staffId == null || staffId.trim().isEmpty()) {
            throw new InvalidStaffIdException("Staff ID cannot be empty.");
        }
        if (newStatus == null || (!"ACTIVE".equalsIgnoreCase(newStatus.trim()) && !"INACTIVE".equalsIgnoreCase(newStatus.trim()))) {
            throw new IllegalArgumentException("Status must be strictly ACTIVE or INACTIVE.");
        }

        String normalizedStatus = newStatus.trim().toUpperCase();
        String trimmedId = staffId.trim();
        
        //Stop owner from deactivating their own account
        StaffMember currentUser = UserSession.getCurrentUser();
        if (currentUser != null && currentUser.getStaffId().equalsIgnoreCase(trimmedId) && "INACTIVE".equals(normalizedStatus)) {
            throw new UnauthorizedAccessException("Safeguard Violation: Owners cannot deactivate their own active account.");
        }

        String sql = "UPDATE staff SET status_ = ? WHERE staff_id = ?";
        try (Connection conn = getValidConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, normalizedStatus);
                ps.setString(2, trimmedId);
                return ps.executeUpdate() > 0;
            }
        }
    

    //Get all staff records (Owner only)
    public List<StaffMember> getAllStaff() throws SQLException, UnauthorizedAccessException {
        if (!UserSession.isOwner()) {
            throw new UnauthorizedAccessException("Unauthorized: Only an active Owner can view all staff records.");
        }

        List<StaffMember> list = new ArrayList<>();
        String sql = "SELECT staff_id, staff_name, role_, contact_number, status_ FROM staff ORDER BY staff_id";

        try (Connection conn = getValidConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                //Hide password by passing empty string
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

    //Update staff password
    public boolean changePassword(String staffId, String oldPassword, String newPassword) 
            throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        if (staffId == null || staffId.trim().isEmpty() || oldPassword == null || newPassword == null) {
            throw new IllegalArgumentException("Staff ID, old password, and new password must be provided.");
        }

        String trimmedId = staffId.trim();
        StaffMember currentUser = UserSession.getCurrentUser();
        boolean isSelf = currentUser != null && currentUser.getStaffId().equalsIgnoreCase(trimmedId);
        boolean isOwner = UserSession.isOwner();

        if (!isSelf && !isOwner) {
            throw new UnauthorizedAccessException("Unauthorized: You can only change your own password unless you are an Owner.");
        }

        if (newPassword.isEmpty() || newPassword.length() > 50) {
            throw new IllegalArgumentException("New password must be 1 to 50 characters.");
        }

        String sql = "UPDATE staff SET password_ = ? WHERE staff_id = ? AND password_ = ?";
        try (Connection conn = getValidConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newPassword);
                ps.setString(2, trimmedId);
                ps.setString(3, oldPassword);
                int rowsUpdated = ps.executeUpdate();
                if (rowsUpdated == 0) {
                    throw new InvalidStaffIdException("Password change failed: Incorrect old password or Staff ID does not exist.");
                }
                return true;
        }
    }

    

    //Verify owner login for one-time action approval
    public StaffMember verifyOwnerOverride(String staffId, String password) throws SQLException, InvalidStaffIdException {
        if (staffId == null || staffId.trim().isEmpty() || password == null || password.isEmpty()) {
            return null;
        }

        StaffMember member = findByIdWithPassword(staffId.trim());
        if (member != null && "OWNER".equalsIgnoreCase(member.getRole()) 
            && "ACTIVE".equalsIgnoreCase(member.getStatus()) 
            && member.getPassword().equals(password)) {
            
            //Return owner details without changing current login session
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




    //Create next staff ID (O001, C001...)
    public String generateNextId(String role) throws SQLException {
        String prefix = "OWNER".equalsIgnoreCase(role) ? "O" : "C";
        String sql = "SELECT MAX(staff_id) FROM staff WHERE staff_id LIKE ?";

        try (Connection conn = getValidConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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

    //Find staff member by ID without password
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

    

    //Find staff member by ID including password for login check
    private StaffMember findByIdWithPassword(String staffId) throws SQLException {
        String sql = "SELECT staff_id, staff_name, role_, contact_number, password_, status_ "
                   + "FROM staff WHERE staff_id = ?";

        try (Connection conn = getValidConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
