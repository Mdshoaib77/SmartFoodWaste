package com.foodwaste.ui;

import com.foodwaste.model.Request;
import com.foodwaste.model.User;
import com.foodwaste.service.ExpiryService;
import com.foodwaste.service.FoodService;
import com.foodwaste.service.RequestService;
import com.foodwaste.service.ServiceException;

/** Receiver / NGO dashboard: browse by priority + capacity, request, cancel, confirm pickup, inbox. */
public class ReceiverMenu {
    private final User receiver;
    private final FoodService foodService = new FoodService();
    private final RequestService requestService = new RequestService();

    public ReceiverMenu(User receiver) { this.receiver = receiver; }

    public void show() {
        while (true) {
            ExpiryService.sync();
            ConsoleIO.header("RECEIVER / NGO MENU  |  " + receiver.getName() + "  |  Area: " + receiver.getAreaId()
                + "  |  Capacity: " + receiver.getCapacity());
            System.out.println(" 1. Browse available food in MY area (urgent first)");
            System.out.println(" 2. Browse available food in ALL areas (urgent + my area first)");
            System.out.println(" 3. Browse only food that FITS my capacity");
            System.out.println(" 4. Request a food item");
            System.out.println(" 5. My requests / interaction history");
            System.out.println(" 6. Confirm pickup with verification code");
            System.out.println(" 7. Cancel one of my requests (Requested or Reserved)");
            System.out.println(" 8. " + AccountMenu.inboxLabel(receiver));
            System.out.println(" 9. Change password");
            System.out.println(" 0. Logout");
            int choice = ConsoleIO.askInt("Choose an option");
            try {
                switch (choice) {
                    case 1: Views.foods(foodService.availableFor(receiver, true, false), receiver); break;
                    case 2: Views.foods(foodService.availableFor(receiver, false, false), receiver); break;
                    case 3: Views.foods(foodService.availableFor(receiver, false, true), receiver); break;
                    case 4: requestFood(); break;
                    case 5: Views.requests(requestService.forReceiver(receiver)); break;
                    case 6: confirmPickup(); break;
                    case 7: cancel(); break;
                    case 8: AccountMenu.inbox(receiver); break;
                    case 9: AccountMenu.changePassword(receiver); break;
                    case 0: return;
                    default: ConsoleIO.error("Invalid option.");
                }
            } catch (ServiceException e) {
                ConsoleIO.error(e.getMessage());
            }
        }
    }

    private void requestFood() {
        Views.foods(foodService.availableFor(receiver, false, false), receiver);
        ConsoleIO.info("Food marked TOO LARGE cannot be requested: your capacity is " + receiver.getCapacity() + ".");
        int foodId = ConsoleIO.askInt("Food ID to request");
        String slot = ConsoleIO.askSlot("Preferred pickup time slot", false);
        String notes = ConsoleIO.ask("Notes for the donor (optional)");
        Request r = requestService.submit(receiver, foodId, slot, notes);
        ConsoleIO.success("Request #" + r.getRequestId() + " submitted. Waiting for the donor's decision.");
        if (r.isSuspicious()) ConsoleIO.info("Note: you hold several open requests, so this one was flagged for administrator review.");
    }

    private void cancel() {
        Views.requests(requestService.forReceiver(receiver));
        int id = ConsoleIO.askInt("Request ID to cancel");
        if (ConsoleIO.confirm("Cancel request #" + id + "? The food will become Available for others.")) {
            requestService.cancelByReceiver(receiver, id);
            ConsoleIO.success("Request #" + id + " cancelled; the donor was notified.");
        }
    }

    private void confirmPickup() {
        Views.requests(requestService.forReceiver(receiver));
        int id = ConsoleIO.askInt("Request ID being collected");
        String code = ConsoleIO.askRequired("Verification code given by the donor");
        requestService.confirmPickup(receiver, id, code);
        ConsoleIO.success("Pickup confirmed for request #" + id + ". The donor can now mark it Completed.");
    }
}
