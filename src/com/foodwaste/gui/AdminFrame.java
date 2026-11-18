package com.foodwaste.gui;

import com.foodwaste.model.ActivityLog;
import com.foodwaste.model.Admin;
import com.foodwaste.model.Food;
import com.foodwaste.model.Message;
import com.foodwaste.model.Request;
import com.foodwaste.model.User;
import com.foodwaste.service.AdminService;
import com.foodwaste.service.AuthService;
import com.foodwaste.service.ExpiryService;
import com.foodwaste.service.MessageService;
import com.foodwaste.util.Constants;
import com.foodwaste.util.Fmt;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.LineBorder;

/** Administrator dashboard: NGO verification, monitoring, suspicious requests, logs and reports (FR-10, FR-11). */
public class AdminFrame extends BaseFrame {
    private static final long serialVersionUID = 1L;

    private final Admin admin;
    private final AdminService service = new AdminService();
    private final MessageService messages = new MessageService();
    private final AuthService auth = new AuthService();

    private final Theme.Stat sUsers = new Theme.Stat("Registered users", Theme.PRIMARY, Icons.Kind.USERS);
    private final Theme.Stat sPending = new Theme.Stat("Pending NGOs", Theme.AMBER, Icons.Kind.CLOCK);
    private final Theme.Stat sFood = new Theme.Stat("Food posts", Theme.BLUE, Icons.Kind.BOWL);
    private final Theme.Stat sReq = new Theme.Stat("Requests", new java.awt.Color(0x6D28D9), Icons.Kind.INBOX);
    private final Theme.Stat sSusp = new Theme.Stat("Suspicious requests", Theme.DANGER, Icons.Kind.WARN);

    private static final String[] USER_COLS = {"ID", "Name", "Contact", "Role", "Area", "Capacity", "Status", "Verified by", "Registered"};
    private static final String[] FOOD_COLS = {"ID", "Food", "Category", "Qty", "Priority", "Status", "Expires", "Time left", "Address detail", "Pickup area", "Donor"};
    private static final String[] REQ_COLS = {"Req", "Food #", "Food", "Donor", "Pickup area", "Receiver / NGO", "Receiver area", "Wanted slot", "Approved slot",
        "Status", "Flag", "Requested", "Updated", "Picked up", "Notes"};
    private static final String[] ADMIN_COLS = {"ID", "Username", "Created by", "Created", "Default password"};
    private static final String[] MSG_COLS = {"Sent", "To", "Subject", "Message"};
    private static final String[] LOG_COLS = {"Log", "Time", "Actor", "Action", "Details"};

    private final ObjTable<ActivityLog> recentLogs = new ObjTable<>(LOG_COLS, AdminFrame::logRow);
    private final ObjTable<User> pendingTable = new ObjTable<>(USER_COLS, AdminFrame::userRow);
    private final ObjTable<User> usersTable = new ObjTable<>(USER_COLS, AdminFrame::userRow);
    private final ObjTable<Food> foodTable = new ObjTable<>(FOOD_COLS, AdminFrame::foodRow);
    private final ObjTable<Request> reqTable = new ObjTable<>(REQ_COLS, AdminFrame::reqRow);
    private final ObjTable<ActivityLog> logTable = new ObjTable<>(LOG_COLS, AdminFrame::logRow);
    private final JCheckBox onlySuspicious = new JCheckBox("Suspicious only");
    private final JSpinner logLimit = new JSpinner(new SpinnerNumberModel(100, 10, 5000, 10));
    private final JTextArea reportArea = new JTextArea();
    private final ObjTable<com.foodwaste.model.Admin> adminTable = new ObjTable<>(ADMIN_COLS,
        a -> new Object[] {a.getAdminId(), a.getUsername(), a.getCreatedBy() == null ? "-" : "Admin #" + a.getCreatedBy(),
            Fmt.dt(a.getCreatedAt()), a.isMustChangePassword() ? "NOT CHANGED" : "changed"});
    private final ObjTable<Message> sentTable = new ObjTable<>(MSG_COLS,
        m -> new Object[] {Fmt.dt(m.getCreatedAt()), m.getRecipientName(), m.getSubject(), m.getBody()});
    private final JComboBox<String> fStatus = new JComboBox<>();
    private final JComboBox<String> fArea = new JComboBox<>();
    private final JTextField fFrom = Theme.field(8);
    private final JTextField fTo = Theme.field(8);
    private final JComboBox<Object> msgTarget = new JComboBox<>();
    private final JTextField msgSubject = Theme.field(30);
    private final JTextArea msgBody = new JTextArea(5, 30);

