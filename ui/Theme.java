package ui;



import java.awt.Color;
import java.awt.Font;



public class Theme {

    //Sidebar Colors (Dark Navy / Slate)
    public static final Color SIDEBAR_BG = new Color(24, 34, 47);         // #18222F
    public static final Color SIDEBAR_ACTIVE = new Color(41, 55, 72);     // #293748
    public static final Color SIDEBAR_TEXT = new Color(220, 226, 232);    // #DCE2E8


    //Main Content Area Colors
    public static final Color MAIN_BG = new Color(248, 250, 252);         // #F8FAFC (Background)
    public static final Color CARD_BG = Color.WHITE;                      // #FFFFFF (Cards and Tables)
    public static final Color BORDER_COLOR = new Color(203, 213, 225);    // #CBD5E1 (Outer border of Cards)


    //Text & Button Colors
    public static final Color TEXT_DARK = new Color(15, 23, 42);          // #0F172A
    public static final Color TEXT_MUTED = new Color(100, 116, 139);      // #64748B
    public static final Color PRIMARY_BTN = new Color(24, 34, 47);        //Sidebar (colorful) Buttons 
    public static final Color DANGER_BTN = new Color(220, 38, 38);        //Cancel/Delete buttons (Red)


    //Fonts
    public static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 24);
    public static final Font SUBTITLE_FONT = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font STAT_NUMBER_FONT = new Font("Segoe UI", Font.BOLD, 26);
    public static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font BOLD_BODY_FONT = new Font("Segoe UI", Font.BOLD, 14);
}