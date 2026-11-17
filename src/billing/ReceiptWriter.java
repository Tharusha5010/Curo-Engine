package billing;



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import models.Bill;
import models.BillItem;




public class ReceiptWriter {


    //FILE WRITE (Save formatted thermal receipt to receipts/Bill_<id>.txt)
    public static String saveReceiptToFile(Bill bill) {
        if (bill == null) return null;

        File folder = new File("receipts");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        String filePath = "receipts/Bill_" + bill.getBillId() + ".txt";
        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("==========================================");
            writer.newLine();
            writer.write("              CURO PHARMACY               ");
            writer.newLine();
            writer.write("==========================================");
            writer.newLine();
            writer.write("Bill No    : #" + bill.getBillId());
            writer.newLine();
            writer.write("Date/Time  : " + dateTime);
            writer.newLine();
            writer.write("Cashier ID : " + bill.getCashierId());
            writer.newLine();
            writer.write("------------------------------------------");
            writer.newLine();
            writer.write(String.format("%-18s %4s %8s %9s", "Medicine", "Qty", "Price", "Total"));
            writer.newLine();
            writer.write("------------------------------------------");
            writer.newLine();

            for (BillItem item : bill.getItems()) {
                String name = item.getMedicineName();
                if (name.length() > 18) {
                    name = name.substring(0, 15) + "...";
                }
                writer.write(String.format("%-18s %4d %8.2f %9.2f",
                        name, item.getQuantity(), item.getSoldPrice(), item.getLineTotal()));
                writer.newLine();
            }

            writer.write("------------------------------------------");
            writer.newLine();
            writer.write(String.format("Sub Total       : Rs. %10.2f", bill.getTotalAmount()));
            writer.newLine();
            writer.write(String.format("Discount (5%%)   : Rs. %10.2f", bill.getDiscountAmount()));
            writer.newLine();

            // Print discount reason for marketing purposes (not saved in DB)
            String reason = (bill.getDiscountReason() != null) ? bill.getDiscountReason() : "None";
            writer.write("Discount Reason : " + reason);
            writer.newLine();

            if (bill.isFreeIssue()) {
                writer.write("Billing Type    : FREE ISSUE (OWNER AUTH)");
                writer.newLine();
            }

            writer.write("==========================================");
            writer.newLine();
            writer.write(String.format("NET TOTAL       : Rs. %10.2f", bill.getNetTotal()));
            writer.newLine();
            writer.write("==========================================");
            writer.newLine();
            writer.write("        Thank You! Get Well Soon.         ");
            writer.newLine();

            return filePath;
        } catch (IOException e) {
            System.err.println("Error writing receipt file: " + e.getMessage());
            return null;
        }
    }



    //FILE READ (Read the saved .txt receipt back to display in Virtual Thermal Receipt)
    public static String readReceiptFromFile(String filePath) {
        if (filePath == null) return "Receipt file not found.";
    StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            return "Error reading receipt: " + e.getMessage();
        }
        return content.toString();
    }
}