    /** A single user as a recipient choice. */
    private static final class Recipient {
        final User user;
        Recipient(User u) { this.user = u; }
        @Override public String toString() { return "#" + user.getUserId() + " " + user.getName() + " (" + user.getRole() + ", " + user.getAreaId() + ")"; }
    }

    public AdminFrame(Admin admin) {
        super("Administrator", admin.getUsername());
        this.admin = admin;
        addPage("dash", "Overview", overviewPage(), this::loadOverview);
        addPage("pending", "Verify NGOs", pendingPage(), this::loadPending);
        addPage("users", "All Users", Theme.tablePanel(usersTable), () -> load(() -> service.allUsers(admin), usersTable));
        addPage("food", "All Food Records", Theme.tablePanel(foodTable), () -> load(() -> service.allFood(admin), foodTable));
        addPage("reqs", "Interactions", requestsPage(), this::loadRequests);
        addPage("msg", "Send Message", messagePage(), this::loadMessagePage);
        addPage("admins", "Administrators", adminsPage(), this::loadAdmins);
        addPage("logs", "Activity Log", logPage(), this::loadLogs);
        addPage("report", "Reports", reportPage(), () -> { });
        addPage("pwd", "Change Password", new PasswordPanel((o, n) -> auth.changeAdminPassword(admin, o, n),
            () -> admin.setMustChangePassword(false)), () -> { });
        open("dash");
        if (admin.isMustChangePassword())
            javax.swing.SwingUtilities.invokeLater(() -> {
                Ui.warn(this, "You are using the default administrator password. Please change it now (sidebar > Change Password).");
                open("pwd");
            });
    }

    private <T> void load(java.util.concurrent.Callable<List<T>> source, ObjTable<T> table) {
        Ui.run(this, source, table::setItems);
    }

    // ------------------------------------------------------------------ row mappers

    private static Object[] userRow(User u) {
        return new Object[] {u.getUserId(), u.getName(), u.getContact(), u.getRole(), u.getAreaId(),
            u.getCapacity() == null ? "-" : u.getCapacity(), u.getStatus(),
            u.getVerifiedBy() == null ? "-" : "Admin #" + u.getVerifiedBy(), Fmt.dt(u.getCreatedAt())};
    }

    private static Object[] foodRow(Food f) {
        return new Object[] {f.getFoodId(), f.getFoodName(), f.getCategory(), f.getQuantity(), f.getPriorityLevel(), f.getStatus(),
            Fmt.dt(f.getExpiryTime()), Fmt.remaining(f.getExpiryTime(), LocalDateTime.now()), f.getLocation(), f.getAreaId(), f.getDonorName()};
    }

    private static Object[] reqRow(Request r) {
        return new Object[] {r.getRequestId(), r.getFoodId(), r.getFoodName(), r.getDonorName(), Fmt.nz(r.getPickupArea()),
            r.getReceiverName(), Fmt.nz(r.getReceiverArea()), r.getTimeSlot(), Fmt.nz(r.getApprovedSlot()), r.getStatus(),
            r.isSuspicious() ? "SUSPICIOUS" : "", Fmt.dt(r.getRequestedAt()), Fmt.dt(r.getUpdatedAt()),
            r.getPickupTime() == null ? "-" : Fmt.dt(r.getPickupTime()), Fmt.nz(r.getNotes())};
    }

    private static Object[] logRow(ActivityLog l) {
        return new Object[] {l.getLogId(), Fmt.dt(l.getLoggedAt()), l.getActorType() + (l.getActorId() == null ? "" : " #" + l.getActorId()),
            l.getAction(), Fmt.nz(l.getDetails())};
    }

    // ------------------------------------------------------------------ overview

