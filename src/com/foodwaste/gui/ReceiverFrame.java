package com.foodwaste.gui;

import com.foodwaste.model.Food;
import com.foodwaste.model.Request;
import com.foodwaste.model.User;
import com.foodwaste.service.AuthService;
import com.foodwaste.service.FoodService;
import com.foodwaste.service.MessageService;
import com.foodwaste.service.RequestService;
import com.foodwaste.util.Constants;
import com.foodwaste.util.Fmt;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDateTime;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;

/** Receiver / NGO dashboard: browse by priority, request food, confirm pickup with the donor's code. */
public class ReceiverFrame extends BaseFrame {
    private static final long serialVersionUID = 1L;

    private final User receiver;
    private final FoodService foods = new FoodService();
    private final RequestService requests = new RequestService();
    private final MessageService messages = new MessageService();
    private final AuthService auth = new AuthService();
    private MessagesPanel inbox;

    private final Theme.Stat sArea = new Theme.Stat("Available in my area", Theme.PRIMARY, Icons.Kind.PIN);
    private final Theme.Stat sAll = new Theme.Stat("Available everywhere", Theme.BLUE, Icons.Kind.BOWL);
    private final Theme.Stat sOpen = new Theme.Stat("My open requests", Theme.AMBER, Icons.Kind.CLOCK);
    private final Theme.Stat sUnread = new Theme.Stat("Unread messages", Theme.DANGER, Icons.Kind.MAIL);
    private final Theme.Stat sDone = new Theme.Stat("Completed pickups", new java.awt.Color(0x334155), Icons.Kind.CHECK);

    private final JRadioButton myArea = new JRadioButton("My area", true);
    private final JRadioButton allAreas = new JRadioButton("All areas");
    private final JCheckBox fitsOnly = new JCheckBox("Only food that fits my capacity");

    private final ObjTable<Food> foodTable = new ObjTable<>(
        new String[] {"ID", "Food", "Category", "Qty", "Priority", "Capacity", "Expires", "Time left", "Address detail", "Pickup area", "Donor", "Safety info"},
        f -> new Object[] {f.getFoodId(), f.getFoodName(), f.getCategory(), f.getQuantity(), f.getPriorityLevel(),
            fitLabel(f),
            Fmt.dt(f.getExpiryTime()), Fmt.remaining(f.getExpiryTime(), LocalDateTime.now()), f.getLocation(), f.getAreaId(),
            f.getDonorName(), Fmt.nz(f.getSafetyInfo())});

    private final ObjTable<Request> reqTable = new ObjTable<>(
        new String[] {"Req", "Food #", "Food", "Donor", "Wanted slot", "Approved slot", "Status", "Flag", "Requested", "Notes"},
        r -> new Object[] {r.getRequestId(), r.getFoodId(), r.getFoodName(), r.getDonorName(), r.getTimeSlot(), Fmt.nz(r.getApprovedSlot()),
            r.getStatus(), r.isSuspicious() ? "SUSPICIOUS" : "", Fmt.dt(r.getRequestedAt()), Fmt.nz(r.getNotes())});

    private String fitLabel(Food f) { return FoodService.exceedsCapacity(f, receiver) ? "TOO LARGE" : "Fits"; }

    public ReceiverFrame(User receiver) {
        super("Receiver / NGO", receiver.getName() + "   |   Area: " + receiver.getAreaId() + "   |   Capacity: " + receiver.getCapacity());
        this.receiver = receiver;
        addPage("dash", "Dashboard", dashboardPage(), this::loadDashboard);
        addPage("browse", "Browse Food", browsePage(), this::loadFood);
        addPage("reqs", "My Requests", requestsPage(), this::loadRequests);
        inbox = new MessagesPanel(receiver, this::afterOpen);
        addPage("msgs", "Messages", inbox, inbox::load);
        addPage("pwd", "Change Password", new PasswordPanel((o, n) -> auth.changeUserPassword(receiver, o, n), null), () -> { });
        open("dash");
    }

    @Override protected void afterOpen() {
        Ui.run(this, () -> messages.unreadCount(receiver), n -> {
            setNavLabel("msgs", n > 0 ? "Messages (" + n + ")" : "Messages");
            sUnread.set(n);
        });
    }

