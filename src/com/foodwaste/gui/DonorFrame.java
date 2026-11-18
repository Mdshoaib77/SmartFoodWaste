package com.foodwaste.gui;

import com.foodwaste.model.Food;
import com.foodwaste.model.Pickup;
import com.foodwaste.model.Request;
import com.foodwaste.model.User;
import com.foodwaste.service.AuthService;
import com.foodwaste.service.FoodService;
import com.foodwaste.service.MessageService;
import com.foodwaste.service.PriorityCalculator;
import com.foodwaste.service.RequestService;
import com.foodwaste.util.Constants;
import com.foodwaste.util.Fmt;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Donor dashboard: post food, review requests, approve/reject, hand over the code, close the record. */
public class DonorFrame extends BaseFrame {
    private static final long serialVersionUID = 1L;

    private final User donor;
    private final FoodService foods = new FoodService();
    private final RequestService requests = new RequestService();
    private final MessageService messages = new MessageService();
    private final AuthService auth = new AuthService();
    private MessagesPanel inbox;

    private final Theme.Stat sPosts = new Theme.Stat("My food posts", Theme.PRIMARY, Icons.Kind.LIST);
    private final Theme.Stat sAvail = new Theme.Stat("Available now", Theme.BLUE, Icons.Kind.BOWL);
    private final Theme.Stat sWait = new Theme.Stat("Awaiting my decision", Theme.AMBER, Icons.Kind.CLOCK);
    private final Theme.Stat sRes = new Theme.Stat("Reserved", new java.awt.Color(0x6D28D9), Icons.Kind.LOCK);
    private final Theme.Stat sUnread = new Theme.Stat("Unread messages", Theme.DANGER, Icons.Kind.MAIL);
    private final Theme.Stat sDone = new Theme.Stat("Completed", new java.awt.Color(0x334155), Icons.Kind.CHECK);

    private final ObjTable<Food> postsTable = new ObjTable<>(
        new String[] {"ID", "Food", "Category", "Qty", "Priority", "Status", "Expires", "Time left", "Pickup area", "Address detail", "Safety info"},
        f -> new Object[] {f.getFoodId(), f.getFoodName(), f.getCategory(), f.getQuantity(), f.getPriorityLevel(), f.getStatus(),
            Fmt.dt(f.getExpiryTime()), Fmt.remaining(f.getExpiryTime(), LocalDateTime.now()), f.getAreaId(), f.getLocation(), Fmt.nz(f.getSafetyInfo())});

    private final ObjTable<Request> reqTable = new ObjTable<>(
        new String[] {"Req", "Food #", "Food", "Receiver / NGO", "Wanted slot", "Approved slot", "Status", "Flag", "Requested", "Notes"},
        r -> new Object[] {r.getRequestId(), r.getFoodId(), r.getFoodName(), r.getReceiverName(), r.getTimeSlot(),
            Fmt.nz(r.getApprovedSlot()), r.getStatus(), r.isSuspicious() ? "SUSPICIOUS" : "", Fmt.dt(r.getRequestedAt()),
            Fmt.nz(r.getNotes())});

    private final ObjTable<User> nearTable = new ObjTable<>(
        new String[] {"ID", "Name", "Contact", "Area", "Capacity", "Status"},
        u -> new Object[] {u.getUserId(), u.getName(), u.getContact(), u.getAreaId(), u.getCapacity(), u.getStatus()});

    private final JComboBox<Object> nearFood = new JComboBox<>();
    private boolean nearLoading;
    private static final String ALL_RECEIVERS = "All receivers in my registered area";

    public DonorFrame(User donor) {
        super("Donor", donor.getName() + "   |   Area: " + donor.getAreaId());
        this.donor = donor;
        addPage("dash", "Dashboard", dashboardPage(), this::loadDashboard);
        addPage("post", "Post Surplus Food", postPage(), () -> { });
        addPage("posts", "My Food Posts", postsPage(), this::loadPosts);
        addPage("reqs", "Incoming Requests", requestsPage(), this::loadRequests);
        addPage("near", "Nearby Receivers", nearPage(), this::loadNear);
        inbox = new MessagesPanel(donor, this::afterOpen);
        addPage("msgs", "Messages", inbox, inbox::load);
        addPage("pwd", "Change Password", new PasswordPanel((o, n) -> auth.changeUserPassword(donor, o, n), null), () -> { });
        open("dash");
    }

