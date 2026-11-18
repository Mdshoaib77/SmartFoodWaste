package com.foodwaste.ui;

import com.foodwaste.model.Food;
import com.foodwaste.model.Pickup;
import com.foodwaste.model.User;
import com.foodwaste.service.ExpiryService;
import com.foodwaste.service.FoodService;
import com.foodwaste.service.RequestService;
import com.foodwaste.service.ServiceException;
import java.time.LocalDateTime;

/** Donor dashboard: post/edit/delete food, review requests, approve (with slot) / reject, hand over the code, inbox. */
public class DonorMenu {
    private final User donor;
    private final FoodService foodService = new FoodService();
    private final RequestService requestService = new RequestService();

    public DonorMenu(User donor) { this.donor = donor; }

    public void show() {
        while (true) {
            ExpiryService.sync();
            ConsoleIO.header("DONOR MENU  |  " + donor.getName() + "  |  Area: " + donor.getAreaId());
            System.out.println(" 1. Post surplus food");
            System.out.println(" 2. My food posts");
            System.out.println(" 3. Incoming requests");
            System.out.println(" 4. Approve a request (confirm or counter-propose the time slot)");
            System.out.println(" 5. Reject a request");
            System.out.println(" 6. Show pickup code of a reserved request");
            System.out.println(" 7. Mark a picked-up request as Completed");
            System.out.println(" 8. Nearby Receivers/NGOs (area + capacity; optionally for one food item)");
            System.out.println(" 9. Edit one of my food posts (only while Available)");
            System.out.println("10. Delete one of my food posts (only while Available)");
            System.out.println("11. " + AccountMenu.inboxLabel(donor));
            System.out.println("12. Change password");
            System.out.println(" 0. Logout");
            int choice = ConsoleIO.askInt("Choose an option");
            try {
                switch (choice) {
                    case 1: postFood(); break;
                    case 2: Views.foods(foodService.myPosts(donor)); break;
                    case 3: Views.requests(requestService.forDonor(donor)); break;
                    case 4: approve(); break;
                    case 5: reject(); break;
                    case 6: showCode(); break;
                    case 7: complete(); break;
                    case 8: nearby(); break;
                    case 9: edit(); break;
                    case 10: delete(); break;
                    case 11: AccountMenu.inbox(donor); break;
                    case 12: AccountMenu.changePassword(donor); break;
                    case 0: return;
                    default: ConsoleIO.error("Invalid option.");
                }
            } catch (ServiceException e) {
                ConsoleIO.error(e.getMessage());
            }
        }
    }

    private void postFood() {
        ConsoleIO.header("POST SURPLUS FOOD");
        String name = ConsoleIO.askRequired("Food name");
        String category = ConsoleIO.askRequired("Category (e.g. Cooked meal, Bakery, Fruit, Packaged)");
        int qty = ConsoleIO.askPositiveInt("Quantity (portions / units)");
        LocalDateTime prep = ConsoleIO.askDateTime("Preparation time", true);
        LocalDateTime expiry = ConsoleIO.askDateTime("Expiry time", false);
        ConsoleIO.info("Choose the PICKUP AREA (district where the food is collected; used for matching):");
        String area = ConsoleIO.chooseArea("Pickup area", foodService.areas(), donor.getAreaId());
        String location = ConsoleIO.askRequired("Pickup address detail (house / road / landmark)");
        String safety = ConsoleIO.ask("Safety information (storage, allergens - optional)");
        Food f = foodService.postFood(donor, name, category, qty, prep, expiry, area, location, safety);
        ConsoleIO.success("Food posted. Food ID " + f.getFoodId() + ", priority " + f.getPriorityLevel() + ".");
        int fit = foodService.nearbyReceivers(donor, f.getFoodId()).size();
        ConsoleIO.info(fit + " approved Receiver/NGO account(s) in pickup area '" + f.getAreaId() + "' have enough capacity for this food.");
    }

    private void edit() {
        Views.foods(foodService.myPosts(donor));
        int id = ConsoleIO.askInt("Food ID to edit");
        ConsoleIO.info("Enter the new values (all fields are asked again).");
        String name = ConsoleIO.askRequired("Food name");
        String category = ConsoleIO.askRequired("Category");
        int qty = ConsoleIO.askPositiveInt("Quantity");
        LocalDateTime prep = ConsoleIO.askDateTime("Preparation time", true);
        LocalDateTime expiry = ConsoleIO.askDateTime("Expiry time", false);
        String area = ConsoleIO.chooseArea("Pickup area", foodService.areas(), donor.getAreaId());
        String location = ConsoleIO.askRequired("Pickup address detail (house / road / landmark)");
        String safety = ConsoleIO.ask("Safety information (optional)");
        Food f = foodService.updateFood(donor, id, name, category, qty, prep, expiry, area, location, safety);
        ConsoleIO.success("Food #" + id + " updated. Priority is now " + f.getPriorityLevel() + ".");
    }

    private void delete() {
        Views.foods(foodService.myPosts(donor));
        int id = ConsoleIO.askInt("Food ID to delete");
        if (ConsoleIO.confirm("Delete food #" + id + " permanently?")) {
            foodService.deleteFood(donor, id);
            ConsoleIO.success("Food #" + id + " deleted.");
        }
    }

    private void nearby() {
        Views.foods(foodService.myPosts(donor));
        String s = ConsoleIO.ask("Food ID to match (blank = every receiver in my registered area)");
        if (s.isEmpty()) {
            Views.users(foodService.nearbyReceivers(donor));
            return;
        }
        try {
            Views.users(foodService.nearbyReceivers(donor, Integer.valueOf(s)));
        } catch (NumberFormatException e) {
            ConsoleIO.error("Please enter a whole number.");
        }
    }

    private void approve() {
        Views.requests(requestService.forDonor(donor));
        int id = ConsoleIO.askInt("Request ID to approve");
        String slot = ConsoleIO.askSlot("Pickup slot to confirm (type a different slot to counter-propose)", true);
        String code = requestService.approve(donor, id, slot);
        ConsoleIO.success("Request #" + id + " approved and food reserved.");
        ConsoleIO.info("PICKUP VERIFICATION CODE: " + code + "  (give it to the receiver at handover)");
    }

    private void reject() {
        Views.requests(requestService.forDonor(donor));
        int id = ConsoleIO.askInt("Request ID to reject");
        if (ConsoleIO.confirm("Reject request #" + id + "?")) {
            requestService.reject(donor, id);
            ConsoleIO.success("Request #" + id + " rejected; the food is Available again.");
        }
    }

    private void showCode() {
        int id = ConsoleIO.askInt("Request ID");
        Pickup p = requestService.pickupFor(donor, id);
        ConsoleIO.info("Request #" + id + "  pickup status: " + p.getStatus() + "  code: " + p.getVerificationCode());
    }

    private void complete() {
        int id = ConsoleIO.askInt("Request ID to complete");
        requestService.complete(donor, id);
        ConsoleIO.success("Request #" + id + " marked Completed.");
    }
}
