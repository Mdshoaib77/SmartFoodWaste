package com.foodwaste.service;

import com.foodwaste.dao.ActivityLogDAO;
import com.foodwaste.dao.AreaDAO;
import com.foodwaste.dao.FoodDAO;
import com.foodwaste.dao.UserDAO;
import com.foodwaste.model.Food;
import com.foodwaste.model.User;
import com.foodwaste.util.Constants;
import java.time.LocalDateTime;
import java.util.List;

/** FR-03 / FR-04 / FR-05: food posting, editing, priority assignment and area + capacity matching. */
public class FoodService {
    private final FoodDAO foodDAO = new FoodDAO();
    private final UserDAO userDAO = new UserDAO();
    private final AreaDAO areaDAO = new AreaDAO();
    private final ActivityLogDAO logDAO = new ActivityLogDAO();

    /** The standard area list (all 64 districts) for the pickup-area drop-down. */
    public List<String> areas() {
        return Tx.read(areaDAO::findAll);
    }

    /**
     * FR-03: posts a food record. pickupArea = the district where the food is collected (drop-down value, matched
     * trimmed and case-insensitively); blank means the donor's own registered area. location = address detail.
     * FR-05 matching uses the pickup area, so a donor can offer food in a district other than the one registered in.
     */
    public Food postFood(User donor, String name, String category, int quantity, LocalDateTime prep,
                         LocalDateTime expiry, String pickupArea, String location, String safetyInfo) {
        final Food f = build(name, category, quantity, prep, expiry, location, safetyInfo);
        return Tx.tx(c -> {
            User d = Guard.user(c, donor, Constants.ROLE_DONOR);
            f.setAreaId(resolveArea(c, d, pickupArea));
            f.setUserId(d.getUserId());
            f.setStatus(Constants.FOOD_AVAILABLE);
            f.setFoodId(foodDAO.insert(c, f));
            logDAO.insert(c, "User", d.getUserId(), "FOOD_POSTED",
                "Food #" + f.getFoodId() + " '" + f.getFoodName() + "' x" + quantity + ", priority " + f.getPriorityLevel()
                + ", pickup area " + f.getAreaId());
            return f;
        });
    }

    /** Donor edit: allowed only while the food is still Available (not requested / reserved / finished). */
    public Food updateFood(User donor, int foodId, String name, String category, int quantity, LocalDateTime prep,
                           LocalDateTime expiry, String pickupArea, String location, String safetyInfo) {
        final Food f = build(name, category, quantity, prep, expiry, location, safetyInfo);
        return Tx.tx(c -> {
            User d = Guard.user(c, donor, Constants.ROLE_DONOR);
            Food old = ownFood(c, d, foodId);
            f.setAreaId(resolveArea(c, d, pickupArea));
            f.setFoodId(foodId);
            f.setUserId(d.getUserId());
            if (foodDAO.updateAvailable(c, f) == 0)
                throw new ServiceException("Food #" + foodId + " can only be edited while it is Available (status: " + old.getStatus() + ").");
            logDAO.insert(c, "User", d.getUserId(), "FOOD_EDITED", "Food #" + foodId + " '" + f.getFoodName() + "' x" + quantity
                + ", pickup area " + f.getAreaId());
            return foodDAO.findById(c, foodId);
        });
    }

    /** Donor delete: allowed only while the food is still Available. */
    public void deleteFood(User donor, int foodId) {
        Tx.tx(c -> {
            User d = Guard.user(c, donor, Constants.ROLE_DONOR);
            Food old = ownFood(c, d, foodId);
            if (foodDAO.deleteAvailable(c, foodId, d.getUserId()) == 0)
                throw new ServiceException("Food #" + foodId + " can only be deleted while it is Available (status: " + old.getStatus() + ").");
            logDAO.insert(c, "User", d.getUserId(), "FOOD_DELETED", "Food #" + foodId + " '" + old.getFoodName() + "' deleted by donor");
            return null;
        });
    }

    public List<Food> myPosts(User donor) {
        return Tx.read(c -> foodDAO.findByDonor(c, Guard.user(c, donor, Constants.ROLE_DONOR).getUserId()));
    }