    @Override protected void afterOpen() {
        Ui.run(this, () -> messages.unreadCount(donor), n -> {
            setNavLabel("msgs", n > 0 ? "Messages (" + n + ")" : "Messages");
            sUnread.set(n);
        });
    }

    // ------------------------------------------------------------------ dashboard

    private JPanel dashboardPage() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setOpaque(false);
        p.add(Theme.statRow(sPosts, sAvail, sWait, sRes, sDone, sUnread), BorderLayout.NORTH);
        JPanel levels = Theme.card();
        levels.setLayout(new BorderLayout(0, 12));
        levels.add(Theme.heading("Priority levels"), BorderLayout.NORTH);
        JPanel chips = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chips.add(Theme.pill("High", new java.awt.Color(0xB91C1C), new java.awt.Color(0xFDE8E8)));
        chips.add(Theme.muted("under " + Constants.HIGH_PRIORITY_HOURS + " h left"));
        chips.add(javax.swing.Box.createHorizontalStrut(14));
        chips.add(Theme.pill("Medium", new java.awt.Color(0xB45309), new java.awt.Color(0xFEF0D4)));
        chips.add(Theme.muted("under " + Constants.MEDIUM_PRIORITY_HOURS + " h left"));
        chips.add(javax.swing.Box.createHorizontalStrut(14));
        chips.add(Theme.pill("Low", new java.awt.Color(0x15803D), new java.awt.Color(0xE3F5EA)));
        chips.add(Theme.muted(Constants.MEDIUM_PRIORITY_HOURS + " h or more"));
        levels.add(chips, BorderLayout.CENTER);
        JPanel flow = Theme.steps("How it works", "Post food", "Approve a request", "Give the 6-digit code", "Mark completed");
        p.add(Theme.stack(flow, levels), BorderLayout.CENTER);
        return p;
    }

    private void loadDashboard() {
        Ui.run(this, () -> new Ui.Pair<>(foods.myPosts(donor), requests.forDonor(donor)), r -> {
            List<Food> posts = r.a;
            List<Request> reqs = r.b;
            sPosts.set(posts.size());
            sAvail.set(posts.stream().filter(f -> Constants.FOOD_AVAILABLE.equals(f.getStatus())).count());
            sWait.set(reqs.stream().filter(q -> Constants.REQ_REQUESTED.equals(q.getStatus())).count());
            sRes.set(reqs.stream().filter(q -> Constants.REQ_RESERVED.equals(q.getStatus())).count());
            sDone.set(reqs.stream().filter(q -> Constants.REQ_COMPLETED.equals(q.getStatus())).count());
        });
    }

    // ------------------------------------------------------------------ post food (FR-03 / FR-04)

    private JPanel postPage() {
        final JTextField name = Theme.field(22);
        final JComboBox<String> category = new JComboBox<>(new String[] {
            "Cooked meal", "Bakery", "Fruit", "Vegetables", "Packaged food", "Dairy", "Beverages", "Other"});
        category.setEditable(true);
        final JSpinner qty = new JSpinner(new SpinnerNumberModel(10, 1, 100000, 1));
        Theme.styleInput(qty);
        final JComboBox<String> pickupArea = areaCombo(donor.getAreaId());
        final JTextField location = Theme.field(22);
        final JTextField prep = Theme.field(22);
        prep.setText(Fmt.dt(LocalDateTime.now()));
        final JTextField expiry = Theme.field(22);
        final JTextArea safety = new JTextArea(3, 22);
        final JLabel preview = Theme.muted("Priority: enter an expiry time");

        JPanel quick = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (final int h : new int[] {2, 6, 12, 24}) {
            JButton b = Theme.button("+" + h + "h", Theme.Kind.SECONDARY);
            b.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 14, 8, 14));
            b.addActionListener(e -> expiry.setText(Fmt.dt(LocalDateTime.now().plusHours(h))));
            quick.add(b);
        }
        JPanel expiryBox = new JPanel(new BorderLayout(0, 6));
        expiryBox.setOpaque(false);
        expiryBox.add(expiry, BorderLayout.NORTH);
        expiryBox.add(quick, BorderLayout.CENTER);
        expiryBox.add(preview, BorderLayout.SOUTH);

        expiry.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                try {
                    LocalDateTime x = Ui.parseDateTime(expiry.getText(), false);
                    LocalDateTime now = LocalDateTime.now();
                    preview.setText("Priority: " + PriorityCalculator.calculate(x, now) + "  (" + Fmt.remaining(x, now) + " left)");
                } catch (IllegalArgumentException ex) {
                    preview.setText("Priority: enter a valid expiry time");
                }
            }
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        });

        Theme.Form f = new Theme.Form(2);
        f.add("Food name", name);
        f.add("Category", category);
        f.add("Quantity (portions)", qty);
        f.add("Pickup area (district)", pickupArea);
        f.add("Address detail (house / road / landmark)", location);
        f.add("Prepared at (yyyy-MM-dd HH:mm)", prep);
        f.add("Expires at (yyyy-MM-dd HH:mm or +5h)", expiryBox);
        f.add("Safety info (storage, allergens - optional)", Theme.textAreaScroll(safety), 2);

        JButton submit = Theme.button("Post food", Theme.Kind.PRIMARY, Icons.Kind.SEND);
        submit.addActionListener(e -> {
            LocalDateTime p, x;
            try {
                p = Ui.parseDateTime(prep.getText(), true);
                x = Ui.parseDateTime(expiry.getText(), false);
            } catch (IllegalArgumentException ex) {
                Ui.warn(this, ex.getMessage());
                return;
            }
            final LocalDateTime fp = p, fx = x;
            final String cat = String.valueOf(category.getEditor().getItem());
            final int q = ((Number) qty.getValue()).intValue();
            Ui.run(this, () -> {
                Food posted = foods.postFood(donor, name.getText(), cat, q, fp, fx, (String) pickupArea.getSelectedItem(),
                    location.getText(), safety.getText());
                return new Ui.Pair<>(posted, foods.nearbyReceivers(donor, posted.getFoodId()).size());
            }, r -> {
                Ui.success(this, "Food posted", "Food #" + r.a.getFoodId() + "  \u00B7  priority " + r.a.getPriorityLevel() + "\n"
                    + r.b + " approved Receiver/NGO account(s) in '" + r.a.getAreaId() + "' have enough capacity and will see it first.");
                name.setText(""); location.setText(""); safety.setText(""); expiry.setText("");
                prep.setText(Fmt.dt(LocalDateTime.now()));
            });
        });

        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 4));
        card.add(f.panel(), BorderLayout.NORTH);
        JPanel south = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.add(submit);
        card.add(south, BorderLayout.CENTER);
        JPanel wrap = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrap.add(card);
        javax.swing.JScrollPane sp = new javax.swing.JScrollPane(new Skin.FitWidth(wrap), javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        JPanel out = Theme.transparent(new BorderLayout());
        out.add(sp, BorderLayout.CENTER);
        return out;
    }

    // ------------------------------------------------------------------ my posts / requests / nearby

    private JPanel postsPage() {
        JButton edit = Theme.button("Edit", Theme.Kind.SECONDARY);
        JButton del = Theme.button("Delete", Theme.Kind.DANGER);
        edit.addActionListener(e -> {
            final Food f = postsTable.selected();
            if (f == null) { Ui.hint(this, "Select a food post in the table first."); return; }
            if (!Constants.FOOD_AVAILABLE.equals(f.getStatus())) {
                Ui.warn(this, "Only posts that are still Available can be edited (this one is " + f.getStatus() + ").");
                return;
            }
            editDialog(f);
        });
        del.addActionListener(e -> {
            final Food f = postsTable.selected();
            if (f == null) { Ui.hint(this, "Select a food post in the table first."); return; }
            if (!Constants.FOOD_AVAILABLE.equals(f.getStatus())) {
                Ui.warn(this, "Only posts that are still Available can be deleted (this one is " + f.getStatus() + ").");
                return;
            }
            if (!Ui.confirmDanger(this, "Delete this post?", "Food #" + f.getFoodId() + " '" + f.getFoodName() + "' will be deleted permanently.", "Delete", "Keep")) return;
            Ui.run(this, () -> { foods.deleteFood(donor, f.getFoodId()); return Boolean.TRUE; }, ok -> loadPosts());
        });
        return Theme.tablePanel(postsTable, edit, del);
    }

    /** Drop-down with the standard area list (all 64 districts), pre-selected on the given area. */
    private JComboBox<String> areaCombo(String selected) {
        JComboBox<String> cb = new JComboBox<>();
        Theme.styleInput(cb);
        try {
            for (String a : foods.areas()) cb.addItem(a);
        } catch (RuntimeException e) {
            Ui.warn(this, "Could not load the area list: " + e.getMessage());
        }
        cb.setSelectedItem(selected);
        return cb;
    }

    /** Edit form for an Available post (all values pre-filled). */
    private void editDialog(final Food f) {
        final JTextField name = Theme.field(22);
        name.setText(f.getFoodName());
        final JComboBox<String> category = new JComboBox<>(new String[] {
            "Cooked meal", "Bakery", "Fruit", "Vegetables", "Packaged food", "Dairy", "Beverages", "Other"});
        category.setEditable(true);
        category.setSelectedItem(f.getCategory());
        final JSpinner qty = new JSpinner(new SpinnerNumberModel(Math.max(1, f.getQuantity()), 1, 100000, 1));
        final JComboBox<String> pickupArea = areaCombo(f.getAreaId());
        final JTextField location = Theme.field(22);
        location.setText(f.getLocation());
        final JTextField prep = Theme.field(22);
        prep.setText(Fmt.dt(f.getPreparationTime()));
        final JTextField expiry = Theme.field(22);
        expiry.setText(Fmt.dt(f.getExpiryTime()));
        final JTextArea safety = new JTextArea(3, 22);
        safety.setText(f.getSafetyInfo() == null ? "" : f.getSafetyInfo());
        Theme.Form form = new Theme.Form(2);
        form.add("Food name", name);
        form.add("Category", category);
        form.add("Quantity", qty);
        form.add("Pickup area (district)", pickupArea);
        form.add("Pickup address detail", location);
        form.add("Prepared at (yyyy-MM-dd HH:mm)", prep);
        form.add("Expires at (yyyy-MM-dd HH:mm or +5h)", expiry);
        form.add("Safety information", Theme.textAreaScroll(safety), 2);
        if (!Modal.form(this, "Edit food #" + f.getFoodId(), null, form.panel(), "Save changes")) return;
        final LocalDateTime p, x;
        try {
            p = Ui.parseDateTime(prep.getText(), false);
            x = Ui.parseDateTime(expiry.getText(), false);
        } catch (IllegalArgumentException ex) {
            Ui.warn(this, ex.getMessage());
            return;
        }
        final String cat = String.valueOf(category.getEditor().getItem());
        final int q = ((Number) qty.getValue()).intValue();
        Ui.run(this, () -> foods.updateFood(donor, f.getFoodId(), name.getText(), cat, q, p, x, (String) pickupArea.getSelectedItem(),
            location.getText(), safety.getText()),
            updated -> {
                Ui.success(this, "Post updated", "Food #" + updated.getFoodId() + " saved. Priority is now " + updated.getPriorityLevel() + ".");
                loadPosts();
            });
    }

    private void loadPosts() { Ui.run(this, () -> foods.myPosts(donor), postsTable::setItems); }

    private JPanel requestsPage() {
        JButton approve = Theme.button("Approve", Theme.Kind.PRIMARY);
        JButton reject = Theme.button("Reject", Theme.Kind.DANGER);
        JButton code = Theme.button("Show pickup code", Theme.Kind.SECONDARY);
        JButton done = Theme.button("Mark completed", Theme.Kind.SECONDARY);

        approve.addActionListener(e -> {
            final Request r = pick();
            if (r == null) return;
            final String slot = Ui.askSlot(this, "The receiver asked for " + r.getTimeSlot() + ".\nConfirm it, or type another slot to counter-propose.", r.getTimeSlot());
            if (slot == null) return;
            Ui.run(this, () -> requests.approve(donor, r.getRequestId(), slot), c -> {
                Ui.showCode(this, "Request #" + r.getRequestId() + " approved and the food is reserved.\n"
                    + "Give this code to the receiver at handover.", c);
                loadRequests();
            });
        });
        reject.addActionListener(e -> {
            final Request r = pick();
            if (r == null || !Ui.confirmDanger(this, "Reject this request?", "Request #" + r.getRequestId() + " will be rejected and the food becomes Available again.", "Reject", "Keep")) return;
            Ui.run(this, () -> { requests.reject(donor, r.getRequestId()); return Boolean.TRUE; }, ok -> loadRequests());
        });
        code.addActionListener(e -> {
            final Request r = pick();
            if (r == null) return;
            Ui.run(this, () -> requests.pickupFor(donor, r.getRequestId()), (Pickup p) ->
                Ui.showCode(this, "Request #" + r.getRequestId() + "  \u00B7  status: " + p.getStatus(), p.getVerificationCode()));
        });
        done.addActionListener(e -> {
            final Request r = pick();
            if (r == null || !Ui.confirm(this, "Mark as completed?", "Request #" + r.getRequestId() + " will be closed as Completed.", "Mark completed")) return;
            Ui.run(this, () -> { requests.complete(donor, r.getRequestId()); return Boolean.TRUE; }, ok -> loadRequests());
        });
        return Theme.tablePanel(reqTable, approve, reject, code, done);
    }

    private Request pick() {
        Request r = reqTable.selected();
        if (r == null) Ui.hint(this, "Select a request in the table first.");
        return r;
    }

    private void loadRequests() { Ui.run(this, () -> requests.forDonor(donor), reqTable::setItems); }

    private JPanel nearPage() {
        nearFood.addActionListener(e -> { if (!nearLoading) loadNearTable(); });
        return Theme.tablePanel(nearTable, Theme.muted("Match for"), nearFood);
    }

    private void loadNear() {
        Ui.run(this, () -> foods.myPosts(donor), posts -> {
            Object keep = nearFood.getSelectedItem();
            nearLoading = true;
            nearFood.removeAllItems();
            nearFood.addItem(ALL_RECEIVERS);
            for (Food f : posts)
                if (Constants.FOOD_AVAILABLE.equals(f.getStatus()) || Constants.FOOD_REQUESTED.equals(f.getStatus()))
                    nearFood.addItem(new FoodItem(f));
            nearFood.setSelectedItem(ALL_RECEIVERS);
            if (keep instanceof FoodItem)
                for (int i = 0; i < nearFood.getItemCount(); i++)
                    if (nearFood.getItemAt(i) instanceof FoodItem && ((FoodItem) nearFood.getItemAt(i)).food.getFoodId() == ((FoodItem) keep).food.getFoodId())
                        nearFood.setSelectedIndex(i);
            nearLoading = false;
            loadNearTable();
        });
    }

    /** Receivers in my area; for a selected food item only those whose capacity can handle its quantity. */
    private void loadNearTable() {
        Object sel = nearFood.getSelectedItem();
        final Integer foodId = sel instanceof FoodItem ? Integer.valueOf(((FoodItem) sel).food.getFoodId()) : null;
        Ui.run(this, () -> foods.nearbyReceivers(donor, foodId), nearTable::setItems);
    }

    private static final class FoodItem {
        final Food food;
        FoodItem(Food f) { this.food = f; }
        @Override public String toString() { return "#" + food.getFoodId() + " " + food.getFoodName() + " (qty " + food.getQuantity() + ")"; }
    }
}
