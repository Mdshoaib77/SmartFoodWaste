package com.foodwaste.gui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.IntConsumer;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.plaf.basic.ComboPopup;

/** Styled building blocks (buttons, inputs, cards, tabs...). Everything is plain Swing + Java2D. */
public final class Skin {
    private Skin() { }

    public static final int CONTROL_H = 38;
    public static final int RADIUS = 10;
    /** Slightly darker than BORDER so form controls stay visible on white cards. */
    public static final Color LINE = new Color(0xCFD6D0);
    public static final Color SOFT_LINE = new Color(0xE6EAE4);

    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** True when the keyboard focus is on c or inside it (used for the "focus ring" of composite inputs). */
    static boolean hasFocusWithin(Component c) {
        Component o = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        return o != null && SwingUtilities.isDescendingFrom(o, c);
    }

    private static boolean installed;

    /** Installs the global defaults (scroll bars, combo boxes, spinners, check boxes, tool tips) and the focus-ring repaint hook. */
    static void install() {
        if (installed) return;
        installed = true;
        javax.swing.UIManager.put("ScrollBarUI", ScrollUI.class.getName());
        javax.swing.UIManager.put("ComboBoxUI", ComboUI.class.getName());
        javax.swing.UIManager.put("SpinnerUI", SpinUI.class.getName());
        javax.swing.UIManager.put("CheckBox.icon", new CheckIcon());
        javax.swing.UIManager.put("RadioButton.icon", new RadioIcon());
        javax.swing.UIManager.put("CheckBox.foreground", Theme.TEXT);
        javax.swing.UIManager.put("RadioButton.foreground", Theme.TEXT);
        javax.swing.UIManager.put("CheckBox.background", Theme.BG);
        javax.swing.UIManager.put("RadioButton.background", Theme.BG);
        javax.swing.UIManager.put("ComboBox.border", new javax.swing.plaf.BorderUIResource(new InputBorder(2, 8, 2, 2)));
        javax.swing.UIManager.put("Spinner.border", new javax.swing.plaf.BorderUIResource(new InputBorder(2, 6, 2, 2)));
        javax.swing.UIManager.put("ToolTip.background", new Color(0x14231B));
        javax.swing.UIManager.put("ToolTip.foreground", Color.WHITE);
        javax.swing.UIManager.put("ToolTip.border", new EmptyBorder(6, 10, 6, 10));
        javax.swing.UIManager.put("ToolTip.font", Theme.BASE.deriveFont(12f));
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", e -> {
            repaintInput((Component) e.getOldValue());
            repaintInput((Component) e.getNewValue());
        });
    }

    private static void repaintInput(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p instanceof JComponent && ((JComponent) p).getBorder() instanceof InputBorder) { p.repaint(); return; }
        }
    }

    // ================================================================== buttons

    /** Rounded button with animated hover, keyboard focus ring and a loading spinner. */
    public static class Btn extends JButton {
        private static final long serialVersionUID = 1L;
        private final Theme.Kind kind;
        private final Icons.Kind icon;
        private float hover;
        private final Timer anim;
        private boolean over, byMouse;
        private int busy;
        private float spin;
        private final Timer spinner;

        Btn(String text, Theme.Kind kind, Icons.Kind icon) {
            super(text);
            this.kind = kind;
            this.icon = icon;
            setFont(Theme.BOLD);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(12, 22, 12, 22));
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released");
            anim = new Timer(15, e -> {
                float target = over ? 1f : 0f;
                hover += (target - hover) * 0.35f;
                if (Math.abs(target - hover) < 0.02f) { hover = target; ((Timer) e.getSource()).stop(); }
                repaint();
            });
            spinner = new Timer(30, e -> { spin = (spin + 14f) % 360f; repaint(); });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; anim.start(); }
                @Override public void mouseExited(MouseEvent e) { over = false; anim.start(); }
                @Override public void mousePressed(MouseEvent e) { byMouse = true; }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent e) { byMouse = false; repaint(); }
                @Override public void focusGained(FocusEvent e) { repaint(); }
            });
        }

        /** Shows a spinner on the button (nested calls are counted); clicks are ignored while loading. */
        public void setBusy(boolean on) {
            busy = Math.max(0, busy + (on ? 1 : -1));
            boolean loading = busy > 0;
            setEnabled(!loading);
            if (loading) spinner.start(); else spinner.stop();
            repaint();
        }

        public boolean isBusy() { return busy > 0; }

        @Override public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) return super.getPreferredSize();
            FontMetrics fm = getFontMetrics(getFont());
            Insets in = getInsets();
            int w = fm.stringWidth(getText()) + in.left + in.right + (icon != null ? 26 : 0);
            return new Dimension(w, fm.getHeight() + in.top + in.bottom);
        }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            ButtonModel m = getModel();
            boolean loading = busy > 0;
            boolean off = !isEnabled() && !loading;
            float h = m.isArmed() ? 1f : hover;
            Color bg, fg, line = null;
            switch (kind) {
                case PRIMARY:
                    bg = mix(Theme.PRIMARY, new Color(0x0F4A25), m.isArmed() ? 0.8f : h * 0.45f); fg = Color.WHITE; break;
                case DANGER_SOLID:
                    bg = mix(Theme.DANGER, new Color(0x7F1414), m.isArmed() ? 0.7f : h * 0.4f); fg = Color.WHITE; break;
                case DANGER:
                    bg = mix(Color.WHITE, new Color(0xFDECEA), h); fg = Theme.DANGER; line = mix(new Color(0xE9B4B4), Theme.DANGER, h); break;
                case GHOST:
                    bg = mix(new Color(0xFFFFFF), new Color(0xE9EEEA), h); fg = mix(Theme.MUTED_DARK, Theme.TEXT, h);
                    if (h == 0f) bg = null; break;
                default:
                    bg = mix(Color.WHITE, new Color(0xF0F5F1), h); fg = Theme.TEXT; line = mix(LINE, Theme.PRIMARY, h * 0.6f); break;
            }
            if (off) { bg = new Color(0xE9EEEA); fg = new Color(0x93A19A); line = null; }
            int w = getWidth(), ht = getHeight();
            if (bg != null) {
                g.setColor(bg);
                g.fill(new RoundRectangle2D.Double(2, 2, w - 4, ht - 4, RADIUS * 2, RADIUS * 2));
            }
            if (line != null) {
                g.setColor(line);
                g.setStroke(new BasicStroke(1f));
                g.draw(new RoundRectangle2D.Double(2.5, 2.5, w - 5, ht - 5, RADIUS * 2, RADIUS * 2));
            }
            if (hasFocus() && !byMouse && isEnabled()) {
                g.setColor(alpha(kind == Theme.Kind.DANGER || kind == Theme.Kind.DANGER_SOLID ? Theme.DANGER : Theme.PRIMARY, 140));
                g.setStroke(new BasicStroke(2f));
                g.draw(new RoundRectangle2D.Double(1, 1, w - 2, ht - 2, RADIUS * 2 + 2, RADIUS * 2 + 2));
            }
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(getText());
            boolean lead = icon != null || loading;
            int total = tw + (lead ? 26 : 0);
            int x = (w - total) / 2;
            if (loading) {
                g.setColor(fg);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new java.awt.geom.Arc2D.Double(x + 1, ht / 2.0 - 8, 16, 16, -spin, 260, java.awt.geom.Arc2D.OPEN));
            } else if (icon != null) {
                Icons.paint(g, icon, x, ht / 2.0 - 9, 18, fg, 1f, 2.0);
            }
            g.setColor(fg);
            g.drawString(getText(), x + (lead ? 26 : 0), (ht - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    // ================================================================== inputs

    /** Rounded 1px border that turns into a green focus ring while the field (or something inside it) has focus. */
    public static final class InputBorder extends AbstractBorder {
        private static final long serialVersionUID = 1L;
        private final Insets in;

        public InputBorder(int top, int left, int bottom, int right) { in = new Insets(top + 2, left + 2, bottom + 2, right + 2); }

        @Override public void paintBorder(Component c, Graphics g0, int x, int y, int w, int h) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            boolean focus = hasFocusWithin(c);
            if (focus) {
                g.setColor(alpha(Theme.PRIMARY, 55));
                g.setStroke(new BasicStroke(3f));
                g.draw(new RoundRectangle2D.Double(x + 1.5, y + 1.5, w - 3, h - 3, RADIUS * 2 + 2, RADIUS * 2 + 2));
                g.setColor(Theme.PRIMARY);
                g.setStroke(new BasicStroke(1.3f));
                g.draw(new RoundRectangle2D.Double(x + 1.7, y + 1.7, w - 3.4, h - 3.4, RADIUS * 2, RADIUS * 2));
            } else {
                g.setColor(c.isEnabled() ? LINE : SOFT_LINE);
                g.setStroke(new BasicStroke(1f));
                g.draw(new RoundRectangle2D.Double(x + 1.5, y + 1.5, w - 3, h - 3, RADIUS * 2, RADIUS * 2));
            }
            g.dispose();
        }

        @Override public Insets getBorderInsets(Component c) { return (Insets) in.clone(); }

        @Override public Insets getBorderInsets(Component c, Insets i) {
            i.set(in.top, in.left, in.bottom, in.right);
            return i;
        }
    }

    static void fillInput(Graphics2D g, JComponent c) {
        aa(g);
        g.setColor(c.isEnabled() ? Color.WHITE : new Color(0xF3F5F3));
        g.fill(new RoundRectangle2D.Double(2, 2, c.getWidth() - 4, c.getHeight() - 4, RADIUS * 2 - 2, RADIUS * 2 - 2));
    }

    /** Single-line text field with optional placeholder and leading icon. */
    public static class Field extends JTextField {
        private static final long serialVersionUID = 1L;
        private String hint;
        private final Icons.Kind lead;

        public Field(int cols, Icons.Kind lead) {
            super(cols);
            this.lead = lead;
            setOpaque(false);
            setBorder(new InputBorder(7, lead != null ? 30 : 10, 7, 10));
            setForeground(Theme.TEXT);
            setCaretColor(Theme.PRIMARY_DARK);
            setSelectionColor(new Color(0xBFE6CB));
            setSelectedTextColor(Theme.TEXT);
        }

        public Field setHint(String h) { hint = h; return this; }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, Math.max(d.height, CONTROL_H));
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            fillInput(g, this);
            g.dispose();
            super.paintComponent(g0);
            Graphics2D g2 = (Graphics2D) g0.create();
            aa(g2);
            if (lead != null) Icons.paint(g2, lead, 11, (getHeight() - 16) / 2.0, 16, Theme.MUTED, 1f, 2.0);
            if (hint != null && getText().isEmpty()) {
                g2.setFont(getFont());
                g2.setColor(new Color(0x8A978F));
                Insets in = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, in.left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    /** Password field with the same look. */
    public static class PwField extends JPasswordField {
        private static final long serialVersionUID = 1L;

        public PwField(int cols) {
            super(cols);
            setOpaque(false);
            setBorder(new InputBorder(7, 10, 7, 10));
            setForeground(Theme.TEXT);
            setCaretColor(Theme.PRIMARY_DARK);
            setSelectionColor(new Color(0xBFE6CB));
            setSelectedTextColor(Theme.TEXT);
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, Math.max(d.height, CONTROL_H));
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            fillInput(g, this);
            g.dispose();
            super.paintComponent(g0);
        }
    }

    /** Scroll pane drawn as a rounded input (used around text areas). */
    public static class RoundedScroll extends JScrollPane {
        private static final long serialVersionUID = 1L;

        public RoundedScroll(Component view) {
            super(view);
            setOpaque(false);
            getViewport().setOpaque(false);
            setBorder(new InputBorder(3, 4, 3, 4));
            if (view instanceof JComponent) ((JComponent) view).setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            fillInput(g, this);
            g.dispose();
            super.paintComponent(g0);
        }
    }

    // ------------------------------------------------------------------ combo box

    /** Flat rounded combo box with a chevron and a clean pop-up list. */
    public static class ComboUI extends BasicComboBoxUI {
        public static ComponentUI createUI(JComponent c) { return new ComboUI(); }

        @Override protected void installDefaults() {
            super.installDefaults();
            comboBox.setOpaque(false);
        }

        @Override protected JButton createArrowButton() {
            JButton b = new JButton() {
                private static final long serialVersionUID = 1L;
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    aa(g);
                    Icons.paint(g, Icons.Kind.CHEVRON_DOWN, getWidth() / 2.0 - 8, getHeight() / 2.0 - 8, 16,
                        comboBox.isEnabled() ? Theme.MUTED_DARK : new Color(0xB5BFB9), 1f, 2.2);
                    g.dispose();
                }
            };
            b.setBorder(null);
            b.setContentAreaFilled(false);
            b.setFocusable(false);
            b.setOpaque(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return b;
        }

        @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { }

        @Override public void paint(Graphics g0, JComponent c) {
            Graphics2D g = (Graphics2D) g0.create();
            fillInput(g, c);
            g.dispose();
            super.paint(g0, c);
        }

        @Override public Dimension getPreferredSize(JComponent c) {
            Dimension d = super.getPreferredSize(c);
            return new Dimension(d.width, Math.max(d.height, CONTROL_H));
        }

        @Override protected javax.swing.ListCellRenderer<Object> createRenderer() { return new ItemRenderer(); }

        @Override protected ComboPopup createPopup() {
            return new BasicComboPopup(comboBox) {
                private static final long serialVersionUID = 1L;
                @Override protected void configurePopup() {
                    super.configurePopup();
                    setBorder(new LineBorder(LINE, 1, false));
                }
            };
        }

        @Override protected javax.swing.ComboBoxEditor createEditor() {
            javax.swing.ComboBoxEditor e = super.createEditor();
            Component ed = e.getEditorComponent();
            if (ed instanceof JTextField) {
                ((JTextField) ed).setBorder(new EmptyBorder(0, 4, 0, 2));
                ((JTextField) ed).setOpaque(false);
            }
            return e;
        }
    }

    static final class ItemRenderer extends javax.swing.DefaultListCellRenderer implements UIResource {
        private static final long serialVersionUID = 1L;

        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            super.getListCellRendererComponent(list, value, index, false, false);
            setFont(Theme.BASE);
            if (index < 0) {
                setOpaque(false);
                setForeground(Theme.TEXT);
                setBorder(new EmptyBorder(6, 4, 6, 4));
            } else {
                setOpaque(true);
                setBorder(new EmptyBorder(8, 12, 8, 12));
                setBackground(sel ? Theme.PRIMARY_LIGHT : Color.WHITE);
                setForeground(sel ? Theme.PRIMARY_DARK : Theme.TEXT);
            }
            return this;
        }
    }

    // ------------------------------------------------------------------ spinner

    public static class SpinUI extends BasicSpinnerUI {
        public static ComponentUI createUI(JComponent c) { return new SpinUI(); }

        private Component arrow(final boolean up) {
            JButton b = new JButton() {
                private static final long serialVersionUID = 1L;
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    aa(g);
                    g.setColor(getModel().isRollover() ? Theme.PRIMARY : Theme.MUTED_DARK);
                    g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
                    if (up) { g.draw(new java.awt.geom.Line2D.Double(cx - 3.5, cy + 1.5, cx, cy - 2)); g.draw(new java.awt.geom.Line2D.Double(cx, cy - 2, cx + 3.5, cy + 1.5)); }
                    else { g.draw(new java.awt.geom.Line2D.Double(cx - 3.5, cy - 1.5, cx, cy + 2)); g.draw(new java.awt.geom.Line2D.Double(cx, cy + 2, cx + 3.5, cy - 1.5)); }
                    g.dispose();
                }
            };
            b.setBorder(null);
            b.setContentAreaFilled(false);
            b.setFocusable(false);
            b.setOpaque(false);
            b.setRolloverEnabled(true);
            b.setPreferredSize(new Dimension(24, 14));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return b;
        }

        @Override protected Component createNextButton() {
            Component b = arrow(true);
            b.setName("Spinner.nextButton");
            installNextButtonListeners(b);
            return b;
        }

        @Override protected Component createPreviousButton() {
            Component b = arrow(false);
            b.setName("Spinner.previousButton");
            installPreviousButtonListeners(b);
            return b;
        }

        @Override protected JComponent createEditor() {
            JComponent e = super.createEditor();
            if (e instanceof JSpinner.DefaultEditor) {
                e.setOpaque(false);
                JFormattedTextField f = ((JSpinner.DefaultEditor) e).getTextField();
                f.setBorder(new EmptyBorder(0, 4, 0, 4));
                f.setOpaque(false);
                f.setForeground(Theme.TEXT);
            }
            return e;
        }

        @Override public void installUI(JComponent c) {
            super.installUI(c);
            c.setOpaque(false);
        }

        @Override public void paint(Graphics g0, JComponent c) {
            Graphics2D g = (Graphics2D) g0.create();
            fillInput(g, c);
            g.dispose();
            super.paint(g0, c);
        }

        @Override public Dimension getPreferredSize(JComponent c) {
            LayoutManager lm = c.getLayout();
            Dimension d = lm == null ? null : lm.preferredLayoutSize(c);
            if (d == null) d = new Dimension(80, CONTROL_H);
            return new Dimension(d.width, Math.max(d.height, CONTROL_H));
        }
    }

    // ------------------------------------------------------------------ scroll bar

    public static class ScrollUI extends BasicScrollBarUI {
        public static ComponentUI createUI(JComponent c) { return new ScrollUI(); }

        private JButton none() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override protected JButton createDecreaseButton(int o) { return none(); }

        @Override protected JButton createIncreaseButton(int o) { return none(); }

        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }

        @Override protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            g.setColor(isDragging ? new Color(0x8FA196) : isThumbRollover() ? new Color(0xAEBBB3) : new Color(0xC9D2CC));
            boolean vert = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            g.fill(new RoundRectangle2D.Double(r.x + (vert ? 3 : 1), r.y + (vert ? 1 : 3), r.width - (vert ? 6 : 2), r.height - (vert ? 2 : 6), 8, 8));
            g.dispose();
        }

        @Override public Dimension getPreferredSize(JComponent c) { return new Dimension(12, 12); }
    }

    // ------------------------------------------------------------------ check box / radio

    public static final class CheckIcon implements Icon, UIResource {
        @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int s = 18, oy = y + (getIconHeight() - s) / 2;
            RoundRectangle2D box = new RoundRectangle2D.Double(x + 1, oy + 1, s - 2, s - 2, 9, 9);
            if (m.isSelected()) {
                g.setColor(b.isEnabled() ? Theme.PRIMARY : new Color(0xB5BFB9));
                g.fill(box);
                Icons.paint(g, Icons.Kind.CHECK, x + 3, oy + 3, 12, Color.WHITE, 1f, 3.0);
            } else {
                g.setColor(b.isEnabled() ? Color.WHITE : new Color(0xF3F5F3));
                g.fill(box);
                g.setColor(m.isRollover() ? Theme.PRIMARY : LINE);
                g.setStroke(new BasicStroke(1.4f));
                g.draw(box);
            }
            if (b.hasFocus()) {
                g.setColor(alpha(Theme.PRIMARY, 110));
                g.setStroke(new BasicStroke(2f));
                g.draw(new RoundRectangle2D.Double(x - 0.5, oy - 0.5, s + 1, s + 1, 11, 11));
            }
            g.dispose();
        }

        @Override public int getIconWidth() { return 24; }

        @Override public int getIconHeight() { return 22; }
    }

    public static final class RadioIcon implements Icon, UIResource {
        @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel m = b.getModel();
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int s = 18, oy = y + (getIconHeight() - s) / 2;
            Ellipse2D ring = new Ellipse2D.Double(x + 1, oy + 1, s - 2, s - 2);
            g.setColor(Color.WHITE);
            g.fill(ring);
            g.setColor(m.isSelected() ? Theme.PRIMARY : m.isRollover() ? Theme.PRIMARY : LINE);
            g.setStroke(new BasicStroke(m.isSelected() ? 2f : 1.4f));
            g.draw(ring);
            if (m.isSelected()) {
                g.setColor(Theme.PRIMARY);
                g.fill(new Ellipse2D.Double(x + 5, oy + 5, s - 10, s - 10));
            }
            if (b.hasFocus()) {
                g.setColor(alpha(Theme.PRIMARY, 110));
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(x - 0.5, oy - 0.5, s + 1, s + 1));
            }
            g.dispose();
        }

        @Override public int getIconWidth() { return 24; }

        @Override public int getIconHeight() { return 22; }
    }

    // ================================================================== cards

    /** White rounded card with a soft layered shadow. The shadow area is part of the border, so layouts need no extra margin. */
    public static class Card extends JPanel {
        private static final long serialVersionUID = 1L;
        static final int SH = 7;
        private final int radius;

        public Card(int radius, int padV, int padH) {
            this.radius = radius;
            setOpaque(false);
            setBackground(Theme.CARD);
            setBorder(new EmptyBorder(SH + padV, SH + padH, SH + padV, SH + padH));
        }

        public static void paintCard(Graphics2D g, int x, int y, int w, int h, int radius, Color fill) {
            aa(g);
            for (int i = SH; i >= 1; i--) {
                g.setColor(new Color(16, 42, 28, 2 + (SH - i) / 3));
                g.fill(new RoundRectangle2D.Double(x + SH - i, y + SH - i + 2, w - 2 * (SH - i), h - 2 * (SH - i) - 2, radius * 2 + i, radius * 2 + i));
            }
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Double(x + SH, y + SH, w - 2 * SH, h - 2 * SH, radius * 2, radius * 2));
            g.setColor(SOFT_LINE);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(x + SH + 0.5, y + SH + 0.5, w - 2 * SH - 1, h - 2 * SH - 1, radius * 2, radius * 2));
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            paintCard(g, 0, 0, getWidth(), getHeight(), radius, getBackground());
            g.dispose();
        }
    }

    /** FlowLayout that wraps onto more rows when the container is too narrow (toolbars, filter rows). */
    public static class WrapLayout extends java.awt.FlowLayout {
        private static final long serialVersionUID = 1L;

        public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override public Dimension preferredLayoutSize(Container t) { return size(t, true); }

        @Override public Dimension minimumLayoutSize(Container t) {
            Dimension d = size(t, false);
            d.width -= getHgap() + 1;
            return d;
        }

        private Dimension size(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                Container c = target;
                while (c.getWidth() == 0 && c.getParent() != null) c = c.getParent();
                int targetWidth = c.getWidth() == 0 ? Integer.MAX_VALUE : c.getWidth();
                Insets in = target.getInsets();
                int extra = in.left + in.right + getHgap() * 2;
                int max = targetWidth - extra;
                Dimension dim = new Dimension(0, 0);
                int rowW = 0, rowH = 0;
                for (int i = 0; i < target.getComponentCount(); i++) {
                    Component m = target.getComponent(i);
                    if (!m.isVisible()) continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowW + d.width > max && rowW > 0) {
                        dim.width = Math.max(dim.width, rowW);
                        if (dim.height > 0) dim.height += getVgap();
                        dim.height += rowH;
                        rowW = 0;
                        rowH = 0;
                    }
                    if (rowW != 0) rowW += getHgap();
                    rowW += d.width;
                    rowH = Math.max(rowH, d.height);
                }
                dim.width = Math.max(dim.width, rowW);
                if (dim.height > 0) dim.height += getVgap();
                dim.height += rowH;
                dim.width += extra;
                dim.height += in.top + in.bottom + getVgap() * 2;
                return dim;
            }
        }
    }

    /** Panel for scroll panes that follows the viewport width (so content wraps / shrinks instead of being cut off). */
    public static final class FitWidth extends JPanel implements javax.swing.Scrollable {
        private static final long serialVersionUID = 1L;

        public FitWidth(java.awt.Component content) {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            add(content, java.awt.BorderLayout.CENTER);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(16, r.height - 32); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ================================================================== segmented tabs

    /** Two-option pill switch (Sign in / Register). */
    public static final class Segmented extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String[] labels;
        private int selected;
        private float pos;
        private final Timer anim;
        private final IntConsumer onChange;

        public Segmented(IntConsumer onChange, String... labels) {
            this.labels = labels;
            this.onChange = onChange;
            setFocusable(true);
            setFont(Theme.BOLD);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            anim = new Timer(15, e -> {
                pos += (selected - pos) * 0.35f;
                if (Math.abs(selected - pos) < 0.01f) { pos = selected; ((Timer) e.getSource()).stop(); }
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int i = Math.min(Segmented.this.labels.length - 1, Math.max(0, e.getX() * Segmented.this.labels.length / Math.max(1, getWidth())));
                    requestFocusInWindow();
                    choose(i);
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prev");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next");
            getActionMap().put("prev", new AbstractAction() {
                private static final long serialVersionUID = 1L;
                @Override public void actionPerformed(ActionEvent e) { choose(Math.max(0, selected - 1)); }
            });
            getActionMap().put("next", new AbstractAction() {
                private static final long serialVersionUID = 1L;
                @Override public void actionPerformed(ActionEvent e) { choose(Math.min(Segmented.this.labels.length - 1, selected + 1)); }
            });
        }

        private void choose(int i) {
            if (i == selected) return;
            setSelected(i);
            if (onChange != null) onChange.accept(i);
        }

        public void setSelected(int i) {
            selected = i;
            anim.start();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(250, 42); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = getWidth(), h = getHeight(), n = labels.length;
            g.setColor(new Color(0xE6ECE7));
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, 24, 24));
            double sw = (w - 8) / (double) n;
            double x = 4 + pos * sw;
            g.setColor(new Color(0, 0, 0, 22));
            g.fill(new RoundRectangle2D.Double(x, 5.5, sw, h - 8, 20, 20));
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Double(x, 4, sw, h - 8, 20, 20));
            if (hasFocus()) {
                g.setColor(alpha(Theme.PRIMARY, 140));
                g.setStroke(new BasicStroke(2f));
                g.draw(new RoundRectangle2D.Double(x, 4, sw, h - 8, 20, 20));
            }
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < n; i++) {
                g.setColor(i == selected ? Theme.PRIMARY_DARK : Theme.MUTED_DARK);
                int tx = (int) (4 + i * sw + (sw - fm.stringWidth(labels[i])) / 2);
                g.drawString(labels[i], tx, (h - fm.getHeight()) / 2 + fm.getAscent());
            }
            g.dispose();
        }
    }

    // ================================================================== responsive grid

    /** Lays children out in as many equal columns as fit (each at least minWidth wide), wrapping to more rows. */
    public static final class ResponsiveGrid implements LayoutManager {
        private final int minWidth, gap, rowHeight;

        public ResponsiveGrid(int minWidth, int gap, int rowHeight) { this.minWidth = minWidth; this.gap = gap; this.rowHeight = rowHeight; }

        private int cols(Container p) {
            Insets in = p.getInsets();
            int w = p.getWidth() - in.left - in.right;
            if (w <= 0) w = 1000;
            int n = Math.max(1, p.getComponentCount());
            int fit = Math.max(1, Math.min(n, (w + gap) / (minWidth + gap)));
            int rows = (n + fit - 1) / fit;
            return (n + rows - 1) / rows;   // balanced: 6 cards become 3 + 3, not 5 + 1
        }

        @Override public Dimension preferredLayoutSize(Container p) {
            int n = p.getComponentCount();
            int rows = n == 0 ? 0 : (n + cols(p) - 1) / cols(p);
            Insets in = p.getInsets();
            return new Dimension(minWidth + in.left + in.right, rows * rowHeight + Math.max(0, rows - 1) * gap + in.top + in.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container p) { return preferredLayoutSize(p); }

        @Override public void layoutContainer(Container p) {
            int n = p.getComponentCount();
            if (n == 0) return;
            int cols = cols(p);
            Insets in = p.getInsets();
            int w = p.getWidth() - in.left - in.right;
            int cw = (w - gap * (cols - 1)) / cols;
            for (int i = 0; i < n; i++) {
                int r = i / cols, c = i % cols;
                p.getComponent(i).setBounds(in.left + c * (cw + gap), in.top + r * (rowHeight + gap), cw, rowHeight);
            }
        }

        @Override public void addLayoutComponent(String name, Component comp) { }

        @Override public void removeLayoutComponent(Component comp) { }
    }

    static AlphaComposite alphaComposite(float a) { return AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, a))); }
}
