package com.foodwaste.service;

import com.foodwaste.dao.ActivityLogDAO;
import com.foodwaste.dao.FoodDAO;
import com.foodwaste.dao.PickupDAO;
import com.foodwaste.dao.RequestDAO;
import com.foodwaste.model.Food;
import com.foodwaste.model.Pickup;
import com.foodwaste.model.Request;
import com.foodwaste.model.User;
import com.foodwaste.util.Constants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FR-06 / FR-07 / FR-08: request -> approve (with slot) / reject -> verification code -> pickup -> completion, plus
 * cancellation. Every state change is a guarded UPDATE inside one transaction, so two users can never win the same food.
 */
public class RequestService {
    private static final SecureRandom RNG = new SecureRandom();
    private final FoodDAO foodDAO = new FoodDAO();
    private final RequestDAO requestDAO = new RequestDAO();
    private final PickupDAO pickupDAO = new PickupDAO();
    private final ActivityLogDAO logDAO = new ActivityLogDAO();

    /** Available -> Requested. Blocks (does not just warn) when the food is larger than the receiver's capacity. */
    public Request submit(User receiver, int foodId, String timeSlot, String notes) {
        final String slot = Check.slot(timeSlot);
        final String note = Check.optional(notes, "Notes", 500);
        return Tx.tx(c -> {
            User rec = Guard.user(c, receiver, Constants.ROLE_RECEIVER);
            Food f = foodDAO.findById(c, foodId);
            if (f == null) throw new ServiceException("Food ID " + foodId + " does not exist.");
            if (!f.getExpiryTime().isAfter(LocalDateTime.now())) throw new ServiceException("This food has already expired.");
            if (rec.getCapacity() != null && f.getQuantity() > rec.getCapacity())
                throw new ServiceException("Request blocked: food #" + foodId + " has quantity " + f.getQuantity()
                    + " but your capacity is " + rec.getCapacity() + ".");
            if (foodDAO.transition(c, foodId, Constants.FOOD_AVAILABLE, Constants.FOOD_REQUESTED) == 0)
                throw new ServiceException("Food #" + foodId + " is no longer available (status: " + f.getStatus() + ").");
            Request r = new Request();
            r.setFoodId(foodId); r.setUserId(rec.getUserId()); r.setTimeSlot(slot);
            r.setNotes(note.isEmpty() ? null : note); r.setStatus(Constants.REQ_REQUESTED);
            r.setSuspicious(requestDAO.countOpenByReceiver(c, rec.getUserId()) >= Constants.OPEN_REQUEST_FLAG_THRESHOLD);
            r.setRequestId(requestDAO.insert(c, r));
            logDAO.insert(c, "User", rec.getUserId(), "REQUEST_SUBMITTED", "Request #" + r.getRequestId()
                + " for food #" + foodId + (r.isSuspicious() ? " (auto-flagged: many open requests)" : ""));
            Notifier.send(c, f.getUserId(), "New pickup request #" + r.getRequestId(),
                rec.getName() + " asked for '" + f.getFoodName() + "' (food #" + foodId + "), preferred slot " + slot + ". Please approve or reject it.");
            return r;
        });
    }

    /** Requested -> Reserved using the receiver's preferred slot. */
    public String approve(User donor, int requestId) {
        return approve(donor, requestId, null);
    }