    private JPanel overviewPage() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(Theme.statRow(sUsers, sPending, sFood, sReq, sSusp), BorderLayout.NORTH);
        JButton expiry = Theme.button("Run expiry check now", Theme.Kind.SECONDARY, Icons.Kind.CLOCK);
        expiry.addActionListener(e -> Ui.run(this, ExpiryService::sync, n -> {
            Ui.success(this, "Expiry check complete", n + " food item(s) newly marked Expired.");
            loadOverview();
        }));
        JPanel row = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(expiry);
        top.add(row, BorderLayout.CENTER);
        p.add(top, BorderLayout.NORTH);
        JPanel card = Theme.tablePanel(recentLogs, Theme.heading("Recent activity"));
        p.add(card, BorderLayout.CENTER);
        return p;
    }

    private void loadOverview() {
        Ui.run(this, () -> {
            List<User> users = service.allUsers(admin);
            List<User> pending = service.pendingReceivers(admin);
            List<Food> food = service.allFood(admin);
            List<Request> reqs = service.allRequests(admin);
            List<Request> susp = service.suspiciousRequests(admin);
            List<ActivityLog> logs = service.recentLogs(admin, 15);
            return new Object[] {users.size(), pending.size(), food.size(), reqs.size(), susp.size(), logs};
        }, d -> {
            sUsers.set(d[0]); sPending.set(d[1]); sFood.set(d[2]); sReq.set(d[3]); sSusp.set(d[4]);
            @SuppressWarnings("unchecked")
            List<ActivityLog> logs = (List<ActivityLog>) d[5];
            recentLogs.setItems(logs);
        });
    }

    // ------------------------------------------------------------------ NGO verification

    private JPanel pendingPage() {
        JButton approve = Theme.button("Approve", Theme.Kind.PRIMARY);
        JButton reject = Theme.button("Reject", Theme.Kind.DANGER);
        approve.addActionListener(e -> verify(true));
        reject.addActionListener(e -> verify(false));
        return Theme.tablePanel(pendingTable, approve, reject);
    }

    private void verify(final boolean approve) {
        final User u = pendingTable.selected();
        if (u == null) { Ui.hint(this, "Select a pending account first."); return; }
        String who = u.getName() + " (user #" + u.getUserId() + ")";
        if (approve ? !Ui.confirm(this, "Approve this NGO?", who + " will be approved and can start requesting food.", "Approve")
            : !Ui.confirmDanger(this, "Reject this NGO?", who + " will be rejected.", "Reject", "Keep")) return;
        Ui.run(this, () -> { service.verifyReceiver(admin, u.getUserId(), approve); return Boolean.TRUE; }, ok -> loadPending());
    }

    private void loadPending() { load(() -> service.pendingReceivers(admin), pendingTable); }

    // ------------------------------------------------------------------ requests / suspicious

    private JPanel requestsPage() {
        onlySuspicious.setOpaque(false);
        onlySuspicious.setFocusPainted(false);
        fStatus.addItem("All");
        for (String st : Arrays.asList("Requested", "Reserved", "Rejected", "Picked Up", "Completed", "Expired", "Cancelled")) fStatus.addItem(st);
        fArea.addItem("All");
        Theme.styleInput(fStatus);
        Theme.styleInput(fArea);
        fFrom.setToolTipText("yyyy-MM-dd (blank = any)");
        fTo.setToolTipText("yyyy-MM-dd (blank = any)");
        JButton apply = Theme.button("Apply filters", Theme.Kind.PRIMARY);
        apply.addActionListener(e -> loadRequests());
        JButton reset = Theme.button("Reset", Theme.Kind.SECONDARY);
        reset.addActionListener(e -> {
            fStatus.setSelectedIndex(0); fArea.setSelectedIndex(0); fFrom.setText(""); fTo.setText("");
            onlySuspicious.setSelected(false);
            loadRequests();
        });
        JButton flag = Theme.button("Flag / unflag", Theme.Kind.SECONDARY);
        JButton cancel = Theme.button("Cancel request", Theme.Kind.DANGER);
        flag.addActionListener(e -> {
            final Request r = reqTable.selected();
            if (r == null) { Ui.hint(this, "Select a request first."); return; }
            final boolean set = !r.isSuspicious();
            Ui.run(this, () -> { service.flagRequest(admin, r.getRequestId(), set); return Boolean.TRUE; }, ok -> loadRequests());
        });
        cancel.addActionListener(e -> {
            final Request r = reqTable.selected();
            if (r == null) { Ui.hint(this, "Select a request first."); return; }
            if (!Ui.confirmDanger(this, "Cancel this request?", "Request #" + r.getRequestId() + " will be cancelled and the food released. Both sides are notified.", "Cancel request", "Keep")) return;
            Ui.run(this, () -> { service.cancelRequest(admin, r.getRequestId()); return Boolean.TRUE; }, ok -> loadRequests());
        });
        JPanel row1 = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row1.add(Theme.muted("Status")); row1.add(fStatus);
        row1.add(Theme.muted("Area")); row1.add(fArea);
        row1.add(Theme.muted("From")); row1.add(fFrom);
        row1.add(Theme.muted("To")); row1.add(fTo);
        JPanel row2 = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row2.add(onlySuspicious); row2.add(apply); row2.add(reset);
        JPanel filters = Theme.transparent(new java.awt.GridLayout(2, 1, 0, 0));
        filters.add(row1);
        filters.add(row2);
        JPanel wrap = new JPanel(new BorderLayout(0, 10));
        wrap.setOpaque(false);
        JPanel fc = Theme.card();
        fc.setLayout(new BorderLayout());
        fc.add(filters, BorderLayout.CENTER);
        wrap.add(fc, BorderLayout.NORTH);
        wrap.add(Theme.tablePanel(reqTable, flag, cancel), BorderLayout.CENTER);
        return wrap;
    }

    private void loadRequests() {
        final Object st = fStatus.getSelectedItem();
        final Object ar = fArea.getSelectedItem();
        final LocalDate from, to;
        try {
            from = fFrom.getText().trim().isEmpty() ? null : LocalDate.parse(fFrom.getText().trim());
            to = fTo.getText().trim().isEmpty() ? null : LocalDate.parse(fTo.getText().trim());
        } catch (java.time.format.DateTimeParseException ex) {
            Ui.warn(this, "Dates must look like 2026-11-10 (yyyy-MM-dd), or be left blank.");
            return;
        }
        final boolean susp = onlySuspicious.isSelected();
        Ui.run(this, () -> new Ui.Pair<>(service.areas(admin),
            service.interactions(admin, st == null ? null : st.toString(), ar == null ? null : ar.toString(), from, to, susp)), r -> {
            if (fArea.getItemCount() - 1 != r.a.size()) {      // fill the area list once (and after areas change)
                fArea.removeAllItems();
                fArea.addItem("All");
                for (String a : r.a) fArea.addItem(a);
                if (ar != null) fArea.setSelectedItem(ar);
            }
            reqTable.setItems(r.b);
        });
    }

    // ------------------------------------------------------------------ administrator messages

    private JPanel messagePage() {
        Theme.styleInput(msgTarget);
        Theme.Form f = new Theme.Form(1);
        f.add("Send to", msgTarget);
        f.add("Subject", msgSubject);
        f.add("Message", Theme.textAreaScroll(msgBody));
        JButton send = Theme.button("Send message", Theme.Kind.PRIMARY, Icons.Kind.SEND);
        send.addActionListener(e -> {
            final Object t = msgTarget.getSelectedItem();
            final String subject = msgSubject.getText();
            final String body = msgBody.getText();
            if (t == null) { Ui.warn(this, "Choose a recipient."); return; }
            Ui.run(this, () -> {
                if (t instanceof Recipient) {
                    messages.sendToUser(admin, ((Recipient) t).user.getUserId(), subject, body);
                    return 1;
                }
                return messages.broadcast(admin, t.toString(), subject, body);
            }, n -> {
                Ui.success(this, "Message sent", "Delivered to " + n + " user(s).");
                msgSubject.setText("");
                msgBody.setText("");
                loadMessagePage();
            });
        });
        JPanel form = Theme.card();
        form.setLayout(new BorderLayout(0, 6));
        form.add(f.panel(), BorderLayout.NORTH);
        JPanel south = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.add(send);
        form.add(south, BorderLayout.CENTER);
        JPanel p = new JPanel(new BorderLayout(0, 14));
        p.setOpaque(false);
        p.add(form, BorderLayout.NORTH);
        p.add(Theme.tablePanel(sentTable, Theme.heading("Recently sent")), BorderLayout.CENTER);
        return p;
    }

    private void loadMessagePage() {
        Ui.run(this, () -> new Ui.Pair<>(service.allUsers(admin), messages.recentAdminMessages(admin, 100)), r -> {
            Object keep = msgTarget.getSelectedItem();
            msgTarget.removeAllItems();
            msgTarget.addItem(Constants.AUDIENCE_DONORS);
            msgTarget.addItem(Constants.AUDIENCE_RECEIVERS);
            msgTarget.addItem(Constants.AUDIENCE_EVERYONE);
            for (User u : r.a)
                if (Constants.USER_APPROVED.equals(u.getStatus())) msgTarget.addItem(new Recipient(u));
            if (keep != null && !(keep instanceof Recipient)) msgTarget.setSelectedItem(keep);
            sentTable.setItems(r.b);
        });
    }

    // ------------------------------------------------------------------ administrator accounts

    private JPanel adminsPage() {
        final JTextField user = Theme.field(16);
        final javax.swing.JPasswordField pw = Theme.password(16);
        final javax.swing.JPasswordField pw2 = Theme.password(16);
        JButton create = Theme.button("Create administrator", Theme.Kind.PRIMARY);
        create.addActionListener(e -> {
            final String p1 = new String(pw.getPassword());
            if (!p1.equals(new String(pw2.getPassword()))) { Ui.warn(this, "Passwords do not match."); return; }
            Ui.run(this, () -> service.createAdmin(admin, user.getText(), p1), a -> {
                Ui.success(this, "Administrator created", "'" + a.getUsername() + "' can now sign in.");
                user.setText(""); pw.setText(""); pw2.setText("");
                loadAdmins();
            });
        });
        Theme.Form f = new Theme.Form(3);
        f.add("New username", user);
        f.add("Password (min 6 characters)", pw);
        f.add("Confirm password", pw2);
        JPanel form = Theme.card();
        form.setLayout(new BorderLayout(0, 6));
        form.add(f.panel(), BorderLayout.NORTH);
        JPanel south = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.add(create);
        form.add(south, BorderLayout.CENTER);
        JPanel p = new JPanel(new BorderLayout(0, 14));
        p.setOpaque(false);
        p.add(form, BorderLayout.NORTH);
        p.add(Theme.tablePanel(adminTable), BorderLayout.CENTER);
        return p;
    }

    private void loadAdmins() { load(() -> service.allAdmins(admin), adminTable); }

    // ------------------------------------------------------------------ activity log

    private JPanel logPage() {
        Theme.styleInput(logLimit);
        logLimit.setPreferredSize(new java.awt.Dimension(96, Skin.CONTROL_H));
        JButton show = Theme.button("Show", Theme.Kind.SECONDARY);
        show.addActionListener(e -> loadLogs());
        return Theme.tablePanel(logTable, Theme.muted("Latest entries"), logLimit, show);
    }

    private void loadLogs() {
        final int n = ((Number) logLimit.getValue()).intValue();
        Ui.run(this, () -> service.recentLogs(admin, n), logTable::setItems);
    }

    // ------------------------------------------------------------------ report (FR-11)

    private JPanel reportPage() {
        reportArea.setEditable(false);
        reportArea.setFont(Theme.MONO);
        reportArea.setText("Generate a report to preview it here.");
        JButton gen = Theme.button("Generate report", Theme.Kind.PRIMARY, Icons.Kind.FILE);
        JButton save = Theme.button("Save to file", Theme.Kind.SECONDARY);
        gen.addActionListener(e -> Ui.run(this, () -> service.generateReport(admin), t -> { reportArea.setText(t); reportArea.setCaretPosition(0); }));
        save.addActionListener(e -> {
            final String text = reportArea.getText();
            if (!text.startsWith("SMART FOOD WASTE")) { Ui.hint(this, "Generate the report first."); return; }
            Ui.run(this, () -> service.saveReport(admin, text), path -> Ui.success(this, "Report saved", "Saved to " + path.toAbsolutePath()));
        });
        JButton csv = Theme.button("Export CSV reports", Theme.Kind.SECONDARY);
        csv.addActionListener(e -> Ui.run(this, () -> service.exportCsv(admin), paths -> {
            StringBuilder sb = new StringBuilder("CSV files saved:");
            for (java.nio.file.Path pth : paths) sb.append("\n").append(pth.toAbsolutePath());
            Ui.success(this, "Export complete", sb.toString());
        }));
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 12));
        JPanel bar = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.add(gen);
        bar.add(save);
        bar.add(csv);
        JScrollPane sp = new JScrollPane(reportArea);
        sp.setBorder(new LineBorder(Theme.BORDER));
        card.add(bar, BorderLayout.NORTH);
        card.add(sp, BorderLayout.CENTER);
        return card;
    }
}
