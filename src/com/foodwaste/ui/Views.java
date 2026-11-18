package com.foodwaste.ui;

import com.foodwaste.model.ActivityLog;
import com.foodwaste.model.Admin;
import com.foodwaste.model.Food;
import com.foodwaste.model.Message;
import com.foodwaste.model.Request;
import com.foodwaste.model.User;
import com.foodwaste.service.FoodService;
import com.foodwaste.util.Fmt;
import com.foodwaste.util.TextTable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Turns model objects into console tables. */
public final class Views {
    private Views() { }

    public static void foods(List<Food> list) { foods(list, null); }

    /** When a receiver is given, an extra column marks food larger than that receiver's capacity. */
    public static void foods(List<Food> list, User receiver) {
        if (list.isEmpty()) { ConsoleIO.info("No food records found."); return; }
        LocalDateTime now = LocalDateTime.now();
        List<String[]> rows = new ArrayList<>();
        List<String> head = new ArrayList<>(java.util.Arrays.asList("ID", "Food", "Category", "Qty", "Priority", "Status", "Expires",
            "Left", "Address detail", "Pickup area", "Donor", "Safety"));
        if (receiver != null) {
            head.add("Nearby");
            head.add("Capacity");
        }
        rows.add(head.toArray(new String[0]));
        for (Food f : list) {
            List<String> row = new ArrayList<>(java.util.Arrays.asList(String.valueOf(f.getFoodId()), f.getFoodName(), f.getCategory(),
                String.valueOf(f.getQuantity()), f.getPriorityLevel(), f.getStatus(), Fmt.dt(f.getExpiryTime()),
                Fmt.remaining(f.getExpiryTime(), now), f.getLocation(), f.getAreaId(), f.getDonorName(), Fmt.nz(f.getSafetyInfo())));
            if (receiver != null) {
                row.add(com.foodwaste.service.Check.areaKey(f.getAreaId()).equals(com.foodwaste.service.Check.areaKey(receiver.getAreaId()))
                    ? "YES" : "-");
                row.add(FoodService.exceedsCapacity(f, receiver) ? "TOO LARGE (max " + receiver.getCapacity() + ")" : "ok");
            }
            rows.add(row.toArray(new String[0]));
        }
        System.out.print(TextTable.render(rows));
    }

    public static void requests(List<Request> list) {
        if (list.isEmpty()) { ConsoleIO.info("No requests found."); return; }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Req", "Food#", "Food", "Donor", "Pickup area", "Receiver/NGO", "Recv. area", "Wanted slot", "Approved slot",
            "Status", "Flag", "Requested", "Updated", "Picked up", "Notes"});
        for (Request r : list) {
            rows.add(new String[] {String.valueOf(r.getRequestId()), String.valueOf(r.getFoodId()), r.getFoodName(), r.getDonorName(),
                Fmt.nz(r.getPickupArea()), r.getReceiverName(), Fmt.nz(r.getReceiverArea()), r.getTimeSlot(), Fmt.nz(r.getApprovedSlot()),
                r.getStatus(), r.isSuspicious() ? "SUSPICIOUS" : "", Fmt.dt(r.getRequestedAt()), Fmt.dt(r.getUpdatedAt()),
                r.getPickupTime() == null ? "-" : Fmt.dt(r.getPickupTime()), Fmt.nz(r.getNotes())});
        }
        System.out.print(TextTable.render(rows));
    }

    public static void users(List<User> list) {
        if (list.isEmpty()) { ConsoleIO.info("No users found."); return; }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"ID", "Name", "Contact", "Role", "Area", "Capacity", "Status", "Verified by", "Registered"});
        for (User u : list) {
            rows.add(new String[] {String.valueOf(u.getUserId()), u.getName(), u.getContact(), u.getRole(), u.getAreaId(),
                u.getCapacity() == null ? "-" : String.valueOf(u.getCapacity()), u.getStatus(),
                u.getVerifiedBy() == null ? "-" : "Admin #" + u.getVerifiedBy(), Fmt.dt(u.getCreatedAt())});
        }
        System.out.print(TextTable.render(rows));
    }

    public static void admins(List<Admin> list) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"ID", "Username", "Created by", "Created", "Default password still active"});
        for (Admin a : list)
            rows.add(new String[] {String.valueOf(a.getAdminId()), a.getUsername(), a.getCreatedBy() == null ? "-" : "Admin #" + a.getCreatedBy(),
                Fmt.dt(a.getCreatedAt()), a.isMustChangePassword() ? "YES" : "no"});
        System.out.print(TextTable.render(rows));
    }

    public static void messages(List<Message> list) {
        if (list.isEmpty()) { ConsoleIO.info("Your inbox is empty."); return; }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"ID", "", "Received", "From", "Subject", "Message"});
        for (Message m : list)
            rows.add(new String[] {String.valueOf(m.getMessageId()), m.isRead() ? "" : "NEW", Fmt.dt(m.getCreatedAt()), m.getSenderName(),
                m.getSubject(), m.getBody()});
        System.out.print(TextTable.render(rows));
    }

    public static void logs(List<ActivityLog> list) {
        if (list.isEmpty()) { ConsoleIO.info("No activity recorded."); return; }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Log", "Time", "Actor", "Action", "Details"});
        for (ActivityLog l : list) {
            rows.add(new String[] {String.valueOf(l.getLogId()), Fmt.dt(l.getLoggedAt()),
                l.getActorType() + (l.getActorId() == null ? "" : " #" + l.getActorId()), l.getAction(), Fmt.nz(l.getDetails())});
        }
        System.out.print(TextTable.render(rows));
    }
}
