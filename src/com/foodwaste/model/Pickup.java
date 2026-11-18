package com.foodwaste.model;

import java.time.LocalDateTime;

/** ER entity PICKUP. */
public class Pickup {
    private int pickupId;
    private int requestId;
    private LocalDateTime pickupTime;
    private String verificationCode;
    private String status;

    public int getPickupId() { return pickupId; }
    public void setPickupId(int pickupId) { this.pickupId = pickupId; }
    public int getRequestId() { return requestId; }
    public void setRequestId(int requestId) { this.requestId = requestId; }
    public LocalDateTime getPickupTime() { return pickupTime; }
    public void setPickupTime(LocalDateTime pickupTime) { this.pickupTime = pickupTime; }
    public String getVerificationCode() { return verificationCode; }
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
