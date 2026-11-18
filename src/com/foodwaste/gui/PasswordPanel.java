package com.foodwaste.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.BiConsumer;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/** Change-password form (old password, new password, confirmation) used by every role. */
public class PasswordPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** action receives (oldPassword, newPassword) and must throw ServiceException on failure; it runs off the UI thread. */
    public PasswordPanel(final BiConsumer<String, String> action, final Runnable afterSuccess) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(false);
        final JPasswordField old = Theme.password(22);
        final JPasswordField pw = Theme.password(22);
        final JPasswordField pw2 = Theme.password(22);
        Theme.Form f = new Theme.Form(1);
        f.add("Current password", old);
        f.add("New password (min 6 characters)", pw);
        f.add("Confirm new password", pw2);
        JButton go = Theme.button("Change password", Theme.Kind.PRIMARY);
        go.addActionListener(e -> {
            final String o = new String(old.getPassword());
            final String n = new String(pw.getPassword());
            if (!n.equals(new String(pw2.getPassword()))) { Ui.warn(this, "The new passwords do not match."); return; }
            Ui.run(this, () -> { action.accept(o, n); return Boolean.TRUE; }, ok -> {
                old.setText(""); pw.setText(""); pw2.setText("");
                Ui.success(this, "Password changed", "Your password was updated successfully.");
                if (afterSuccess != null) afterSuccess.run();
            });
        });
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 4));
        card.add(f.panel(), BorderLayout.NORTH);
        JPanel south = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.add(go);
        card.add(south, BorderLayout.CENTER);
        add(card);
    }
}
