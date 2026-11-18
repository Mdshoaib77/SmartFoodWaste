package com.foodwaste.ui;

import com.foodwaste.model.Admin;
import com.foodwaste.service.AdminService;
import com.foodwaste.service.ExpiryService;
import com.foodwaste.service.MessageService;
import com.foodwaste.service.ServiceException;
import com.foodwaste.util.Constants;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Administrator dashboard: NGO verification, monitoring with filters, suspicious requests, messages, admins, logs, reports. */
public class AdminMenu {
    private static final List<String> STATUSES = Arrays.asList("Requested", "Reserved", "Rejected", "Picked Up", "Completed",
        "Expired", "Cancelled");
    private final Admin admin;
    private final AdminService service = new AdminService();
    private final MessageService messages = new MessageService();

    public AdminMenu(Admin admin) { this.admin = admin; }

    public void show() {
        if (admin.isMustChangePassword()) {
            ConsoleIO.header("SECURITY WARNING");
            ConsoleIO.error("You are still using the default administrator password. Please change it now (option 17).");
        }
        while (true) {
            ExpiryService.sync();
            ConsoleIO.header("ADMINISTRATOR MENU  |  " + admin.getUsername());
            System.out.println(" 1. Pending NGO / Receiver accounts");
            System.out.println(" 2. Approve an NGO / Receiver");
            System.out.println(" 3. Reject an NGO / Receiver");
            System.out.println(" 4. All users");
            System.out.println(" 5. All food records");
            System.out.println(" 6. Monitor interactions (filter by status / area / date)");
            System.out.println(" 7. Suspicious requests");
            System.out.println(" 8. Flag / unflag a request as suspicious");
            System.out.println(" 9. Cancel a request");
            System.out.println("10. Activity log");
            System.out.println("11. Distribution & waste-reduction report (text)");
            System.out.println("12. Run expiry / stale-request check now");
            System.out.println("13. Send a message to one user");
            System.out.println("14. Broadcast a message (all donors / receivers / everyone)");
            System.out.println("15. Create another administrator account");
            System.out.println("16. List administrator accounts");
            System.out.println("17. Change my password");
            System.out.println("18. Export reports as CSV");
            System.out.println(" 0. Logout");
            int choice = ConsoleIO.askInt("Choose an option");
            try {
                switch (choice) {
                    case 1: Views.users(service.pendingReceivers(admin)); break;
                    case 2: verify(true); break;
                    case 3: verify(false); break;
                    case 4: Views.users(service.allUsers(admin)); break;
                    case 5: Views.foods(service.allFood(admin)); break;
                    case 6: monitor(); break;
                    case 7: Views.requests(service.suspiciousRequests(admin)); break;
                    case 8: flag(); break;
                    case 9: cancel(); break;
                    case 10: Views.logs(service.recentLogs(admin, ConsoleIO.askPositiveInt("How many recent entries"))); break;
                    case 11: report(); break;
                    case 12: ConsoleIO.success(ExpiryService.sync() + " food item(s) newly marked Expired."); break;
                    case 13: sendOne(); break;
                    case 14: broadcast(); break;
                    case 15: createAdmin(); break;
                    case 16: Views.admins(service.allAdmins(admin)); break;
                    case 17: AccountMenu.changePassword(admin); admin.setMustChangePassword(false); break;
                    case 18: csv(); break;
                    case 0: return;
                    default: ConsoleIO.error("Invalid option.");
                }
            } catch (ServiceException e) {
                ConsoleIO.error(e.getMessage());
            }
        }
    }

    private void monitor() {
        ConsoleIO.info("Leave a filter blank to skip it.");
        String status = ConsoleIO.choose("Status", STATUSES, true);
        String area = ConsoleIO.choose("Area (donor or receiver)", service.areas(admin), true);
        LocalDate from = ConsoleIO.askDateOrBlank("From date");
        LocalDate to = ConsoleIO.askDateOrBlank("To date");
        boolean susp = ConsoleIO.confirm("Only suspicious requests?");
        Views.requests(service.interactions(admin, status, area, from, to, susp));
    }

    private void verify(boolean approve) {
        Views.users(service.pendingReceivers(admin));
        int id = ConsoleIO.askInt("User ID to " + (approve ? "approve" : "reject"));
        service.verifyReceiver(admin, id, approve);
        ConsoleIO.success("User #" + id + (approve ? " approved." : " rejected."));
    }

    private void flag() {
        Views.requests(service.allRequests(admin));
        int id = ConsoleIO.askInt("Request ID");
        boolean flag = ConsoleIO.confirm("Flag as suspicious? (n = remove the flag)");
        service.flagRequest(admin, id, flag);
        ConsoleIO.success("Request #" + id + (flag ? " flagged." : " unflagged."));
    }

    private void cancel() {
        Views.requests(service.allRequests(admin));
        int id = ConsoleIO.askInt("Request ID to cancel");
        if (ConsoleIO.confirm("Cancel request #" + id + " and release the food?")) {
            service.cancelRequest(admin, id);
            ConsoleIO.success("Request #" + id + " cancelled.");
        }
    }

    private void sendOne() {
        Views.users(service.allUsers(admin));
        int id = ConsoleIO.askInt("Recipient user ID");
        String subject = ConsoleIO.askRequired("Subject");
        String body = ConsoleIO.askRequired("Message");
        messages.sendToUser(admin, id, subject, body);
        ConsoleIO.success("Message sent to user #" + id + ".");
    }

    private void broadcast() {
        List<String> aud = new ArrayList<>(Arrays.asList(Constants.AUDIENCE_DONORS, Constants.AUDIENCE_RECEIVERS, Constants.AUDIENCE_EVERYONE));
        String audience = ConsoleIO.choose("Audience", aud, false);
        String subject = ConsoleIO.askRequired("Subject");
        String body = ConsoleIO.askRequired("Message");
        int n = messages.broadcast(admin, audience, subject, body);
        ConsoleIO.success("Message delivered to " + n + " user(s).");
    }

    private void createAdmin() {
        String username = ConsoleIO.askRequired("New administrator username");
        String pw = ConsoleIO.askPassword("Password (min 6 characters)");
        if (!pw.equals(ConsoleIO.askPassword("Confirm password"))) { ConsoleIO.error("Passwords do not match."); return; }
        ConsoleIO.success("Administrator '" + service.createAdmin(admin, username, pw).getUsername() + "' created.");
    }

    private void report() {
        String text = service.generateReport(admin);
        System.out.println();
        System.out.print(text);
        if (ConsoleIO.confirm("Save this report to a file?")) {
            Path p = service.saveReport(admin, text);
            ConsoleIO.success("Saved to " + p.toAbsolutePath());
        }
    }

    private void csv() {
        for (Path p : service.exportCsv(admin)) ConsoleIO.success("Saved " + p.toAbsolutePath());
    }
}
