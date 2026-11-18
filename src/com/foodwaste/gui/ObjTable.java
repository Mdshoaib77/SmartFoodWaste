package com.foodwaste.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

/** Read-only, sortable table that keeps the model objects so a selected row can be turned back into an object. */
public class ObjTable<T> extends JTable {
    private static final long serialVersionUID = 1L;

    private static final Map<String, Color[]> CHIPS = new HashMap<>();
    static {
        chip("High", 0xB91C1C, 0xFDE8E8);      chip("Medium", 0xB45309, 0xFEF0D4);   chip("Low", 0x15803D, 0xE3F5EA);
        chip("Available", 0x15803D, 0xE3F5EA); chip("Requested", 0x1D4ED8, 0xE3ECFE); chip("Reserved", 0x6D28D9, 0xEEE8FD);
        chip("Picked Up", 0x0F766E, 0xDDF4F1); chip("Completed", 0x334155, 0xE8ECF1); chip("Expired", 0xB91C1C, 0xFDE8E8);
        chip("Rejected", 0xB91C1C, 0xFDE8E8);  chip("Cancelled", 0x57636D, 0xE9EDF0); chip("Pending", 0xB45309, 0xFEF0D4);
        chip("Approved", 0x15803D, 0xE3F5EA);  chip("SUSPICIOUS", 0xB91C1C, 0xFDE8E8);
        chip("TOO LARGE", 0xB91C1C, 0xFDE8E8); chip("Fits", 0x15803D, 0xE3F5EA);   chip("NEW", 0x1D4ED8, 0xE3ECFE);
    }

    private static void chip(String k, int fg, int bg) { CHIPS.put(k, new Color[] {new Color(fg), new Color(bg)}); }

    private final List<T> items = new ArrayList<>();
    private final Function<T, Object[]> mapper;
    private final DefaultTableModel model;

    public ObjTable(String[] columns, Function<T, Object[]> mapper) {
        super(new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        this.model = (DefaultTableModel) getModel();
        this.mapper = mapper;
        setRowHeight(42);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        setFillsViewportHeight(true);
        setAutoCreateRowSorter(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setAutoResizeMode(AUTO_RESIZE_ALL_COLUMNS);
        setSelectionBackground(new Color(0xDDF1E3));
        setSelectionForeground(Theme.TEXT);
        setDefaultRenderer(Object.class, new CellRenderer());
        setFocusable(true);
        getAccessibleContext().setAccessibleName("Data table");
        MouseAdapter hoverTracker = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { setHover(rowAtPoint(e.getPoint())); }
            @Override public void mouseExited(MouseEvent e) { setHover(-1); }
        };
        addMouseMotionListener(hoverTracker);
        addMouseListener(hoverTracker);

        JTableHeader h = getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(100, 40));
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, false, false, r, c);
                l.setBackground(new Color(0xF4F7F4));
                l.setForeground(Theme.MUTED_DARK);
                l.setFont(Theme.SMALL);
                l.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, Skin.SOFT_LINE), new EmptyBorder(0, 10, 0, 8)));
                return l;
            }
        });
    }

    private int hoverRow = -1;

    private void setHover(int r) {
        if (r == hoverRow) return;
        hoverRow = r;
        repaint();
    }

    public void setItems(List<T> list) {
        items.clear();
        items.addAll(list);
        model.setRowCount(0);
        for (T t : list) model.addRow(mapper.apply(t));
        packColumns();
    }

    /** The object behind the selected row, or null when nothing is selected. */
    public T selected() {
        int r = getSelectedRow();
        return r < 0 ? null : items.get(convertRowIndexToModel(r));
    }

    private void packColumns() {
        for (int c = 0; c < getColumnCount(); c++) {
            TableColumn col = getColumnModel().getColumn(c);
            int w = getTableHeader().getDefaultRenderer()
                .getTableCellRendererComponent(this, col.getHeaderValue(), false, false, -1, c).getPreferredSize().width + 16;
            for (int r = 0; r < Math.min(getRowCount(), 200); r++)
                w = Math.max(w, prepareRenderer(getCellRenderer(r, c), r, c).getPreferredSize().width + 4);
            col.setPreferredWidth(Math.min(Math.max(w, 60), 280));
        }
    }

    /** Stretch to the window when there is room, scroll sideways when there are many columns. */
    @Override public boolean getScrollableTracksViewportWidth() {
        return getParent() == null || getPreferredSize().width < getParent().getWidth();
    }

    /** Friendly empty state: icon and one short line. */
    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (getRowCount() > 0) return;
        Graphics2D g = (Graphics2D) g0.create();
        Skin.aa(g);
        java.awt.Rectangle vr = getVisibleRect();
        String msg = model.getRowCount() > 0 ? "No matches" : "Nothing here yet";
        int cx = vr.x + vr.width / 2, cy = vr.y + Math.max(70, vr.height / 2 - 10);
        Icons.paint(g, Icons.Kind.INBOX, cx - 20, cy - 44, 40, new Color(0xB9C5BD), 1f, 1.6);
        g.setFont(Theme.BASE);
        g.setColor(Theme.MUTED);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, cx - fm.stringWidth(msg) / 2, cy + 20);
        g.dispose();
    }

    private final class CellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private Color[] chip;
        private Color rowBg = Color.WHITE;

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, false, r, c);
            setBorder(new EmptyBorder(0, 10, 0, 8));
            setForeground(Theme.TEXT);
            setFont(Theme.BASE);
            rowBg = sel ? getSelectionBackground() : r == hoverRow ? new Color(0xF1F7F2) : r % 2 == 0 ? Color.WHITE : new Color(0xFAFBFA);
            setBackground(rowBg);
            chip = v == null ? null : CHIPS.get(v.toString());
            if (chip != null) {
                setForeground(chip[0]);
                setFont(Theme.SMALL);
            }
            setToolTipText(v == null ? null : v.toString());
            return this;
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return chip != null ? new Dimension(d.width + 10, d.height) : d;
        }

        @Override protected void paintComponent(Graphics g0) {
            if (chip == null) { super.paintComponent(g0); return; }
            Graphics2D g = (Graphics2D) g0.create();
            Skin.aa(g);
            g.setColor(rowBg);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            String text = getText();
            int w = fm.stringWidth(text) + 20, h = 24;
            int x = 4, y = (getHeight() - h) / 2;
            g.setColor(chip[1]);
            g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
            g.setColor(chip[0]);
            g.drawString(text, x + 10, y + (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }
}
