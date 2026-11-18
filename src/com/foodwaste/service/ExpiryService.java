package com.foodwaste.service;

import com.foodwaste.dao.ActivityLogDAO;
import com.foodwaste.dao.FoodDAO;
import com.foodwaste.dao.PickupDAO;
import com.foodwaste.dao.RequestDAO;
import com.foodwaste.model.Food;
import com.foodwaste.model.Request;
import com.foodwaste.util.Constants;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * FR-04 / FR-09 housekeeping: refreshes priorities, expires overdue food / requests / pickups (with notifications) and
 * auto-cancels stale requests that the donor never answered (Constants.STALE_REQUEST_HOURS).
 */
public final class ExpiryService {
    private static final FoodDAO FOOD = new FoodDAO();
    private static final RequestDAO REQUESTS = new RequestDAO();
    private static final PickupDAO PICKUPS = new PickupDAO();
    private static final ActivityLogDAO LOG = new ActivityLogDAO();
    private static ScheduledExecutorService scheduler;

    private ExpiryService() { }

    /** Starts a background check every minute (daemon thread, never blocks JVM exit). */
    public static synchronized void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "expiry-checker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(ExpiryService::sync, 1, 1, TimeUnit.MINUTES);
    }

    public static synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /** Runs one pass; returns the number of food items newly marked Expired. Never throws. */
    public static int sync() {
        try {
            return syncAt(LocalDateTime.now());
        } catch (RuntimeException e) {
            System.err.println("[expiry-check] " + e.getMessage());
            return 0;
        }
    }

    /** One pass at the given clock time (exposed so tests can pin the time). */
    public static int syncAt(LocalDateTime now) {
        return Tx.tx(c -> {
            FOOD.refreshPriorities(c, now);
            // 1. food that ran out of time: notify first (needs the open requests), then flip statuses.
            List<Food> overdue = FOOD.findOverdue(c, now);
            for (Food f : overdue) {
                Notifier.send(c, f.getUserId(), "Food #" + f.getFoodId() + " expired",
                    "'" + f.getFoodName() + "' (qty " + f.getQuantity() + ") expired before pickup and is recorded as wasted in the reports.");
                for (Request r : REQUESTS.findOpenByFood(c, f.getFoodId()))
                    Notifier.send(c, r.getUserId(), "Request #" + r.getRequestId() + " expired",
                        "'" + f.getFoodName() + "' expired before it could be collected, so your request was closed.");
            }
            PICKUPS.expireOverdue(c, now);   // order matters: these two read the food status
            REQUESTS.expireOverdue(c, now);
            int expired = FOOD.expireOverdue(c, now);
            if (expired > 0) LOG.insert(c, "System", null, "EXPIRY_CHECK", expired + " food item(s) marked Expired");

            // 2. stale requests: donor silent for STALE_REQUEST_HOURS -> cancel, food Available again
            int stale = 0;
            for (Request r : REQUESTS.findStale(c, now.minusHours(Constants.STALE_REQUEST_HOURS))) {
                try {
                    RequestService.cancelOpen(c, r, "stale");
                } catch (ServiceException alreadyHandled) {
                    continue;   // someone answered / cancelled it a moment ago
                }
                stale++;
                LOG.insert(c, "System", null, "REQUEST_AUTO_CANCELLED", "Request #" + r.getRequestId()
                    + " not answered by the donor within " + Constants.STALE_REQUEST_HOURS + "h");
                Notifier.send(c, r.getUserId(), "Request #" + r.getRequestId() + " cancelled automatically",
                    "The donor did not answer your request for '" + r.getFoodName() + "' within " + Constants.STALE_REQUEST_HOURS
                    + " hours, so it was cancelled and the food is Available again. You may request it again or choose other food.");
                Notifier.send(c, r.getDonorId(), "Request #" + r.getRequestId() + " cancelled automatically",
                    "You did not answer the request for '" + r.getFoodName() + "' within " + Constants.STALE_REQUEST_HOURS
                    + " hours. It was cancelled and the food is Available again.");
            }
            return expired;
        });
    }
}
