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
        btn.setForeground(readableOn(Theme.PRIMARY_BTN));
        btn.setFont(Theme.BOLD_BODY_FONT);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 20, 38));
        btn.setMinimumSize(btn.getPreferredSize());


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
        btn.setForeground(readableOn(Theme.DANGER_BTN));
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
        header.setForeground(readableOn(Theme.SIDEBAR_BG));
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


    // =========================================================================
    // Colour contrast: light text on dark backgrounds, dark text on light ones
    // =========================================================================

    private static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    //Relative luminance (WCAG)
    public static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    //Contrast ratio between two colours (1 = none, 21 = black on white)
    public static double contrastRatio(Color a, Color b) {
        double l1 = luminance(a), l2 = luminance(b);
        double hi = Math.max(l1, l2), lo = Math.min(l1, l2);
        return (hi + 0.05) / (lo + 0.05);
    }

    //Text colour that is readable on the given background (light on dark, dark on light)
    public static Color readableOn(Color background) {
        return luminance(background) < 0.4 ? new Color(248, 250, 252) : Theme.TEXT_DARK;
    }

    //Keep a chosen colour if it is readable on the background, otherwise switch to light/dark
    public static Color ensureReadable(Color wanted, Color background) {
        if (wanted != null && contrastRatio(wanted, background) >= 4.5) {
            return wanted;
        }
        return readableOn(background);
    }

    //Real background behind a component (first opaque ancestor)
    private static Color effectiveBackground(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p instanceof JComponent jc && !jc.isOpaque() && !(p instanceof JScrollPane)) {
                continue;
            }
            if (p.getBackground() != null) {
                return p.getBackground();
            }
        }
        return Theme.MAIN_BG;
    }

    //Walk a whole window/panel and fix any text that is hard to read on its background
    public static void applyContrast(Component root) {
        if (root == null) {
            return;
        }
        if (root instanceof JLabel || root instanceof AbstractButton
                || root instanceof javax.swing.text.JTextComponent || root instanceof JList
                || root instanceof JComboBox) {
            Color bg = effectiveBackground(root);
            Color fg = root.getForeground();
            if (root instanceof AbstractButton btn && !(root instanceof JCheckBox) && !(root instanceof JRadioButton)) {
                bg = btn.getBackground();
            }
            root.setForeground(ensureReadable(fg, bg));
            if (root instanceof javax.swing.text.JTextComponent tc) {
                tc.setCaretColor(readableOn(bg));
            }
        }
        if (root instanceof JTable table) {
            JTableHeader header = table.getTableHeader();
            if (header != null) {
                header.setForeground(ensureReadable(header.getForeground(), header.getBackground()));
            }
            table.setForeground(ensureReadable(table.getForeground(), table.getBackground()));
            table.setSelectionForeground(ensureReadable(table.getSelectionForeground(), table.getSelectionBackground()));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyContrast(child);
            }
        }
    }

    // =========================================================================
    // Windows scaling / screen-aware window sizing
    // =========================================================================

    //Usable desktop area (excludes the task bar) in scaled (logical) pixels
    public static Rectangle usableScreen(Component near) {
        GraphicsConfiguration gc = (near != null && near.getGraphicsConfiguration() != null)
                ? near.getGraphicsConfiguration()
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle b = gc.getBounds();
        Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(b.x + in.left, b.y + in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
    }

    //Size a window to the wanted size, but never bigger than the visible screen (any Windows scale)
    public static void fitToScreen(Window w, int wantedW, int wantedH, int minW, int minH) {
        Rectangle area = usableScreen(w);
        int width = Math.min(wantedW, (int) (area.width * 0.96));
        int height = Math.min(wantedH, (int) (area.height * 0.94));
        w.setMinimumSize(new Dimension(Math.min(minW, width), Math.min(minH, height)));
        w.setSize(width, height);
        w.setLocation(area.x + (area.width - width) / 2, area.y + (area.height - height) / 2);
    }


}