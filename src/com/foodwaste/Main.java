package com.foodwaste;

import com.foodwaste.config.DBConnection;
import com.foodwaste.model.Admin;
import com.foodwaste.model.User;
import com.foodwaste.service.AuthService;
import com.foodwaste.service.ExpiryService;
import com.foodwaste.service.ServiceException;
import com.foodwaste.ui.AdminMenu;
import com.foodwaste.ui.ConsoleIO;
import com.foodwaste.ui.DonorMenu;
import com.foodwaste.ui.ReceiverMenu;
import com.foodwaste.util.Constants;
import java.sql.SQLException;

/** Entry point: Smart Food Waste Sharing and Priority-Based Distribution System (console edition). */
public class Main {
    private static final AuthService AUTH = new AuthService();

    public static void main(String[] args) {
        System.out.println("==============================================================");
        System.out.println("  Smart Food Waste Sharing & Priority-Based Distribution System");
        System.out.println("==============================================================");
        try {
            DBConnection.test();
        } catch (SQLException e) {
            System.out.println("Cannot connect to MySQL: " + e.getMessage());
            System.out.println("Check db.properties (URL / user / password), that MySQL is running,");
            System.out.println("that database.sql was executed, and that the MySQL Connector/J jar is in lib/.");
            System.out.println("Using: " + DBConnection.describe());
            return;
        }
        try {
            if (AUTH.ensureDefaultAdmin())
                System.out.println("First run: the default administrator account was created (see README.md). Change its password after login.");
        } catch (ServiceException e) {
            System.out.println("Database is not initialised: " + e.getMessage() + "\nRun database.sql first.");
            return;
        }
        ExpiryService.sync();
        ExpiryService.start();

        while (true) {
            ConsoleIO.header("MAIN MENU");
            System.out.println(" 1. Login (Donor / Receiver-NGO)");
            System.out.println(" 2. Administrator login");
            System.out.println(" 3. Register new account");
            System.out.println(" 0. Exit");
            int choice = ConsoleIO.askInt("Choose an option");
            try {
                switch (choice) {
                    case 1: userLogin(); break;
                    case 2: adminLogin(); break;
                    case 3: register(); break;
                    case 0:
                        ExpiryService.stop();
                        System.out.println("Goodbye.");
                        return;
                    default: ConsoleIO.error("Invalid option.");
                }
            } catch (ServiceException e) {
                ConsoleIO.error(e.getMessage());
            }
        }
    }

    private static void userLogin() {
        ConsoleIO.header("LOGIN");
        String contact = ConsoleIO.askRequired("Contact (phone / e-mail)");
        String password = ConsoleIO.askPassword("Password");
        User u = AUTH.loginUser(contact, password);
        ConsoleIO.success("Welcome, " + u.getName() + " (" + u.getRole() + ")");
        if (Constants.ROLE_DONOR.equals(u.getRole())) new DonorMenu(u).show();
        else new ReceiverMenu(u).show();
    }

    private static void adminLogin() {
        ConsoleIO.header("ADMINISTRATOR LOGIN");
        String username = ConsoleIO.askRequired("Username");
        String password = ConsoleIO.askPassword("Password");
        Admin a = AUTH.loginAdmin(username, password);
        ConsoleIO.success("Welcome, " + a.getUsername());
        new AdminMenu(a).show();
    }

    private static void register() {
        ConsoleIO.header("REGISTER");
        String name = ConsoleIO.askRequired("Full / organisation name");
        String contact = ConsoleIO.askRequired("Contact (phone / e-mail) - used to log in");
        String password = ConsoleIO.askPassword("Password (min 6 characters)");
        String confirm = ConsoleIO.askPassword("Confirm password");
        if (!password.equals(confirm)) { ConsoleIO.error("Passwords do not match."); return; }
        ConsoleIO.info("Choose your area (used for matching):");
        String area = ConsoleIO.choose("Area", AUTH.areas(), false);
        String role;
        while (true) {
            int r = ConsoleIO.askInt("Role: 1 = Donor, 2 = Receiver/NGO");
            if (r == 1) { role = Constants.ROLE_DONOR; break; }
            if (r == 2) { role = Constants.ROLE_RECEIVER; break; }
            ConsoleIO.error("Enter 1 or 2.");
        }
        Integer capacity = null;
        if (Constants.ROLE_RECEIVER.equals(role))
            capacity = ConsoleIO.askPositiveInt("Capacity (maximum quantity you can take for one food item)");
        User u = AUTH.register(name, contact, password, role, area, capacity);
        if (Constants.USER_PENDING.equals(u.getStatus()))
            ConsoleIO.success("Registered (user #" + u.getUserId() + "). Your account is PENDING until an administrator approves it.");
        else
            ConsoleIO.success("Registered (user #" + u.getUserId() + "). You can log in now.");
    }
}
