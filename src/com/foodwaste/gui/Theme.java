package com.foodwaste.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 * Design tokens (colours, type, spacing) and small factory methods so every screen looks the same.
 * Scale: spacing 4 / 8 / 12 / 16 / 24 / 32, card radius 16, control radius 10, control height 38.
 */
public final class Theme {
    private Theme() { }

    // ---- colour tokens: fresh green primary, warm amber accent, soft neutrals
    public static final Color PRIMARY = new Color(0x15803D);
    public static final Color PRIMARY_DARK = new Color(0x14532D);
    public static final Color PRIMARY_LIGHT = new Color(0xE6F5EB);
    public static final Color ACCENT = new Color(0xF59E0B);
    public static final Color BG = new Color(0xF5F7F4);
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(0xE2E8E3);
    public static final Color TEXT = new Color(0x14231B);
    public static final Color MUTED = new Color(0x66756C);
    public static final Color MUTED_DARK = new Color(0x4A5A51);
    public static final Color DANGER = new Color(0xB91C1C);
    public static final Color AMBER = new Color(0xB45309);
    public static final Color BLUE = new Color(0x1D4ED8);

    // ---- spacing tokens
    public static final int S1 = 4, S2 = 8, S3 = 12, S4 = 16, S5 = 24, S6 = 32;

