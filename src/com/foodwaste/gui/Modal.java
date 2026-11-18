package com.foodwaste.gui;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/**
 * THE modal / dialog component of the application. Every message, confirmation and small form goes through here:
 * <pre>
 *   Modal.alert(parent, Modal.Type.SUCCESS, "Saved", "Your changes were saved.");   // success / error / warning / info
 *   boolean yes = Modal.confirm(parent, Modal.Type.DELETE, "Delete post?", "This cannot be undone.", "Delete", "Keep");
 *   boolean ok  = Modal.form(parent, "Edit food", "subtitle or null", contentPanel, "Save");
 *   String text = Modal.prompt(parent, "Confirm pickup", "Enter the code", "Code", "", "Confirm");  // null = cancelled
 *   Modal.code(parent, "Pickup code", "Give this code to the receiver.", "123456");
 *   Modal.toast(parent, Modal.Type.INFO, "Select a row first.");                       // non-blocking
 * </pre>
 * The calls block like JOptionPane did (they return after the user answered), so callers keep the same flow.
 * Look: centred card, dimmed and blurred backdrop, animated icon, focus kept inside, Esc cancels,
 * Enter confirms (never on a delete), a click on the backdrop closes plain messages.
 */
public final class Modal {
    private Modal() { }

    public enum Type { SUCCESS, ERROR, WARNING, INFO, CONFIRM, DELETE }

    private static final String TITLE = "Smart Food Waste Sharing";

    // ================================================================== public API

    public static void alert(Component parent, Type type, String title, String message) {
        Spec s = new Spec(type, title);
        s.message = message;
        s.okLabel = "OK";
        s.backdropCloses = true;
        present(parent, s);
    }

    /** Returns true when the user pressed the confirm button, false for cancel / Esc. */
    public static boolean confirm(Component parent, Type type, String title, String message, String confirmLabel, String cancelLabel) {
        Spec s = new Spec(type == Type.DELETE ? Type.DELETE : Type.CONFIRM, title);
        s.message = message;
        s.okLabel = confirmLabel;
        s.cancelLabel = cancelLabel;
        s.backdropCloses = type != Type.DELETE;
        s.focusCancel = type == Type.DELETE;
        return present(parent, s);
    }

    /** Shows a form (or any panel) in a modal. Returns true on OK. */
    public static boolean form(Component parent, String title, String subtitle, JComponent content, String okLabel) {
        Spec s = new Spec(Type.CONFIRM, title);
        s.formStyle = true;
        s.message = subtitle;
        s.content = content;
        s.okLabel = okLabel;
        s.cancelLabel = "Cancel";
        return present(parent, s);
    }

    /** Asks for one line of text. Returns the text, or null when cancelled. */
    public static String prompt(Component parent, String title, String message, String label, String initial, String okLabel) {
        Spec s = new Spec(Type.CONFIRM, title);
        s.formStyle = true;
        s.message = message;
        s.okLabel = okLabel;
        s.cancelLabel = "Cancel";
        final Skin.Field f = new Skin.Field(22, null);
        f.setText(initial == null ? "" : initial);
        f.getAccessibleContext().setAccessibleName(label);
        JPanel box = new JPanel(new BorderLayout(0, 6));
        box.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(Theme.SMALL);
        l.setForeground(Theme.MUTED_DARK);
        box.add(l, BorderLayout.NORTH);
        box.add(f, BorderLayout.CENTER);
        s.content = box;
        s.input = f;
        return present(parent, s) ? f.getText() : null;
    }

    /** Large, easy-to-read verification code. */
    public static void code(Component parent, String title, String message, String code) {
        Spec s = new Spec(Type.SUCCESS, title);
        s.message = message;
        s.okLabel = "Done";
        s.code = code;
        present(parent, s);
    }

    /** Small non-blocking message at the bottom of the window. */
    public static void toast(final Component parent, final Type type, final String message) {
        if (!EventQueue.isDispatchThread()) { EventQueue.invokeLater(() -> toast(parent, type, message)); return; }
        JRootPane root = rootOf(parent);
        if (root == null) return;
        Toast.show(root, type, message);
    }

    /** Starts a delayed spinner on the window; call the returned Runnable when the work is finished. */
    public static Runnable busy(Component parent) {
        JRootPane root = rootOf(parent);
        if (root == null) return () -> { };
        return Busy.begin(root);
    }

    // ================================================================== plumbing