    /**
     * Requested -> Reserved. The donor may counter-propose a pickup slot (HH:mm-HH:mm); it is stored on the request and
     * shown to the receiver. A null/blank slot keeps the receiver's preferred slot. Returns the 6-digit verification code.
     */
    public String approve(User donor, int requestId, String approvedSlot) {
        final boolean counter = approvedSlot != null && !approvedSlot.trim().isEmpty();
        final String counterSlot = counter ? Check.slot(approvedSlot) : null;
        return Tx.tx(c -> {
            User d = Guard.user(c, donor, Constants.ROLE_DONOR);
            Request r = ownedByDonor(c, d, requestId);
            Food f = foodDAO.findById(c, r.getFoodId());
            if (!f.getExpiryTime().isAfter(LocalDateTime.now())) throw new ServiceException("This food has already expired.");
            String slot = counter ? counterSlot : r.getTimeSlot();
            if (requestDAO.approve(c, requestId, slot) == 0)
                throw new ServiceException("Request #" + requestId + " is not waiting for approval (status: " + r.getStatus() + ").");
            if (foodDAO.transition(c, f.getFoodId(), Constants.FOOD_REQUESTED, Constants.FOOD_RESERVED) == 0)
                throw new ServiceException("Food status is inconsistent; the request cannot be approved.");
            String code = String.format("%06d", RNG.nextInt(1_000_000));
            pickupDAO.insert(c, requestId, code);
            logDAO.insert(c, "User", d.getUserId(), "REQUEST_APPROVED",
                "Request #" + requestId + " approved, slot " + slot + "; verification code generated");
            Notifier.send(c, r.getUserId(), "Request #" + requestId + " approved",
                "Your request for '" + r.getFoodName() + "' was approved by " + d.getName() + ". Pickup slot: " + slot
                + (counter && !slot.equals(r.getTimeSlot()) ? " (the donor changed your preferred slot " + r.getTimeSlot() + ")" : "")
                + ". The donor will give you the verification code at handover.");
            return code;
        });
    }

    /** Requested -> Rejected and the food becomes Available again. */
    public void reject(User donor, int requestId) {
        Tx.tx(c -> {
            User d = Guard.user(c, donor, Constants.ROLE_DONOR);
            Request r = ownedByDonor(c, d, requestId);
            if (requestDAO.transition(c, requestId, Constants.REQ_REQUESTED, Constants.REQ_REJECTED) == 0)
                throw new ServiceException("Request #" + requestId + " is not waiting for approval (status: " + r.getStatus() + ").");
            foodDAO.transition(c, r.getFoodId(), Constants.FOOD_REQUESTED, Constants.FOOD_AVAILABLE);
            logDAO.insert(c, "User", d.getUserId(), "REQUEST_REJECTED", "Request #" + requestId + " rejected");
            Notifier.send(c, r.getUserId(), "Request #" + requestId + " rejected",
                "Your request for '" + r.getFoodName() + "' was rejected by the donor. You can request other food.");
            return null;
        });
    }

    /** Receiver cancels their own Requested or Reserved request: food returns to Available, pickup Cancelled, donor notified. */
    public void cancelByReceiver(User receiver, int requestId) {
        Tx.tx(c -> {
            User rec = Guard.user(c, receiver, Constants.ROLE_RECEIVER);
            Request r = requestDAO.findById(c, requestId);
            if (r == null || r.getUserId() != rec.getUserId())
                throw new ServiceException("Request #" + requestId + " was not found among your requests.");
            cancelOpen(c, r, "Request #" + requestId + " cancelled by the receiver");
            logDAO.insert(c, "User", rec.getUserId(), "REQUEST_CANCELLED_BY_RECEIVER", "Request #" + requestId);
            Notifier.send(c, r.getDonorId(), "Request #" + requestId + " cancelled",
                rec.getName() + " cancelled the request for '" + r.getFoodName() + "'. The food is Available again.");
            return null;
        });
    }

    /**
     * Shared by receiver cancel, admin cancel and stale auto-cancel: Requested/Reserved -> Cancelled, food back to
     * Available, pickup Cancelled. Fails when another thread changed the request first.
     */
    static void cancelOpen(Connection c, Request r, String why) throws SQLException {
        FoodDAO foods = new FoodDAO();
        RequestDAO requests = new RequestDAO();
        String from = r.getStatus();
        if (!Constants.REQ_REQUESTED.equals(from) && !Constants.REQ_RESERVED.equals(from))
            throw new ServiceException("Only Requested or Reserved requests can be cancelled (status: " + from + ").");
        if (requests.transition(c, r.getRequestId(), from, Constants.REQ_CANCELLED) == 0)
            throw new ServiceException("Request state changed; please retry (" + why + ").");
        foods.transition(c, r.getFoodId(), from, Constants.FOOD_AVAILABLE);   // food status mirrors request status here
        new PickupDAO().setStatus(c, r.getRequestId(), Constants.PICKUP_CANCELLED);
    }

