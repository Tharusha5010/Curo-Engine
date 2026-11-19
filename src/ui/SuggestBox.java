package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Auto-suggest drop-down for a text box. After the user types {@code minChars} letters it asks the
 * {@link Finder} for matches (on a background thread) and lists them under the box.
 * Up/Down/Enter/Esc work from the keyboard, or click an item. The popup never takes keyboard focus,
 * so typing is never interrupted.
 */
public class SuggestBox<T> {

    public interface Finder<T> {
        List<T> find(String text) throws Exception;
    }

    private static final int MAX_ROWS = 8;
    private static final int ROW_HEIGHT = 30;

    private final JTextField field;
    private final int minChars;
    private final Finder<T> finder;
    private final Function<T, String> labeler;
    private final Consumer<T> onPick;

    private final JPopupMenu popup = new JPopupMenu();
    private final DefaultListModel<T> model = new DefaultListModel<>();
    private final JList<T> list = new JList<>(model);
    private final JScrollPane scroll = new JScrollPane(list);
    private final javax.swing.Timer timer;

    private boolean silent = false;
    private long requestId = 0;

    public SuggestBox(JTextField field, int minChars, Finder<T> finder,
                      Function<T, String> labeler, Consumer<T> onPick) {
        this.field = field;
        this.minChars = minChars;
        this.finder = finder;
        this.labeler = labeler;
        this.onPick = onPick;

        popup.setFocusable(false);
        popup.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1));
        popup.setLayout(new BorderLayout());

        list.setFocusable(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(Theme.BODY_FONT);
        list.setBackground(Color.WHITE);
        list.setForeground(Theme.TEXT_DARK);
        list.setSelectionBackground(Theme.SIDEBAR_ACTIVE);
        list.setSelectionForeground(Color.WHITE);
        list.setFixedCellHeight(ROW_HEIGHT);
        list.setCursor(new Cursor(Cursor.HAND_CURSOR));
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            @SuppressWarnings("unchecked")
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                String text = labeler.apply((T) value);
                super.getListCellRendererComponent(l, text, index, isSelected, false);
                setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
                if (isSelected) {
                    setBackground(Theme.SIDEBAR_ACTIVE);
                    setForeground(UIHelper.readableOn(Theme.SIDEBAR_ACTIVE));
                } else {
                    setBackground(Color.WHITE);
                    setForeground(Theme.TEXT_DARK);
                }
                return this;
            }
        });

        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setFocusable(false);
        scroll.getVerticalScrollBar().setFocusable(false);
        popup.add(scroll, BorderLayout.CENTER);

        // Mouse: hover highlights, click picks
        list.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = indexAt(e);
                if (i >= 0) {
                    list.setSelectedIndex(i);
                }
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                int i = indexAt(e);
                if (i >= 0) {
                    pick(model.get(i));
                }
            }
        });

        timer = new javax.swing.Timer(200, e -> search());
        timer.setRepeats(false);

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                schedule();
            }
        });

        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                hide();
            }
        });

        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e);
            }
        });
    }

    private int indexAt(MouseEvent e) {
        int i = list.locationToIndex(e.getPoint());
        if (i >= 0 && list.getCellBounds(i, i) != null && list.getCellBounds(i, i).contains(e.getPoint())) {
            return i;
        }
        return -1;
    }

    private void handleKey(KeyEvent e) {
        boolean open = popup.isVisible() && model.size() > 0;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_DOWN -> {
                if (open) {
                    int next = (list.getSelectedIndex() + 1) % model.size();
                    list.setSelectedIndex(next);
                    list.ensureIndexIsVisible(next);
                    e.consume();
                } else if (field.getText().trim().length() >= minChars) {
                    timer.stop();
                    search();
                    e.consume();
                }
            }
            case KeyEvent.VK_UP -> {
                if (open) {
                    int prev = list.getSelectedIndex() <= 0 ? model.size() - 1 : list.getSelectedIndex() - 1;
                    list.setSelectedIndex(prev);
                    list.ensureIndexIsVisible(prev);
                    e.consume();
                }
            }
            case KeyEvent.VK_ENTER -> {
                if (open && list.getSelectedIndex() >= 0) {
                    pick(model.get(list.getSelectedIndex()));
                    e.consume();
                }
            }
            case KeyEvent.VK_ESCAPE -> {
                if (open) {
                    hide();
                    e.consume();
                }
            }
            case KeyEvent.VK_TAB -> hide();
            default -> {
            }
        }
    }

    private void schedule() {
        if (silent) {
            return;
        }
        if (field.getText().trim().length() < minChars) {
            timer.stop();
            requestId++;          // ignore any answer still on its way
            hide();
            return;
        }
        timer.restart();
    }

    private void search() {
        final String query = field.getText().trim();
        if (query.length() < minChars) {
            hide();
            return;
        }
        final long id = ++requestId;
        new SwingWorker<List<T>, Void>() {
            @Override
            protected List<T> doInBackground() throws Exception {
                return finder.find(query);
            }

            @Override
            protected void done() {
                try {
                    List<T> hits = get();
                    if (id != requestId || !field.isShowing() || !field.isFocusOwner()) {
                        return;     // user kept typing / moved away
                    }
                    show(hits);
                } catch (Exception ex) {
                    hide();
                }
            }
        }.execute();
    }

    private void show(List<T> hits) {
        if (hits == null || hits.isEmpty()) {
            hide();
            return;
        }
        model.clear();
        for (T hit : hits) {
            model.addElement(hit);
        }
        list.setSelectedIndex(0);
        list.ensureIndexIsVisible(0);

        int rows = Math.min(model.size(), MAX_ROWS);
        int width = Math.max(field.getWidth(), 300);
        scroll.setPreferredSize(new Dimension(width, rows * ROW_HEIGHT + 2));
        popup.pack();
        if (!popup.isVisible()) {
            popup.show(field, 0, field.getHeight());
        } else {
            popup.revalidate();
            popup.repaint();
        }
    }

    private void pick(T item) {
        hide();
        silent = true;
        try {
            onPick.accept(item);
        } finally {
            silent = false;
        }
    }

    public void hide() {
        timer.stop();
        if (popup.isVisible()) {
            popup.setVisible(false);
        }
    }

    /** Changes the text without opening the suggestion list. */
    public void setTextSilently(String text) {
        silent = true;
        try {
            field.setText(text);
        } finally {
            silent = false;
        }
        hide();
    }
}
