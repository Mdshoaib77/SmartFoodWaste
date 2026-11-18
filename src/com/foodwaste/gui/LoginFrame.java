package com.foodwaste.gui;

import com.foodwaste.model.Admin;
import com.foodwaste.model.User;
import com.foodwaste.service.AuthService;
import com.foodwaste.util.Constants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.Ellipse2D;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/** Welcome window: branding on the left, sign-in / registration on the right (FR-01, FR-02). */
public class LoginFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final AuthService auth = new AuthService();
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);

    // sign in
    private final JTextField idField = Theme.field(20);
    private final JPasswordField pwField = Theme.password(20);
    private final JCheckBox adminBox = new JCheckBox("Sign in as Administrator");

    // register
    private final JTextField regName = Theme.field(12);
    private final JTextField regContact = Theme.field(12);
    private final JPasswordField regPw = Theme.password(12);
    private final JPasswordField regPw2 = Theme.password(12);
    private final JComboBox<String> regArea = new JComboBox<>();
    private final JTextField regCapacity = Theme.field(12);
    private final JComboBox<String> regRole = new JComboBox<>(new String[] {Constants.ROLE_DONOR, Constants.ROLE_RECEIVER});

    private final Skin.Segmented tabs = new Skin.Segmented(i -> cards.show(body, i == 0 ? "login" : "register"), "Sign in", "Register");

    public LoginFrame() {
        super("Smart Food Waste Sharing");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(Icons.appIcon(64));
        setSize(1000, 700);
        setMinimumSize(new Dimension(920, 680));
        setLocationRelativeTo(null);
        setLayout(new GridLayout(1, 2));
        Theme.styleInput(regArea);
        ((Skin.Field) idField).setHint("Phone, e-mail or admin username");
        ((Skin.Field) regName).setHint("Your name or organisation");
        ((Skin.Field) regContact).setHint("Phone or e-mail");
        ((Skin.Field) regCapacity).setHint("e.g. 50");
        loadAreas();
        regRole.addActionListener(e -> regCapacity.setEnabled(Constants.ROLE_RECEIVER.equals(regRole.getSelectedItem())));
        regCapacity.setEnabled(false);
        add(brandPanel());
        add(rightPanel());
        getRootPane().setDefaultButton(null);
    }

    // ------------------------------------------------------------------ left side

    private JPanel brandPanel() {
        JPanel p = new JPanel() {
            private static final long serialVersionUID = 1L;
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Skin.aa(g2);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x0B3D20), getWidth(), getHeight(), new Color(0x1E9A4C)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 18));
                g2.fill(new Ellipse2D.Double(-110, getHeight() - 250, 380, 380));
                g2.setColor(new Color(245, 158, 11, 40));
                g2.fill(new Ellipse2D.Double(getWidth() - 190, -100, 300, 300));
                g2.setColor(new Color(255, 255, 255, 14));
                g2.fill(new Ellipse2D.Double(getWidth() - 120, getHeight() / 2.0 + 60, 200, 200));
                g2.dispose();
            }
        };
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(64, 52, 36, 44));
        JComponent logo = new JComponent() {
            private static final long serialVersionUID = 1L;
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Skin.aa(g2);
                g2.drawImage(Icons.appIcon(112), 0, 0, 56, 56, null);
                g2.dispose();
            }
        };
        logo.setPreferredSize(new Dimension(56, 56));
        logo.setMaximumSize(new Dimension(56, 56));
        logo.setAlignmentX(LEFT_ALIGNMENT);
        p.add(logo);
        p.add(vgap(26));
        p.add(light("<html>Priority-based<br>food distribution</html>", Theme.H1.deriveFont(36f), Color.WHITE));
        p.add(vgap(14));
        p.add(light("<html>Connect surplus food with the people who<br>need it, before it expires.</html>", Theme.BASE.deriveFont(15f), new Color(0xD7F0DF)));
        p.add(vgap(40));
        p.add(feature(Icons.Kind.CLOCK, "Urgent food (under 4 hours) comes first"));
        p.add(feature(Icons.Kind.PIN, "Verified NGOs matched by area"));
        p.add(feature(Icons.Kind.LOCK, "Pickups confirmed with a one-time code"));
        p.add(feature(Icons.Kind.ACTIVITY, "Every step logged for admins"));
        p.add(javax.swing.Box.createVerticalGlue());
        p.add(light("CSE Project  \u00B7  Pundra University of Science and Technology", Theme.BASE.deriveFont(12f), new Color(0xB5DFC2)));
        return p;
    }

    private static java.awt.Component vgap(int h) { return javax.swing.Box.createVerticalStrut(h); }

    private static JLabel light(String text, java.awt.Font f, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        l.setForeground(c);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JComponent feature(final Icons.Kind icon, String text) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(7, 0, 7, 0));
        row.setMaximumSize(new Dimension(Short.MAX_VALUE, 52));
        JComponent chip = new JComponent() {
            private static final long serialVersionUID = 1L;
            @Override public Dimension getPreferredSize() { return new Dimension(36, 36); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Skin.aa(g2);
                g2.setColor(new Color(255, 255, 255, 36));
                g2.fill(new Ellipse2D.Double(0, 0, 36, 36));
                Icons.paint(g2, icon, 8, 8, 20, Color.WHITE, 1f, 2.0);
                g2.dispose();
            }
        };
        JLabel l = new JLabel(text);
        l.setFont(Theme.BASE.deriveFont(14f));
        l.setForeground(Color.WHITE);
        row.add(chip, BorderLayout.WEST);
        row.add(l, BorderLayout.CENTER);
        return row;
    }

    // ------------------------------------------------------------------ right side

    private JPanel rightPanel() {
        JPanel right = new JPanel(new BorderLayout(0, 18));
        right.setBackground(Theme.BG);
        right.setBorder(new EmptyBorder(48, 48, 28, 48));

        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        head.add(Theme.title("Welcome"), BorderLayout.NORTH);
        head.add(Theme.muted("Sign in or create an account."), BorderLayout.CENTER);
        JPanel tabRow = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabRow.setBorder(new EmptyBorder(14, 0, 0, 0));
        tabRow.add(tabs);
        head.add(tabRow, BorderLayout.SOUTH);

        body.setOpaque(false);
        body.add(loginCard(), "login");
        body.add(registerCard(), "register");
        JScrollPane scroll = new JScrollPane(new Skin.FitWidth(body), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        right.add(head, BorderLayout.NORTH);
        right.add(scroll, BorderLayout.CENTER);
        return right;
    }

    private void showTab(boolean login) {
        tabs.setSelected(login ? 0 : 1);
        cards.show(body, login ? "login" : "register");
    }

    private JPanel loginCard() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 4));
        Theme.Form f = new Theme.Form(1);
        f.add("Phone / e-mail or admin username", idField);
        f.add("Password", pwField);
        adminBox.setOpaque(false);
        adminBox.setForeground(Theme.TEXT);
        adminBox.setFocusPainted(false);
        JButton go = Theme.button("Sign in", Theme.Kind.PRIMARY);
        go.addActionListener(e -> doLogin());
        pwField.addActionListener(e -> doLogin());
        idField.addActionListener(e -> pwField.requestFocusInWindow());
        JPanel south = new JPanel(new BorderLayout(0, 14));
        south.setOpaque(false);
        south.add(adminBox, BorderLayout.NORTH);
        south.add(go, BorderLayout.CENTER);
        card.add(f.panel(), BorderLayout.NORTH);
        card.add(south, BorderLayout.CENTER);
        wrap.add(card, BorderLayout.NORTH);
        return wrap;
    }

    private JPanel registerCard() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 4));
        Theme.Form f = new Theme.Form(2);
        f.add("Name / organisation", regName);
        f.add("Phone / e-mail", regContact);
        f.add("Password (min 6 characters)", regPw);
        f.add("Confirm password", regPw2);
        f.add("Area (district)", regArea);
        f.add("Role", regRole);
        f.add("Capacity (Receiver/NGO: max quantity you can take)", regCapacity, 2);
        JButton go = Theme.button("Create account", Theme.Kind.PRIMARY);
        go.addActionListener(e -> doRegister());
        JPanel south = new JPanel(new BorderLayout(0, 12));
        south.setOpaque(false);
        south.add(Theme.muted("<html>Receiver/NGO accounts stay <b>Pending</b> until an admin approves them.</html>"), BorderLayout.NORTH);
        south.add(go, BorderLayout.CENTER);
        card.add(f.panel(), BorderLayout.NORTH);
        card.add(south, BorderLayout.CENTER);
        wrap.add(card, BorderLayout.NORTH);
        return wrap;
    }

    private void loadAreas() {
        try {
            regArea.removeAllItems();
            for (String a : auth.areas()) regArea.addItem(a);
        } catch (RuntimeException e) {
            Ui.warn(this, "Could not load the area list: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ actions

    private void doLogin() {
        final String id = idField.getText();
        final String pw = new String(pwField.getPassword());
        final boolean admin = adminBox.isSelected();
        if (id.trim().isEmpty() || pw.isEmpty()) { Ui.warn(this, "Please enter your contact/username and password."); return; }
        Ui.run(this, () -> admin ? (Object) auth.loginAdmin(id, pw) : (Object) auth.loginUser(id, pw), principal -> {
            JFrame next;
            if (principal instanceof Admin) {
                next = new AdminFrame((Admin) principal);
            } else {
                User u = (User) principal;
                next = Constants.ROLE_DONOR.equals(u.getRole()) ? new DonorFrame(u) : new ReceiverFrame(u);
            }
            next.setVisible(true);
            dispose();
        });
    }

    private void doRegister() {
        final String pw = new String(regPw.getPassword());
        if (!pw.equals(new String(regPw2.getPassword()))) { Ui.warn(this, "Passwords do not match."); return; }
        final String role = (String) regRole.getSelectedItem();
        final String area = (String) regArea.getSelectedItem();
        final Integer capacity;
        if (Constants.ROLE_RECEIVER.equals(role)) {
            try {
                capacity = Integer.valueOf(regCapacity.getText().trim());
            } catch (NumberFormatException e) {
                Ui.warn(this, "Capacity must be a whole number greater than zero.");
                return;
            }
        } else {
            capacity = null;
        }
        Ui.run(this, () -> auth.register(regName.getText(), regContact.getText(), pw, role, area == null ? "" : area, capacity), u -> {
            if (Constants.USER_PENDING.equals(u.getStatus()))
                Ui.success(this, "Account created", "Registered as user #" + u.getUserId() + ".\nYour account is PENDING until an administrator approves it.");
            else
                Ui.success(this, "Account created", "Registered as user #" + u.getUserId() + ".\nYou can sign in now.");
            idField.setText(u.getContact());
            pwField.setText("");
            regName.setText(""); regContact.setText(""); regPw.setText(""); regPw2.setText(""); regCapacity.setText("");
            showTab(true);
        });
    }
}
