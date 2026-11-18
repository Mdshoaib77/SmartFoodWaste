package com.foodwaste.util;

/** Status / role literals shared by every layer (must match the ENUMs in database.sql). */
public final class Constants {
    private Constants() { }

    public static final String ROLE_DONOR = "Donor";
    public static final String ROLE_RECEIVER = "Receiver/NGO";

    public static final String USER_PENDING = "Pending";
    public static final String USER_APPROVED = "Approved";
    public static final String USER_REJECTED = "Rejected";

    public static final String FOOD_AVAILABLE = "Available";
    public static final String FOOD_REQUESTED = "Requested";
    public static final String FOOD_RESERVED = "Reserved";
    public static final String FOOD_PICKED_UP = "Picked Up";
    public static final String FOOD_COMPLETED = "Completed";
    public static final String FOOD_EXPIRED = "Expired";

    public static final String REQ_REQUESTED = "Requested";
    public static final String REQ_RESERVED = "Reserved";
    public static final String REQ_REJECTED = "Rejected";
    public static final String REQ_PICKED_UP = "Picked Up";
    public static final String REQ_COMPLETED = "Completed";
    public static final String REQ_EXPIRED = "Expired";
    public static final String REQ_CANCELLED = "Cancelled";

    public static final String PICKUP_PENDING = "Pending";
    public static final String PICKUP_PICKED_UP = "Picked Up";
    public static final String PICKUP_COMPLETED = "Completed";
    public static final String PICKUP_EXPIRED = "Expired";
    public static final String PICKUP_CANCELLED = "Cancelled";

    public static final String PRIORITY_HIGH = "High";
    public static final String PRIORITY_MEDIUM = "Medium";
    public static final String PRIORITY_LOW = "Low";

    /** FR-04 thresholds. */
    public static final int HIGH_PRIORITY_HOURS = 4;
    public static final int MEDIUM_PRIORITY_HOURS = 12;

    /** A Requested request the donor has not answered for this many hours is auto-cancelled (food becomes Available). */
    public static final int STALE_REQUEST_HOURS = 2;

    /** Login throttling: this many wrong passwords in a row lock the account for LOCK_MINUTES. */
    public static final int MAX_FAILED_LOGINS = 5;
    public static final int LOCK_MINUTES = 5;

    /** Minimum length of every password. */
    public static final int MIN_PASSWORD_LENGTH = 6;

    /** A receiver holding this many open requests gets the next one auto-flagged for admin review. */
    public static final int OPEN_REQUEST_FLAG_THRESHOLD = 3;

    /** Message audiences used by administrator broadcasts. */
    public static final String AUDIENCE_DONORS = "All Donors";
    public static final String AUDIENCE_RECEIVERS = "All Receivers/NGOs";
    public static final String AUDIENCE_EVERYONE = "All approved users";

    /** activity_log action names that tests and screens rely on. */
    public static final String ACT_LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACT_LOGIN_LOCKED = "LOGIN_LOCKED";
}