    /** Receiver enters the code handed over by the donor: Reserved -> Picked Up. */
    public void confirmPickup(User receiver, int requestId, String code) {
        final String entered = code == null ? "" : code.trim();
        boolean ok = Tx.tx(c -> {
            User rec = Guard.user(c, receiver, Constants.ROLE_RECEIVER);
            Request r = requestDAO.findById(c, requestId);
            if (r == null || r.getUserId() != rec.getUserId())
                throw new ServiceException("Request #" + requestId + " was not found among your requests.");
            if (!Constants.REQ_RESERVED.equals(r.getStatus()))
                throw new ServiceException("Request #" + requestId + " is not awaiting pickup (status: " + r.getStatus() + ").");
            Food f = foodDAO.findById(c, r.getFoodId());
            if (!f.getExpiryTime().isAfter(LocalDateTime.now())) throw new ServiceException("This food has expired and can no longer be collected.");
            Pickup p = pickupDAO.findByRequest(c, requestId);
            boolean match = p != null && MessageDigest.isEqual(
                p.getVerificationCode().getBytes(StandardCharsets.UTF_8), entered.getBytes(StandardCharsets.UTF_8));
            if (!match) {
                logDAO.insert(c, "User", rec.getUserId(), "PICKUP_CODE_INVALID", "Wrong code for request #" + requestId);
                return false;
            }
            LocalDateTime now = LocalDateTime.now();
            if (requestDAO.transition(c, requestId, Constants.REQ_RESERVED, Constants.REQ_PICKED_UP) == 0
                || foodDAO.transition(c, f.getFoodId(), Constants.FOOD_RESERVED, Constants.FOOD_PICKED_UP) == 0)
                throw new ServiceException("Pickup could not be confirmed because the state changed. Please try again.");
            pickupDAO.markPickedUp(c, requestId, now);
            logDAO.insert(c, "User", rec.getUserId(), "PICKUP_CONFIRMED", "Request #" + requestId + " picked up");
            Notifier.send(c, r.getDonorId(), "Pickup confirmed for request #" + requestId,
                rec.getName() + " collected '" + r.getFoodName() + "' with a valid verification code. You can now mark the request Completed.");
            return true;
        });
        if (!ok) throw new ServiceException("Invalid verification code. Pickup NOT confirmed.");
    }

    /** Picked Up -> Completed (donor closes the record). */
    public void complete(User donor, int requestId) {
        Tx.tx(c -> {
            User d = Guard.user(c, donor, Constants.ROLE_DONOR);
            Request r = ownedByDonor(c, d, requestId);
            if (requestDAO.transition(c, requestId, Constants.REQ_PICKED_UP, Constants.REQ_COMPLETED) == 0)
                throw new ServiceException("Request #" + requestId + " has not been picked up yet (status: " + r.getStatus() + ").");
            foodDAO.transition(c, r.getFoodId(), Constants.FOOD_PICKED_UP, Constants.FOOD_COMPLETED);
            pickupDAO.setStatus(c, requestId, Constants.PICKUP_COMPLETED);
            logDAO.insert(c, "User", d.getUserId(), "REQUEST_COMPLETED", "Request #" + requestId + " completed");
            Notifier.send(c, r.getUserId(), "Request #" + requestId + " completed",
                "The donor closed request #" + requestId + " ('" + r.getFoodName() + "') as Completed. Thank you!");
            return null;
        });
    }

    /** The donor may look up the code of one of their own reserved requests to hand it over. */
    public Pickup pickupFor(User donor, int requestId) {
        return Tx.read(c -> {
            User d = Guard.user(c, donor, Constants.ROLE_DONOR);
            ownedByDonor(c, d, requestId);
            Pickup p = pickupDAO.findByRequest(c, requestId);
            if (p == null) throw new ServiceException("No pickup code exists yet; approve the request first.");
            return p;
        });
    }

    public List<Request> forReceiver(User receiver) {
        return Tx.read(c -> requestDAO.findByReceiver(c, Guard.user(c, receiver, Constants.ROLE_RECEIVER).getUserId()));
    }

    public List<Request> forDonor(User donor) {
        return Tx.read(c -> requestDAO.findByDonor(c, Guard.user(c, donor, Constants.ROLE_DONOR).getUserId()));
    }

    private Request ownedByDonor(Connection c, User donor, int requestId) throws SQLException {
        Request r = requestDAO.findById(c, requestId);
        if (r == null || r.getDonorId() != donor.getUserId())
            throw new ServiceException("Request #" + requestId + " was not found among your food posts.");
        return r;
    }
}