    /**
     * FR-05: available food for a receiver. Urgent (High) food comes first, and inside every priority food from the
     * food whose PICKUP AREA is the receiver's own area comes first. nearbyOnly restricts the list to the own area; fitsOnly hides food whose quantity
     * is larger than the receiver's capacity (without it such food is listed but {@link #exceedsCapacity} marks it).
     */
    public List<Food> availableFor(User receiver, boolean nearbyOnly, boolean fitsOnly) {
        return Tx.read(c -> {
            User r = Guard.user(c, receiver, Constants.ROLE_RECEIVER);
            return foodDAO.findAvailable(c, r.getAreaId(), nearbyOnly, LocalDateTime.now(), fitsOnly ? r.getCapacity() : null);
        });
    }

    /** True when the food is larger than what this receiver/NGO can take. */
    public static boolean exceedsCapacity(Food f, User receiver) {
        return receiver.getCapacity() != null && f.getQuantity() > receiver.getCapacity();
    }

    /** FR-05: approved Receiver/NGO accounts in the donor's registered area (largest capacity first). */
    public List<User> nearbyReceivers(User donor) {
        return nearbyReceivers(donor, null);
    }

    /**
     * FR-05: when foodId is given, the receivers are those in that FOOD'S PICKUP AREA whose capacity can handle its
     * quantity (the food must belong to the calling donor); without foodId: receivers in the donor's registered area.
     */
    public List<User> nearbyReceivers(User donor, Integer foodId) {
        return Tx.read(c -> {
            User d = Guard.user(c, donor, Constants.ROLE_DONOR);
            Integer min = null;
            String area = d.getAreaId();
            if (foodId != null) {
                Food f = ownFood(c, d, foodId);
                min = f.getQuantity();
                area = f.getAreaId();
            }
            return userDAO.findApprovedReceiversByArea(c, area, min);
        });
    }

    /** Blank = the donor's registered area; otherwise the official spelling from the areas table, or an error. */
    private String resolveArea(java.sql.Connection c, User donor, String pickupArea) throws java.sql.SQLException {
        String raw = pickupArea == null ? "" : pickupArea.trim();
        if (raw.isEmpty()) return donor.getAreaId();
        String official = areaDAO.findOfficial(c, raw);
        if (official == null)
            throw new ServiceException("Please choose the pickup area from the list ('" + raw + "' is not a known area).");
        return official;
    }

    private Food ownFood(java.sql.Connection c, User donor, int foodId) throws java.sql.SQLException {
        Food f = foodDAO.findById(c, foodId);
        if (f == null || f.getUserId() != donor.getUserId()) throw new ServiceException("Food #" + foodId + " was not found among your posts.");
        return f;
    }

    private static Food build(String name, String category, int quantity, LocalDateTime prep, LocalDateTime expiry,
                              String location, String safetyInfo) {
        final String n = Check.text(name, "Food name", 100);
        final String cat = Check.text(category, "Category", 50);
        final String loc = Check.text(location, "Location", 255);
        final String safety = Check.optional(safetyInfo, "Safety information", 500);
        if (quantity <= 0) throw new ServiceException("Quantity must be greater than zero.");
        if (quantity > 1_000_000) throw new ServiceException("Quantity must be at most 1000000.");
        if (prep == null || expiry == null) throw new ServiceException("Preparation time and expiry time are required.");
        LocalDateTime now = LocalDateTime.now();
        if (prep.isAfter(now.plusMinutes(1))) throw new ServiceException("Preparation time cannot be in the future.");
        if (!expiry.isAfter(prep)) throw new ServiceException("Expiry time must be after the preparation time.");
        if (!expiry.isAfter(now)) throw new ServiceException("Expiry time must be in the future.");
        Food f = new Food();
        f.setFoodName(n); f.setCategory(cat); f.setQuantity(quantity);
        f.setPreparationTime(prep); f.setExpiryTime(expiry); f.setLocation(loc);
        f.setSafetyInfo(safety.isEmpty() ? null : safety);
        f.setPriorityLevel(PriorityCalculator.calculate(expiry, now));   // FR-04
        return f;
    }
}