    private static final class Spec {
        final Type type;
        final String title;
        String message, okLabel = "OK", cancelLabel, code;
        JComponent content;
        JTextField input;
        boolean formStyle, backdropCloses, focusCancel;
        boolean result;

        Spec(Type t, String title) { this.type = t; this.title = title; }
    }

    /** Root pane of the visible window that owns the parent (or the active window); null when nothing is showing yet. */
    private static JRootPane rootOf(Component parent) {
        JRootPane r = parent == null ? null : SwingUtilities.getRootPane(parent);
        if (r == null) {
            Window w = windowOf(parent);
            if (w instanceof RootPaneContainer) r = ((RootPaneContainer) w).getRootPane();
        }
        return r != null && r.isShowing() ? r : null;
    }

    private static Window windowOf(Component parent) {
        Window w = parent == null ? null : parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        if (w == null) w = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return w;
    }

    private static boolean present(final Component parent, final Spec s) {
        if (!EventQueue.isDispatchThread()) {
            try {
                EventQueue.invokeAndWait(() -> present(parent, s));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return s.result;
        }
        JRootPane root = rootOf(parent);
        if (root != null) presentOverlay(root, s); else presentDialog(windowOf(parent), s);
        return s.result;
    }

    // ---- colours / icons per type

    private static Color tone(Type t) {
        switch (t) {
            case SUCCESS: return new Color(0x16A34A);
            case ERROR: case DELETE: return new Color(0xDC2626);
            case WARNING: return new Color(0xD97706);
            case INFO: return new Color(0x2563EB);
            default: return Theme.PRIMARY;
        }
    }

    private static Color tint(Type t) {
        switch (t) {
            case SUCCESS: return new Color(0xE3F6EA);
            case ERROR: case DELETE: return new Color(0xFDEAEA);
            case WARNING: return new Color(0xFEF3D7);
            case INFO: return new Color(0xE4EEFF);
            default: return Theme.PRIMARY_LIGHT;
        }
    }

    private static Icons.Kind glyph(Type t) {
        switch (t) {
            case SUCCESS: return Icons.Kind.CHECK;
            case ERROR: return Icons.Kind.CROSS;
            case WARNING: return Icons.Kind.WARN;
            case INFO: return Icons.Kind.INFO;
            case DELETE: return Icons.Kind.TRASH;
            default: return Icons.Kind.QUESTION;
        }
    }

    private static String html(String text, int width, boolean center) {
        String safe = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        return "<html><div style='width:" + width + "px;" + (center ? "text-align:center" : "") + "'>" + safe + "</div></html>";
    }

    private static float easeOut(float t) { float u = 1f - t; return 1f - u * u * u; }

    private static float easeOutBack(float t) {
        float c = 1.9f, u = t - 1f;
        return 1f + (c + 1f) * u * u * u + c * u * u;
    }

    /** Runs a 0..1 animation on the UI thread. */
    private static Timer animate(final int ms, final Consumer<Float> step, final Runnable end) {
        final long t0 = System.currentTimeMillis();
        final Timer t = new Timer(12, null);
        t.addActionListener(e -> {
            float p = Math.min(1f, (System.currentTimeMillis() - t0) / (float) ms);
            step.accept(p);
            if (p >= 1f) { t.stop(); if (end != null) end.run(); }
        });
        t.setInitialDelay(0);
        t.start();
        return t;
    }

    // ================================================================== the card

    /** Animated round icon (tinted disc + glyph that draws itself). */
    private static final class Badge extends JComponent {
        private static final long serialVersionUID = 1L;
        private final Type type;
        private float t;

        Badge(Type type) {
            this.type = type;
            setPreferredSize(new Dimension(76, 76));
            setMaximumSize(new Dimension(76, 76));
        }

        void play() {
            animate(520, p -> { t = p; repaint(); }, null);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Skin.aa(g);
            float pop = easeOutBack(Math.min(1f, t * 1.6f));
            double s = 0.45 + 0.55 * pop;
            g.translate(getWidth() / 2.0, getHeight() / 2.0);
            g.scale(s, s);
            g.translate(-getWidth() / 2.0, -getHeight() / 2.0);
            g.setColor(tint(type));
            g.fill(new Ellipse2D.Double(2, 2, 72, 72));
            float draw = Math.max(0f, Math.min(1f, (t - 0.2f) / 0.6f));
            Icons.paint(g, glyph(type), 20, 20, 36, tone(type), draw, 2.2);
            g.dispose();
        }
    }

    private static final class CardPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        static final int SH = 22;
        float anim = 1f;
        final Spec spec;
        final Consumer<Boolean> closer;
        JButton primary, secondary;
        Badge badge;
        boolean closed;

        CardPanel(Spec spec, Consumer<Boolean> closer, int maxContentHeight) {
            this.spec = spec;
            this.closer = closer;
            setOpaque(false);
            setFocusCycleRoot(true);
            setBorder(new EmptyBorder(SH + 26, SH + 28, SH + 24, SH + 28));
            getAccessibleContext().setAccessibleName(spec.title);
            build(maxContentHeight);
            bindKeys();
        }

        private void build(int maxContentHeight) {
            Spec s = spec;
            boolean danger = s.type == Type.DELETE;
            Theme.Kind okKind = danger || s.type == Type.ERROR ? Theme.Kind.DANGER_SOLID : Theme.Kind.PRIMARY;
            primary = Theme.button(s.okLabel, okKind);
            ((JComponent) primary).getAccessibleContext().setAccessibleName(s.okLabel);
            primary.addActionListener(e -> finish(true));
            if (s.cancelLabel != null) {
                secondary = Theme.button(s.cancelLabel, Theme.Kind.SECONDARY);
                secondary.addActionListener(e -> finish(false));
            }
            if (s.formStyle) buildForm(maxContentHeight); else buildAlert();
        }

        private void buildAlert() {
            Spec s = spec;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            badge = new Badge(s.type);
            badge.setAlignmentX(CENTER_ALIGNMENT);
            JLabel title = new JLabel(s.title, SwingConstants.CENTER);
            title.setFont(Theme.BASE.deriveFont(java.awt.Font.BOLD, 19f));
            title.setForeground(Theme.TEXT);
            title.setAlignmentX(CENTER_ALIGNMENT);
            add(badge);
            add(javax.swing.Box.createVerticalStrut(14));
            add(title);
            if (s.message != null && !s.message.isEmpty()) {
                JLabel m = new JLabel(html(s.message, 330, true), SwingConstants.CENTER);
                m.setFont(Theme.BASE.deriveFont(13.5f));
                m.setForeground(Theme.MUTED_DARK);
                m.setAlignmentX(CENTER_ALIGNMENT);
                add(javax.swing.Box.createVerticalStrut(8));
                add(m);
            }
            if (s.code != null) {
                add(javax.swing.Box.createVerticalStrut(16));
                JComponent box = codeBox(s.code);
                box.setAlignmentX(CENTER_ALIGNMENT);
                add(box);
            }
            add(javax.swing.Box.createVerticalStrut(22));
            JPanel row = new JPanel(new GridLayout(1, secondary == null ? 1 : 2, 12, 0));
            row.setOpaque(false);
            if (secondary != null) row.add(secondary);
            row.add(primary);
            row.setAlignmentX(CENTER_ALIGNMENT);
            row.setMaximumSize(new Dimension(Short.MAX_VALUE, 44));
            if (secondary == null) {
                JPanel centre = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
                centre.setOpaque(false);
                primary.setPreferredSize(new Dimension(150, primary.getPreferredSize().height));
                centre.add(primary);
                centre.setAlignmentX(CENTER_ALIGNMENT);
                add(centre);
            } else {
                add(row);
            }
            setPreferredSize(new Dimension(Math.max(440, getPreferredSize().width), getPreferredSize().height));
        }

        private JComponent codeBox(final String code) {
            final String spaced = code.length() == 6 ? code.substring(0, 3) + "  " + code.substring(3) : code;
            return new JComponent() {
                private static final long serialVersionUID = 1L;
                @Override public Dimension getPreferredSize() { return new Dimension(300, 76); }
                @Override public Dimension getMaximumSize() { return getPreferredSize(); }
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    Skin.aa(g);
                    g.setColor(Theme.PRIMARY_LIGHT);
                    g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 20, 20));
                    g.setColor(Skin.alpha(Theme.PRIMARY, 60));
                    g.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 20, 20));
                    g.setFont(Theme.MONO.deriveFont(java.awt.Font.BOLD, 40f));
                    g.setColor(Theme.PRIMARY_DARK);
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(spaced, (getWidth() - fm.stringWidth(spaced)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    g.dispose();
                }
            };
        }

        private void buildForm(int maxContentHeight) {
            Spec s = spec;
            setLayout(new BorderLayout(0, 18));
            JPanel head = new JPanel(new BorderLayout(0, 4));
            head.setOpaque(false);
            JLabel title = new JLabel(s.title);
            title.setFont(Theme.BASE.deriveFont(java.awt.Font.BOLD, 19f));
            title.setForeground(Theme.TEXT);
            head.add(title, BorderLayout.NORTH);
            if (s.message != null && !s.message.isEmpty()) {
                JLabel m = new JLabel(html(s.message, 380, false));
                m.setForeground(Theme.MUTED_DARK);
                head.add(m, BorderLayout.CENTER);
            }
            add(head, BorderLayout.NORTH);
            JComponent body = s.content;
            if (body != null) {
                if (body.getPreferredSize().height > maxContentHeight) {
                    JScrollPane sp = new JScrollPane(body, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                    sp.setBorder(null);
                    sp.setOpaque(false);
                    sp.getViewport().setOpaque(false);
                    sp.getVerticalScrollBar().setUnitIncrement(16);
                    sp.setPreferredSize(new Dimension(body.getPreferredSize().width + 14, maxContentHeight));
                    body = sp;
                }
                add(body, BorderLayout.CENTER);
            }
            JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            row.setOpaque(false);
            if (secondary != null) row.add(secondary);
            row.add(primary);
            add(row, BorderLayout.SOUTH);
            setPreferredSize(new Dimension(Math.max(460, getPreferredSize().width), getPreferredSize().height));
        }

        private void bindKeys() {
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "modal.cancel");
            getActionMap().put("modal.cancel", new AbstractAction() {
                private static final long serialVersionUID = 1L;
                @Override public void actionPerformed(ActionEvent e) { finish(false); }
            });
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "modal.enter");
            getActionMap().put("modal.enter", new AbstractAction() {
                private static final long serialVersionUID = 1L;
                @Override public void actionPerformed(ActionEvent e) {
                    Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    if (f instanceof JButton) ((JButton) f).doClick();
                    else if (!spec.focusCancel) primary.doClick();
                }
            });
            if (spec.input != null) spec.input.addActionListener(e -> primary.doClick());
        }

        void finish(boolean ok) {
            if (closed) return;
            closed = true;
            spec.result = ok;
            closer.accept(ok);
        }

        /** Component that should receive the keyboard focus when the card appears. */
        JComponent initialFocus() {
            if (spec.focusCancel && secondary != null) return secondary;
            if (spec.formStyle && spec.content != null) {
                JComponent c = firstInput(spec.content);
                if (c != null) return c;
            }
            return primary;
        }

        private static JComponent firstInput(Container c) {
            for (Component k : c.getComponents()) {
                if (k instanceof javax.swing.text.JTextComponent && k.isEnabled() && k.isFocusable()) return (JComponent) k;
                if (k instanceof javax.swing.JComboBox || k instanceof javax.swing.JSpinner) return (JComponent) k;
                if (k instanceof Container) {
                    JComponent r = firstInput((Container) k);
                    if (r != null) return r;
                }
            }
            return null;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Skin.aa(g);
            int w = getWidth(), h = getHeight();
            for (int i = SH; i >= 1; i--) {
                g.setColor(new Color(8, 30, 18, 3 + (SH - i) / 3));
                g.fill(new RoundRectangle2D.Double(SH - i, SH - i + 8, w - 2 * (SH - i), h - 2 * (SH - i) - 8, 44 + i, 44 + i));
            }
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Double(SH, SH, w - 2 * SH, h - 2 * SH, 44, 44));
            g.dispose();
        }

        @Override public void paint(Graphics g0) {
            if (anim >= 1f) { super.paint(g0); return; }
            Graphics2D g = (Graphics2D) g0.create();
            float p = Math.max(0f, anim);
            g.setComposite(AlphaComposite.SrcOver.derive(p));
            double sc = 0.94 + 0.06 * p;
            g.translate(getWidth() / 2.0, getHeight() / 2.0 + (1 - p) * 10);
            g.scale(sc, sc);
            g.translate(-getWidth() / 2.0, -getHeight() / 2.0);
            super.paint(g);
            g.dispose();
        }
    }

    // ================================================================== overlay host (inside the window)

    private static final String OVERLAY_KEY = "sfw.modal.overlay";

    private static final class Overlay extends JPanel {
        private static final long serialVersionUID = 1L;
        final JRootPane root;
        final Component oldGlass;
        final Deque<CardPanel> stack = new ArrayDeque<>();
        BufferedImage backdrop;
        float dim;
        Component lastFocus;

        Overlay(JRootPane root) {
            super(new GridBagLayout());
            this.root = root;
            this.oldGlass = root.getGlassPane();
            setOpaque(false);
            MouseAdapter eat = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    CardPanel top = stack.peek();
                    if (top == null) return;
                    java.awt.Rectangle b = top.getBounds();
                    b.grow(-CardPanel.SH, -CardPanel.SH);
                    if (!b.contains(e.getPoint()) && top.spec.backdropCloses) top.finish(false);
                }
            };
            addMouseListener(eat);
            addMouseMotionListener(eat);
            addMouseWheelListener(e -> { });
        }

        void snapshot() {
            int w = root.getWidth(), h = root.getHeight();
            if (w <= 0 || h <= 0) return;
            BufferedImage full = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = full.createGraphics();
            root.getLayeredPane().paint(g);
            g.dispose();
            BufferedImage small = scale(full, Math.max(1, w / 4), Math.max(1, h / 4));
            float[] k = new float[9];
            java.util.Arrays.fill(k, 1f / 9f);
            ConvolveOp op = new ConvolveOp(new Kernel(3, 3, k), ConvolveOp.EDGE_NO_OP, null);
            for (int i = 0; i < 3; i++) small = op.filter(small, null);
            backdrop = scale(small, w, h);
        }

        private static BufferedImage scale(BufferedImage src, int w, int h) {
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            return out;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            if (backdrop != null) {
                g.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, dim)));
                g.drawImage(backdrop, 0, 0, getWidth(), getHeight(), null);
            }
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(10, 28, 18, (int) (120 * Math.min(1f, dim))));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.dispose();
        }
    }

    private static void presentOverlay(final JRootPane root, final Spec spec) {
        Overlay existing = (Overlay) root.getClientProperty(OVERLAY_KEY);
        final boolean first = existing == null;
        final Overlay ov = first ? new Overlay(root) : existing;
        final SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        final int maxContent = Math.max(160, root.getHeight() - 300);
        final CardPanel[] holder = new CardPanel[1];
        holder[0] = new CardPanel(spec, ok -> close(ov, holder[0], loop), maxContent);
        final CardPanel card = holder[0];
        if (first) {
            ov.lastFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            ov.snapshot();
            root.putClientProperty(OVERLAY_KEY, ov);
            root.setGlassPane(ov);
            ov.setVisible(true);
        } else {
            CardPanel prev = ov.stack.peek();
            if (prev != null) ov.remove(prev);
        }
        ov.stack.push(card);
        ov.add(card);
        ov.revalidate();
        ov.repaint();
        card.anim = 0f;
        animate(210, p -> {
            float e = easeOut(p);
            card.anim = e;
            if (first) ov.dim = e;
            ov.repaint();
        }, () -> { card.anim = 1f; ov.repaint(); });
        EventQueue.invokeLater(() -> {
            if (card.badge != null) card.badge.play();
            JComponent f = card.initialFocus();
            f.requestFocusInWindow();
            if (f instanceof javax.swing.text.JTextComponent) ((javax.swing.text.JTextComponent) f).selectAll();
        });
        loop.enter();
    }

    private static void close(final Overlay ov, final CardPanel card, final SecondaryLoop loop) {
        final boolean last = ov.stack.size() == 1;
        animate(140, p -> {
            card.anim = 1f - easeOut(p);
            if (last) ov.dim = 1f - p;
            ov.repaint();
        }, () -> {
            ov.stack.remove(card);
            ov.remove(card);
            if (last) {
                ov.root.putClientProperty(OVERLAY_KEY, null);
                ov.root.setGlassPane(ov.oldGlass);
                ov.oldGlass.setVisible(false);
                Component f = ov.lastFocus;
                if (f != null && f.isShowing() && f.isFocusable()) f.requestFocusInWindow();
            } else {
                CardPanel prev = ov.stack.peek();
                if (prev != null) { ov.add(prev); ov.revalidate(); ov.repaint(); prev.initialFocus().requestFocusInWindow(); }
            }
            ov.root.repaint();
            loop.exit();
        });
    }

    // ================================================================== fallback host (no visible window yet)

    private static void presentDialog(Window owner, final Spec spec) {
        final JDialog d = new JDialog(owner, TITLE, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        d.setIconImage(Icons.appIcon(64));
        CardPanel card = new CardPanel(spec, ok -> d.dispose(), 400);
        JPanel bg = new JPanel(new BorderLayout());
        bg.setBackground(Theme.BG);
        bg.add(card, BorderLayout.CENTER);
        d.setContentPane(bg);
        d.setResizable(false);
        d.pack();
        d.setLocationRelativeTo(owner);
        EventQueue.invokeLater(() -> {
            if (card.badge != null) card.badge.play();
            card.initialFocus().requestFocusInWindow();
        });
        d.setVisible(true);
    }

    // ================================================================== toast

    private static final class Toast extends JComponent {
        private static final long serialVersionUID = 1L;
        private static final String KEY = "sfw.toast";
        private final Type type;
        private final String text;
        private float a;

        Toast(Type type, String text) { this.type = type; this.text = text; }

        static void show(final JRootPane root, Type type, String message) {
            Object old = root.getClientProperty(KEY);
            if (old instanceof Toast) root.getLayeredPane().remove((Toast) old);
            final Toast t = new Toast(type, message);
            Dimension d = t.getPreferredSize();
            final int x = (root.getWidth() - d.width) / 2, y = root.getHeight() - d.height - 36;
            t.setBounds(x, y + 16, d.width, d.height);
            root.getLayeredPane().add(t, JLayeredPane.POPUP_LAYER);
            root.putClientProperty(KEY, t);
            animate(200, p -> { t.a = easeOut(p); t.setLocation(x, y + (int) (16 * (1 - t.a))); t.repaint(); }, () -> {
                Timer hold = new Timer(2800, e -> animate(220, p -> { t.a = 1f - p; t.repaint(); }, () -> {
                    root.getLayeredPane().remove(t);
                    root.getLayeredPane().repaint();
                    if (root.getClientProperty(KEY) == t) root.putClientProperty(KEY, null);
                }));
                hold.setRepeats(false);
                hold.start();
            });
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(Theme.BOLD);
            return new Dimension(Math.min(640, fm.stringWidth(text) + 84) + 2 * Skin.Card.SH, 52 + 2 * Skin.Card.SH - 8);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Skin.aa(g);
            g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, a))));
            int sh = Skin.Card.SH;
            Skin.Card.paintCard(g, 0, 0, getWidth(), getHeight(), 14, new Color(0x14231B));
            g.setColor(tone(type));
            g.fill(new Ellipse2D.Double(sh + 14, getHeight() / 2.0 - 11, 22, 22));
            Icons.paint(g, glyph(type), sh + 18, getHeight() / 2.0 - 7, 14, Color.WHITE, 1f, 2.6);
            g.setFont(Theme.BOLD);
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, sh + 48, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    // ================================================================== busy indicator

    private static final class Busy extends JComponent {
        private static final long serialVersionUID = 1L;
        private static final String KEY = "sfw.busy";
        private int count;
        private float spin;
        private final Timer spinner = new Timer(30, e -> { spin = (spin + 16f) % 360f; repaint(); });
        private Timer delay;
        private final JRootPane root;

        Busy(JRootPane root) { this.root = root; }

        @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleJComponent() { };
            return accessibleContext;
        }

        static Runnable begin(final JRootPane root) {
            Busy b = (Busy) root.getClientProperty(KEY);
            if (b == null) { b = new Busy(root); root.putClientProperty(KEY, b); }
            final Busy busy = b;
            busy.count++;
            if (busy.count == 1) {
                busy.delay = new Timer(350, e -> busy.reveal());
                busy.delay.setRepeats(false);
                busy.delay.start();
            }
            final boolean[] ended = {false};
            return () -> {
                if (ended[0]) return;
                ended[0] = true;
                busy.count = Math.max(0, busy.count - 1);
                if (busy.count == 0) busy.stopShowing();
            };
        }

        private void reveal() {
            if (count == 0 || getParent() != null) return;
            setBounds((root.getWidth() - 56) / 2, 12, 56, 56);
            getAccessibleContext().setAccessibleName("Loading");
            root.getLayeredPane().add(this, JLayeredPane.POPUP_LAYER);
            spinner.start();
            repaint();
        }

        private void stopShowing() {
            if (delay != null) delay.stop();
            spinner.stop();
            if (getParent() != null) {
                Container p = getParent();
                p.remove(this);
                p.repaint();
            }
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Skin.aa(g);
            Skin.Card.paintCard(g, 0, 0, getWidth(), getHeight(), 14, Color.WHITE);
            g.setColor(Theme.PRIMARY);
            g.setStroke(new java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Arc2D.Double(getWidth() / 2.0 - 10, getHeight() / 2.0 - 10, 20, 20, -spin, 250, java.awt.geom.Arc2D.OPEN));
            g.dispose();
        }
    }
}
