package com.foodwaste.gui;

import com.foodwaste.model.Message;
import com.foodwaste.model.User;
import com.foodwaste.service.MessageService;
import com.foodwaste.util.Fmt;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

/** Inbox page shared by Donor and Receiver/NGO: administrator messages and automatic notifications, with unread count. */
public class MessagesPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final User user;
    private final MessageService service = new MessageService();
    private final Runnable afterChange;
    private final JLabel unread = Theme.muted("");
    private final JTextArea detail = new JTextArea();
    private final ObjTable<Message> table = new ObjTable<>(
        new String[] {"", "Received", "From", "Subject"},
        m -> new Object[] {m.isRead() ? "" : "NEW", Fmt.dt(m.getCreatedAt()), m.getSenderName(), m.getSubject()});

    public MessagesPanel(User user, Runnable afterChange) {
        super(new BorderLayout());
        this.user = user;
        this.afterChange = afterChange;
        setOpaque(false);
        JButton markAll = Theme.button("Mark all as read", Theme.Kind.SECONDARY);
        markAll.addActionListener(e -> Ui.run(this, () -> service.markAllRead(user), n -> load()));
        detail.setEditable(false);
        detail.setText("Select a message to read it.");
        JPanel list = Theme.tablePanel(table, markAll, unread);
        JPanel reader = Theme.card();
        reader.setLayout(new BorderLayout());
        reader.add(Theme.textAreaScroll(detail), BorderLayout.CENTER);
        reader.setPreferredSize(new Dimension(300, 170));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, list, reader);
        split.setBorder(null);
        split.setOpaque(false);
        split.setResizeWeight(0.7);
        add(split, BorderLayout.CENTER);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            final Message m = table.selected();
            if (m == null) return;
            detail.setText(m.getSubject() + "\n" + Fmt.dt(m.getCreatedAt()) + "  |  From: " + m.getSenderName() + "\n\n" + m.getBody());
            detail.setCaretPosition(0);
            if (!m.isRead()) {
                m.setRead(true);
                Ui.run(this, () -> { service.markRead(user, m.getMessageId()); return service.unreadCount(user); }, n -> {
                    unread.setText(n + " unread");
                    afterChange.run();
                });
            }
        });
    }

    /** Reloads the inbox. */
    public void load() {
        Ui.run(this, () -> new Ui.Pair<>(service.inbox(user), service.unreadCount(user)), r -> {
            List<Message> list = r.a;
            table.setItems(list);
            unread.setText(r.b + " unread");
            afterChange.run();
        });
    }
}
