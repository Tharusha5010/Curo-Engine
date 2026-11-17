package ui;



import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.JTableHeader;



public class UIHelper {

    
    //Style primary action buttons 
    public static void stylePrimaryButton(JButton btn) {
        btn.setBackground(Theme.PRIMARY_BTN);
        btn.setForeground(Color.WHITE);
        btn.setFont(Theme.BOLD_BODY_FONT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 20, 38));


        //Change color on hover
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(Theme.SIDEBAR_ACTIVE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(Theme.PRIMARY_BTN);
            }
        });
    }



    //Style danger/delete buttons
    public static void styleDangerButton(JButton btn) {
        btn.setBackground(Theme.DANGER_BTN);
        btn.setForeground(Color.WHITE);
        btn.setFont(Theme.BOLD_BODY_FONT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 20, 38));
    }



    //Style secondary/outline buttons
    public static void styleSecondaryButton(JButton btn) {
        btn.setBackground(Color.WHITE);
        btn.setForeground(Theme.TEXT_DARK);
        btn.setFont(Theme.BOLD_BODY_FONT);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 15, 6, 15)
        ));
    }



    //style text input feilds 
    public static void styleTextField(JTextField field) {
        field.setFont(Theme.BODY_FONT);
        field.setForeground(Theme.TEXT_DARK);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
    }



    //Style data tables and headers 
    public static void styleTable(JTable table) {
        table.setFont(Theme.BODY_FONT);
        table.setForeground(Theme.TEXT_DARK);
        table.setBackground(Color.WHITE);
        table.setRowHeight(32);
        table.setGridColor(Theme.BORDER_COLOR);
        table.setSelectionBackground(new Color(226, 232, 240));
        table.setSelectionForeground(Theme.TEXT_DARK);
        table.setShowVerticalLines(false);

 
        JTableHeader header = table.getTableHeader();
        header.setBackground(Theme.SIDEBAR_BG);
        header.setForeground(Color.WHITE);
        header.setFont(Theme.BOLD_BODY_FONT);
        header.setPreferredSize(new Dimension(header.getWidth(), 36));
        header.setReorderingAllowed(false);
    }



    //Style white card containers
    public static void styleCardPanel(JPanel panel) {
        panel.setBackground(Theme.CARD_BG);
        Border line = BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1);
        Border padding = BorderFactory.createEmptyBorder(15, 15, 15, 15);
        panel.setBorder(BorderFactory.createCompoundBorder(line, padding));
    }

    
    //Create standardized page title label 
    public static JLabel createPageTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.TITLE_FONT);
        label.setForeground(Theme.TEXT_DARK);
        return label;
    }



    //Display Virtual Thermal Receipt popup with Print/Save-as-PDF button
    @SuppressWarnings("UseSpecificCatch")
    public static void showVirtualThermalReceipt(Component parent, String receiptText, int billId) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Receipt #" + billId, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(380, 520);
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout(10, 10));


        JTextArea receiptArea = new JTextArea(receiptText);
        receiptArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        receiptArea.setEditable(false);
        receiptArea.setBackground(new Color(255, 255, 245)); // Thermal paper tint
        receiptArea.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));


        JScrollPane scrollPane = new JScrollPane(receiptArea);
        dialog.add(scrollPane, BorderLayout.CENTER);


        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton btnPrint = new JButton("Print / Save PDF");
        JButton btnClose = new JButton("Close");
        stylePrimaryButton(btnPrint);
        styleSecondaryButton(btnClose);


        btnPrint.addActionListener(e -> {
            try {
                receiptArea.print(); // Opens system print dialog (supports Microsoft Print to PDF & Thermal Printers)
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Print error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnClose.addActionListener(e -> dialog.dispose());


        btnPanel.add(btnPrint);
        btnPanel.add(btnClose);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        
        dialog.setVisible(true);
    }


}