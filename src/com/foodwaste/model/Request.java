package com.foodwaste.model;

import java.time.LocalDateTime;

/** ER entity REQUEST (userId = receiver/NGO). Fields from foodName onward are joined display fields. */
public class Request {
    private int requestId;
    private int foodId;
    private int userId;
    private String timeSlot;
    private String notes;
    private String status;
    private boolean suspicious;
    private LocalDateTime requestedAt;
    private LocalDateTime updatedAt;
    private String foodName;
    private int donorId;
    private String donorName;
    private String receiverName;
    private String approvedSlot;
    private String donorArea;
    private String pickupArea;
    private String receiverArea;
    private LocalDateTime pickupTime;
    private String pickupStatus;

    public int getRequestId() { return requestId; }
    public void setRequestId(int requestId) { this.requestId = requestId; }
    public int getFoodId() { return foodId; }
    public void setFoodId(int foodId) { this.foodId = foodId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isSuspicious() { return suspicious; }
    public void setSuspicious(boolean suspicious) { this.suspicious = suspicious; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public int getDonorId() { return donorId; }
    public void setDonorId(int donorId) { this.donorId = donorId; }
    public String getDonorName() { return donorName; }
    public void setDonorName(String donorName) { this.donorName = donorName; }
    public String getApprovedSlot() { return approvedSlot; }
    public void setApprovedSlot(String approvedSlot) { this.approvedSlot = approvedSlot; }
    public String getPickupArea() { return pickupArea; }
    public void setPickupArea(String pickupArea) { this.pickupArea = pickupArea; }
    public String getDonorArea() { return donorArea; }
    public void setDonorArea(String donorArea) { this.donorArea = donorArea; }
    public String getReceiverArea() { return receiverArea; }
    public void setReceiverArea(String receiverArea) { this.receiverArea = receiverArea; }
    public LocalDateTime getPickupTime() { return pickupTime; }
    public void setPickupTime(LocalDateTime pickupTime) { this.pickupTime = pickupTime; }
    public String getPickupStatus() { return pickupStatus; }
    public void setPickupStatus(String pickupStatus) { this.pickupStatus = pickupStatus; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
}