    // ---- type: one family (Inter, bundled in assets/fonts; falls back to the best installed sans-serif)
    private static final String FAMILY = loadFamily();
    public static final Font BASE = new Font(FAMILY, Font.PLAIN, 13);
    public static final Font BOLD = BASE.deriveFont(Font.BOLD);
    public static final Font SMALL = BASE.deriveFont(Font.BOLD, 12f);
    public static final Font H1 = BASE.deriveFont(Font.BOLD, 24f);
    public static final Font H2 = BASE.deriveFont(Font.BOLD, 16f);
    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private static String loadFamily() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        File[] dirs = {new File("assets/fonts"), codeSourceFonts()};
        for (File dir : dirs) {
            if (dir == null || !dir.isDirectory()) continue;
            for (String name : new String[] {"Inter-Regular.otf", "Inter-Bold.otf"}) {
                File f = new File(dir, name);
                if (!f.isFile()) continue;
                try {
                    ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, f));
                } catch (Exception ignored) { /* use an installed font instead */ }
            }
        }
        Set<String> have = new HashSet<>(Arrays.asList(ge.getAvailableFontFamilyNames()));
        for (String f : new String[] {"Inter", "Segoe UI", "SF Pro Text", "Helvetica Neue", "Noto Sans", "Ubuntu"})
            if (have.contains(f)) return f;
        return Font.SANS_SERIF;
    }

    private static File codeSourceFonts() {
        try {
            File here = new File(Theme.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File root = here.isDirectory() ? here.getParentFile() : here.getParentFile().getParentFile();
            return root == null ? null : new File(root, "assets/fonts");
        } catch (Exception e) {
            return null;
        }
    }

    /** Call once before creating any window. */
    public static void install() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { /* keep default */ }
        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            if (key.toString().endsWith(".font")) UIManager.put(key, BASE);
        }
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("ComboBox.background", Color.WHITE);
        UIManager.put("Spinner.background", Color.WHITE);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollPane.background", BG);
        Skin.install();
    }

    // ------------------------------------------------------------------ labels / panels

    public static JLabel title(String text) {
        JLabel l = new JLabel(text); l.setFont(H1); l.setForeground(TEXT); return l;
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text); l.setFont(H2); l.setForeground(TEXT); return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text); l.setForeground(MUTED); return l;
    }

    /** White rounded card with a soft shadow (the shadow is part of its border). */
    public static JPanel card() { return new Skin.Card(16, 16, 18); }

    public static JPanel transparent(java.awt.LayoutManager lm) {
        JPanel p = new JPanel(lm); p.setOpaque(false); return p;
    }

    /** Small rounded label such as a status or priority chip. */
    public static JComponent pill(final String text, final Color fg, final Color bg) {
        return new JComponent() {
            private static final long serialVersionUID = 1L;
            @Override public Dimension getPreferredSize() {
                FontMetrics fm = getFontMetrics(SMALL);
                return new Dimension(fm.stringWidth(text) + 22, 26);
            }
            @Override public Dimension getMaximumSize() { return getPreferredSize(); }
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Skin.aa(g);
                g.setColor(bg);
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
                g.setFont(SMALL);
                g.setColor(fg);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g.dispose();
            }
        };
    }

    // ------------------------------------------------------------------ inputs

    public static JTextField field(int cols) { return new Skin.Field(cols, null); }

    public static JPasswordField password(int cols) { return new Skin.PwField(cols); }

    /** Gives a combo box / spinner / text field the shared rounded look (combo boxes and spinners also get it globally). */
    public static void styleInput(JComponent c) {
        if (c instanceof javax.swing.JComboBox || c instanceof javax.swing.JSpinner) {
            c.setOpaque(false);
        } else if (!(c.getBorder() instanceof Skin.InputBorder)) {
            c.setBorder(new Skin.InputBorder(7, 10, 7, 10));
            c.setBackground(Color.WHITE);
            c.setForeground(TEXT);
        }
    }

    public static JScrollPane textAreaScroll(javax.swing.JTextArea area) {
        area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(6, 8, 6, 8));
        area.setForeground(TEXT);
        return new Skin.RoundedScroll(area);
    }

    // ------------------------------------------------------------------ buttons

    public enum Kind { PRIMARY, SECONDARY, DANGER, GHOST, DANGER_SOLID }

    public static JButton button(String text, Kind kind) { return new Skin.Btn(text, kind, null); }

    public static JButton button(String text, Kind kind, Icons.Kind icon) { return new Skin.Btn(text, kind, icon); }

    // ------------------------------------------------------------------ form helper

    /** Label-above-field grid. add() fills left to right, wrapping after {@code cols} columns. */
    public static final class Form {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final int cols;
        private int col = 0, row = 0;

        public Form(int cols) { this.cols = cols; panel.setOpaque(false); }

        public Form add(String label, JComponent c) { return add(label, c, 1); }

        public Form add(String label, JComponent c, int span) {
            if (col + span > cols) { col = 0; row += 2; }
            JLabel l = new JLabel(label);
            l.setFont(SMALL); l.setForeground(MUTED_DARK);
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = col; g.gridy = row; g.gridwidth = span; g.weightx = 1;
            g.anchor = GridBagConstraints.NORTHWEST; g.fill = GridBagConstraints.HORIZONTAL;
            g.insets = new Insets(0, col == 0 ? 0 : 16, 6, 0);
            panel.add(l, g);
            GridBagConstraints g2 = (GridBagConstraints) g.clone();
            g2.gridy = row + 1;
            g2.insets = new Insets(0, col == 0 ? 0 : 16, 16, 0);
            panel.add(c, g2);
            col += span;
            if (col >= cols) { col = 0; row += 2; }
            return this;
        }

        public JPanel panel() { return panel; }
    }

    // ------------------------------------------------------------------ stat card

    public static final class Stat extends Skin.Card {
        private static final long serialVersionUID = 1L;
        private final JLabel value = new JLabel("-");
        private final Color accent;
        private final Icons.Kind icon;

        public Stat(String label, Color accent) { this(label, accent, Icons.Kind.LIST); }

        public Stat(String label, Color accent, Icons.Kind icon) {
            super(16, 12, 16);
            this.accent = accent;
            this.icon = icon;
            setLayout(new BorderLayout(0, 2));
            JComponent chip = new JComponent() {
                private static final long serialVersionUID = 1L;
                @Override public Dimension getPreferredSize() { return new Dimension(38, 38); }
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    Skin.aa(g);
                    g.setColor(Skin.alpha(Stat.this.accent, 28));
                    g.fill(new Ellipse2D.Double(0, 0, 38, 38));
                    Icons.paint(g, Stat.this.icon, 9, 9, 20, Stat.this.accent, 1f, 2.0);
                    g.dispose();
                }
            };
            JPanel top = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
            top.add(chip);
            value.setFont(BASE.deriveFont(Font.BOLD, 28f));
            value.setForeground(TEXT);
            JLabel l = new JLabel(label);
            l.setForeground(MUTED); l.setFont(BASE.deriveFont(12f));
            JPanel text = transparent(new BorderLayout(0, 0));
            text.add(value, BorderLayout.CENTER);
            text.add(l, BorderLayout.SOUTH);
            add(top, BorderLayout.NORTH);
            add(text, BorderLayout.CENTER);
        }

        public void set(Object v) { value.setText(String.valueOf(v)); }
    }

    /** Stat cards in as many columns as fit the window width. */
    public static JPanel statRow(Stat... stats) {
        JPanel p = transparent(new Skin.ResponsiveGrid(166, 2, 126));
        for (Stat s : stats) p.add(s);
        return p;
    }

    // ------------------------------------------------------------------ numbered steps (dashboard guide)

    /** Card with a short heading and a row of numbered steps. */
    public static JPanel steps(String heading, String... steps) {
        JPanel p = card();
        p.setLayout(new BorderLayout(0, 14));
        p.add(heading(heading), BorderLayout.NORTH);
        JPanel row = transparent(new GridLayout(1, steps.length, 12, 0));
        for (int i = 0; i < steps.length; i++) {
            final int n = i + 1;
            JPanel cell = transparent(new BorderLayout(10, 0));
            JComponent badge = new JComponent() {
                private static final long serialVersionUID = 1L;
                @Override public Dimension getPreferredSize() { return new Dimension(28, 28); }
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    Skin.aa(g);
                    g.setColor(PRIMARY_LIGHT);
                    g.fill(new Ellipse2D.Double(0, 0, 28, 28));
                    g.setFont(BOLD);
                    g.setColor(PRIMARY_DARK);
                    FontMetrics fm = g.getFontMetrics();
                    String s = String.valueOf(n);
                    g.drawString(s, (28 - fm.stringWidth(s)) / 2, (28 - fm.getHeight()) / 2 + fm.getAscent());
                    g.dispose();
                }
            };
            JPanel left = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
            left.add(badge);
            JLabel t = new JLabel(steps[i]);
            t.setFont(BOLD);
            cell.add(left, BorderLayout.WEST);
            cell.add(t, BorderLayout.CENTER);
            row.add(cell);
        }
        p.add(row, BorderLayout.CENTER);
        return p;
    }

    /** Vertical stack of cards at their natural height (used on dashboards). */
    public static JPanel stack(JComponent... items) {
        JPanel p = transparent(null);
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        for (JComponent it : items) {
            it.setAlignmentX(Component.LEFT_ALIGNMENT);
            it.setMaximumSize(new Dimension(Short.MAX_VALUE, it.getPreferredSize().height));
            p.add(it);
        }
        return p;
    }

    // ------------------------------------------------------------------ table page

    /** Card containing a search box, extra action components, a record counter and the table. */
    public static JPanel tablePanel(final ObjTable<?> table, Component... actions) {
        JPanel p = card();
        p.setLayout(new BorderLayout(0, 12));
        JPanel left = transparent(new Skin.WrapLayout(FlowLayout.LEFT, 10, 4));
        final Skin.Field search = new Skin.Field(12, Icons.Kind.SEARCH);
        search.setHint("Search");
        search.getAccessibleContext().setAccessibleName("Search table");
        left.add(search);
        for (Component a : actions) left.add(a);
        final JLabel count = new JLabel("0 records");
        count.setForeground(MUTED);
        count.setFont(BASE.deriveFont(12f));
        JPanel footer = transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.add(count);
        table.getModel().addTableModelListener(e -> {
            int n = table.getModel().getRowCount();
            count.setText(n + (n == 1 ? " record" : " records"));
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            private void apply() {
                @SuppressWarnings("unchecked")
                TableRowSorter<TableModel> rs = (TableRowSorter<TableModel>) table.getRowSorter();
                String s = search.getText().trim();
                rs.setRowFilter(s.isEmpty() ? null : RowFilter.<TableModel, Integer>regexFilter("(?i)" + Pattern.quote(s)));
            }
            @Override public void insertUpdate(DocumentEvent e) { apply(); }
            @Override public void removeUpdate(DocumentEvent e) { apply(); }
            @Override public void changedUpdate(DocumentEvent e) { apply(); }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(new LineBorder(Skin.SOFT_LINE));
        sp.getViewport().setBackground(Color.WHITE);
        p.add(left, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        p.add(footer, BorderLayout.SOUTH);
        return p;
    }
}
