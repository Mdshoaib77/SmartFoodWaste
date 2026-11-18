package com.foodwaste.gui;

import com.foodwaste.service.ExpiryService;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/** Window skeleton shared by the Donor, Receiver/NGO and Administrator dashboards: header, sidebar, pages. */
public abstract class BaseFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final Map<String, Icons.Kind> NAV_ICONS = new HashMap<>();
    static {
        NAV_ICONS.put("dash", Icons.Kind.DASH);       NAV_ICONS.put("post", Icons.Kind.PLUS);
        NAV_ICONS.put("posts", Icons.Kind.LIST);      NAV_ICONS.put("reqs", Icons.Kind.INBOX);
        NAV_ICONS.put("near", Icons.Kind.USERS);      NAV_ICONS.put("msgs", Icons.Kind.MAIL);
        NAV_ICONS.put("pwd", Icons.Kind.LOCK);        NAV_ICONS.put("browse", Icons.Kind.SEARCH);
        NAV_ICONS.put("pending", Icons.Kind.SHIELD);  NAV_ICONS.put("users", Icons.Kind.USERS);
        NAV_ICONS.put("food", Icons.Kind.BOWL);       NAV_ICONS.put("msg", Icons.Kind.SEND);
        NAV_ICONS.put("admins", Icons.Kind.USER);     NAV_ICONS.put("logs", Icons.Kind.ACTIVITY);
        NAV_ICONS.put("report", Icons.Kind.FILE);
    }

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel nav = new JPanel(new GridLayout(0, 1, 0, 4));
    private final JLabel pageTitle = Theme.title("");
    private final Map<String, Runnable> loaders = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private final Map<String, NavButton> buttons = new HashMap<>();
    private String current;

    protected BaseFrame(String roleLabel, String userLine) {
        super("Smart Food Waste Sharing - " + roleLabel);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(Icons.appIcon(64));
        setSize(1200, 740);
        setMinimumSize(new Dimension(1000, 620));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // ---- header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(new javax.swing.border.CompoundBorder(new MatteBorder(0, 0, 1, 0, Skin.SOFT_LINE), new EmptyBorder(10, 22, 10, 18)));
        JPanel left = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.add(new Logo());
        JLabel brand = new JLabel("Smart Food Waste Sharing");
        brand.setFont(Theme.H2);
        brand.setForeground(Theme.TEXT);
        left.add(brand);
        left.add(Theme.pill(roleLabel.toUpperCase(), Theme.PRIMARY_DARK, Theme.PRIMARY_LIGHT));
        JButton logout = Theme.button("Logout", Theme.Kind.GHOST, Icons.Kind.LOGOUT);
        logout.getAccessibleContext().setAccessibleName("Log out");
        logout.addActionListener(e -> logout());
        JPanel right = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.add(new UserChip(userLine));
        right.add(logout);
        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        // ---- sidebar
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Color.WHITE);
        side.setBorder(new MatteBorder(0, 0, 0, 1, Skin.SOFT_LINE));
        side.setPreferredSize(new Dimension(236, 10));
        nav.setOpaque(false);
        nav.setBorder(new EmptyBorder(16, 12, 12, 12));
        side.add(nav, BorderLayout.NORTH);

        // ---- main area
        JButton refresh = Theme.button("Refresh", Theme.Kind.SECONDARY, Icons.Kind.REFRESH);
        refresh.addActionListener(e -> { if (current != null) open(current); });
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(pageTitle, BorderLayout.WEST);
        titleRow.add(refresh, BorderLayout.EAST);
        content.setOpaque(false);
        JPanel main = new JPanel(new BorderLayout(0, 12));
        main.setBorder(new EmptyBorder(20, 24, 14, 24));
        main.add(titleRow, BorderLayout.NORTH);
        main.add(content, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(side, BorderLayout.WEST);
        add(main, BorderLayout.CENTER);
    }

    /** Registers a page: key, sidebar label, the component and a loader that fills it with fresh data. */
    protected void addPage(final String key, String label, JComponent page, Runnable loader) {
        content.add(page, key);
        loaders.put(key, loader);
        labels.put(key, label);
        NavButton b = new NavButton(label, NAV_ICONS.get(key));
        b.addActionListener(e -> open(key));
        buttons.put(key, b);
        nav.add(b);
    }

    /** Shows a page, runs the expiry check (FR-09) and then reloads that page's data. */
    protected void open(final String key) {
        current = key;
        cards.show(content, key);
        pageTitle.setText(labels.get(key));
        for (Map.Entry<String, NavButton> e : buttons.entrySet()) e.getValue().setSelectedPage(e.getKey().equals(key));
        Ui.run(this, ExpiryService::sync, n -> {
            loaders.get(key).run();
            afterOpen();
        });
    }

    /** Hook run after every page load (dashboards use it to refresh the unread-message badge). */
    protected void afterOpen() { }

    /** Changes a sidebar label (and the page title when that page is showing), e.g. "Messages (3)". */
    protected void setNavLabel(String key, String label) {
        labels.put(key, label);
        NavButton b = buttons.get(key);
        if (b != null) b.setText(label);
        if (key.equals(current)) pageTitle.setText(label);
    }

    private void logout() {
        new LoginFrame().setVisible(true);
        dispose();
    }

    // ------------------------------------------------------------------ header pieces

    private static final class Logo extends JComponent {
        private static final long serialVersionUID = 1L;
        Logo() { setPreferredSize(new Dimension(36, 36)); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Skin.aa(g);
            g.drawImage(Icons.appIcon(72), 0, 0, 36, 36, null);
            g.dispose();
        }
    }

    /** Avatar with the first letter, the name and a muted detail line (area, capacity...). */
    private static final class UserChip extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String name, detail;

        UserChip(String userLine) {
            String[] parts = userLine.split("\\s*\\|\\s*");
            name = parts[0].trim();
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length; i++) sb.append(i > 1 ? "  \u00B7  " : "").append(parts[i].trim());
            detail = sb.toString();
            getAccessibleContext().setAccessibleName(userLine);
        }

        @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleJComponent() { };
            return accessibleContext;
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics a = getFontMetrics(Theme.BOLD), b = getFontMetrics(Theme.BASE.deriveFont(12f));
            return new Dimension(52 + Math.max(a.stringWidth(name), b.stringWidth(detail)), 38);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Skin.aa(g);
            g.setColor(Theme.PRIMARY_LIGHT);
            g.fill(new Ellipse2D.Double(0, 1, 36, 36));
            g.setFont(Theme.BOLD);
            g.setColor(Theme.PRIMARY_DARK);
            FontMetrics fm = g.getFontMetrics();
            String ini = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            g.drawString(ini, (36 - fm.stringWidth(ini)) / 2, 1 + (36 - fm.getHeight()) / 2 + fm.getAscent());
            int x = 46;
            if (detail.isEmpty()) {
                g.setColor(Theme.TEXT);
                g.drawString(name, x, (38 - fm.getHeight()) / 2 + fm.getAscent());
            } else {
                g.setColor(Theme.TEXT);
                g.drawString(name, x, 3 + fm.getAscent());
                g.setFont(Theme.BASE.deriveFont(12f));
                g.setColor(Theme.MUTED);
                g.drawString(detail, x, 21 + g.getFontMetrics().getAscent());
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ sidebar button

    private static final class NavButton extends JButton {
        private static final long serialVersionUID = 1L;
        private boolean selectedPage;
        private final Icons.Kind icon;
        private boolean byMouse;

        NavButton(String text, Icons.Kind icon) {
            super(text);
            this.icon = icon;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setFont(Theme.BOLD);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(190, 42));
            getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0, false), "pressed");
            getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0, true), "released");
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) { byMouse = true; }
            });
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusLost(java.awt.event.FocusEvent e) { byMouse = false; repaint(); }
                @Override public void focusGained(java.awt.event.FocusEvent e) { repaint(); }
            });
        }

        void setSelectedPage(boolean s) { selectedPage = s; repaint(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Skin.aa(g);
            int w = getWidth(), h = getHeight();
            if (selectedPage || getModel().isRollover()) {
                g.setColor(selectedPage ? Theme.PRIMARY_LIGHT : new Color(0xF1F5F2));
                g.fill(new RoundRectangle2D.Double(0, 0, w, h, 20, 20));
            }
            if (hasFocus() && !byMouse) {
                g.setColor(Skin.alpha(Theme.PRIMARY, 140));
                g.setStroke(new java.awt.BasicStroke(2f));
                g.draw(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 20, 20));
            }
            String text = getText();
            String badge = null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*) \\((\\d+)\\)$").matcher(text);
            if (m.matches()) { text = m.group(1); badge = m.group(2); }
            Color fg = selectedPage ? Theme.PRIMARY_DARK : Theme.MUTED_DARK;
            if (icon != null) Icons.paint(g, icon, 14, (h - 20) / 2.0, 20, selectedPage ? Theme.PRIMARY : Theme.MUTED, 1f, 1.9);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(fg);
            g.drawString(text, 46, (h - fm.getHeight()) / 2 + fm.getAscent());
            if (badge != null) {
                int bw = Math.max(24, fm.stringWidth(badge) + 14);
                g.setColor(Theme.DANGER);
                g.fill(new RoundRectangle2D.Double(w - bw - 12, (h - 22) / 2.0, bw, 22, 22, 22));
                g.setColor(Color.WHITE);
                g.setFont(Theme.SMALL);
                FontMetrics bm = g.getFontMetrics();
                g.drawString(badge, w - bw - 12 + (bw - bm.stringWidth(badge)) / 2, (h - bm.getHeight()) / 2 + bm.getAscent());
            }
            g.dispose();
        }
    }
}