    private JPanel dashboardPage() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setOpaque(false);
        p.add(Theme.statRow(sArea, sAll, sOpen, sDone, sUnread), BorderLayout.NORTH);
        p.add(Theme.stack(Theme.steps("How to collect food", "Browse food", "Request a time slot", "Get the 6-digit code", "Confirm pickup")),
            BorderLayout.CENTER);
        return p;
    }

    private void loadDashboard() {
        Ui.run(this, () -> new Ui.Pair<>(new Ui.Pair<>(foods.availableFor(receiver, true, false), foods.availableFor(receiver, false, false)),
            requests.forReceiver(receiver)), r -> {
            List<Request> reqs = r.b;
            sArea.set(r.a.a.size());
            sAll.set(r.a.b.size());
            sOpen.set(reqs.stream().filter(q -> Constants.REQ_REQUESTED.equals(q.getStatus()) || Constants.REQ_RESERVED.equals(q.getStatus())).count());
            sDone.set(reqs.stream().filter(q -> Constants.REQ_COMPLETED.equals(q.getStatus())).count());
        });
    }

    // ------------------------------------------------------------------ browse + request (FR-05 / FR-06)

    private JPanel browsePage() {
        ButtonGroup g = new ButtonGroup();
        g.add(myArea);
        g.add(allAreas);
        for (JRadioButton b : new JRadioButton[] {myArea, allAreas}) {
            b.setOpaque(false);
            b.setFocusPainted(false);
            b.addActionListener(e -> loadFood());
        }
        JButton request = Theme.button("Request selected food", Theme.Kind.PRIMARY, Icons.Kind.SEND);
        request.addActionListener(e -> requestSelected());
        fitsOnly.setOpaque(false);
        fitsOnly.setFocusPainted(false);
        fitsOnly.addActionListener(e -> loadFood());
        return Theme.tablePanel(foodTable, myArea, allAreas, fitsOnly, request);
    }

    private void loadFood() {
        final boolean nearby = myArea.isSelected();
        final boolean fits = fitsOnly.isSelected();
        Ui.run(this, () -> foods.availableFor(receiver, nearby, fits), foodTable::setItems);
    }

    private void requestSelected() {
        final Food f = foodTable.selected();
        if (f == null) { Ui.hint(this, "Select a food item in the table first."); return; }
        if (FoodService.exceedsCapacity(f, receiver)) {
            Ui.warn(this, "Food #" + f.getFoodId() + " has quantity " + f.getQuantity() + " but your capacity is " + receiver.getCapacity()
                + ". Requests above your capacity are blocked.");
            return;
        }
        final JComboBox<String> slot = new JComboBox<>(new String[] {
            "08:00-10:00", "10:00-12:00", "12:00-14:00", "14:00-16:00", "16:00-18:00", "18:00-20:00"});
        slot.setEditable(true);
        final JTextArea notes = new JTextArea(4, 24);
        Theme.Form form = new Theme.Form(1);
        form.add("Pickup time slot", slot);
        form.add("Note for the donor (optional)", Theme.textAreaScroll(notes));
        JPanel panel = form.panel();
        panel.setPreferredSize(new Dimension(380, 210));
        if (!Modal.form(this, "Request food #" + f.getFoodId(), f.getFoodName(), panel, "Send request")) return;
        final String s = String.valueOf(slot.getEditor().getItem()).trim();
        if (!com.foodwaste.util.TimeSlot.isValid(s)) {
            Ui.warn(this, "Invalid time slot '" + s + "'. Use HH:mm-HH:mm on a 24-hour clock, e.g. 16:00-18:00.");
            return;
        }
        Ui.run(this, () -> requests.submit(receiver, f.getFoodId(), s, notes.getText()), r -> {
            String msg = "Request #" + r.getRequestId() + " submitted. Waiting for the donor's decision.";
            if (r.isSuspicious()) msg += "\nNote: you hold several open requests, so this one was flagged for administrator review.";
            Ui.success(this, "Request sent", msg);
            loadFood();
        });
    }

    // ------------------------------------------------------------------ my requests + pickup (FR-08)

    private JPanel requestsPage() {
        JButton confirm = Theme.button("Confirm pickup (enter code)", Theme.Kind.PRIMARY);
        confirm.addActionListener(e -> {
            final Request r = reqTable.selected();
            if (r == null) { Ui.hint(this, "Select a request in the table first."); return; }
            String code = Modal.prompt(this, "Confirm pickup", "Enter the 6-digit code the donor gave you for request #"
                + r.getRequestId() + ".", "Verification code", "", "Confirm pickup");
            if (code == null) return;
            final String c = code.trim();
            if (c.isEmpty()) { Ui.warn(this, "Please enter the verification code."); return; }
            Ui.run(this, () -> { requests.confirmPickup(receiver, r.getRequestId(), c); return Boolean.TRUE; }, ok -> {
                Ui.success(this, "Pickup confirmed", "Request #" + r.getRequestId() + " is confirmed. The donor can now mark it Completed.");
                loadRequests();
            });
        });
        JButton cancel = Theme.button("Cancel request", Theme.Kind.DANGER);
        cancel.addActionListener(e -> {
            final Request r = reqTable.selected();
            if (r == null) { Ui.hint(this, "Select a request in the table first."); return; }
            if (!Constants.REQ_REQUESTED.equals(r.getStatus()) && !Constants.REQ_RESERVED.equals(r.getStatus())) {
                Ui.warn(this, "Only Requested or Reserved requests can be cancelled (this one is " + r.getStatus() + ").");
                return;
            }
            if (!Ui.confirmDanger(this, "Cancel this request?", "Request #" + r.getRequestId() + " will be cancelled. The food becomes Available for others and the donor is notified.", "Cancel request", "Keep")) return;
            Ui.run(this, () -> { requests.cancelByReceiver(receiver, r.getRequestId()); return Boolean.TRUE; }, ok -> loadRequests());
        });
        return Theme.tablePanel(reqTable, confirm, cancel);
    }

    private void loadRequests() { Ui.run(this, () -> requests.forReceiver(receiver), reqTable::setItems); }
}
