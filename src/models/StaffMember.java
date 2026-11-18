package models;



//Declare Class and Encapsulated Variables

//Declare Class and Encapsulated Variables
public class StaffMember {
    
    private String staffId;      
    private String staffName;
    private String role;          
    private String contactNumber;
    private String password;
    private String status;       


    
    // Object creation & data initialization(when Registering, Updating, Loading)
    public StaffMember() {
    }

    
    public StaffMember(String staffId, String staffName, String role,
                       String contactNumber, String password, String status) {
        this.staffId = staffId;
        this.staffName = staffName;
        this.role = role;
        this.contactNumber = contactNumber;
        this.password = password;
        this.status = status;
    }
