package ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.border.Border;

/**
 * Wraps a tab (panel) so it stretches to fill the window when the window is big,
 * and shows scroll bars instead of squeezing/clipping its contents when the window
 * (or a Windows-scaled screen) is small.
 */
public class FitScrollPanel extends JScrollPane {

    private static final long serialVersionUID = 1L;

    public static final int MIN_CONTENT_WIDTH = 760;
    public static final int MIN_CONTENT_HEIGHT = 520;

    private static class View extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        View(JComponent content) {
            super(new BorderLayout());
            setOpaque(false);
            add(content, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, MIN_CONTENT_WIDTH), Math.max(d.height, MIN_CONTENT_HEIGHT));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return orientation == javax.swing.SwingConstants.VERTICAL ? r.height - 40 : r.width - 40;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport && getParent().getWidth() >= getPreferredSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getParent().getHeight() >= getPreferredSize().height;
        }
    }

    public FitScrollPanel(JComponent content) {
        super(new View(content));
        Border none = javax.swing.BorderFactory.createEmptyBorder();
        setBorder(none);
        getViewport().setBackground(Theme.MAIN_BG);
        getVerticalScrollBar().setUnitIncrement(24);
        getHorizontalScrollBar().setUnitIncrement(24);
    }
}
