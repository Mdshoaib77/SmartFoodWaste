package com.foodwaste.gui;

import com.foodwaste.config.DBConnection;
import com.foodwaste.service.AuthService;
import com.foodwaste.service.ExpiryService;
import com.foodwaste.service.ServiceException;
import java.sql.SQLException;
import javax.swing.SwingUtilities;

/** Entry point of the Swing edition (the console edition is com.foodwaste.Main). */
public final class GuiMain {
    private GuiMain() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GuiMain::start);
    }

    private static void start() {
        Theme.install();
        try {
            DBConnection.test();
        } catch (SQLException e) {
            fatal("Cannot connect to MySQL:\n" + e.getMessage()
                + "\n\nCheck db.properties (URL / user / password), that MySQL is running,\n"
                + "that database.sql was executed, and that the MySQL Connector/J jar is in lib/.\n\nUsing: " + DBConnection.describe());
            return;
        }
        try {
            new AuthService().ensureDefaultAdmin();
        } catch (ServiceException e) {
            fatal("Database is not initialised: " + e.getMessage() + "\nRun database.sql first.");
            return;
        }
        ExpiryService.sync();
        ExpiryService.start();
        new LoginFrame().setVisible(true);
    }

    private static void fatal(String message) {
        Modal.alert(null, Modal.Type.ERROR, "Cannot start", message);
        System.exit(1);
    }
}
