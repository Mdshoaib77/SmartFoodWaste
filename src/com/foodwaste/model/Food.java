package com.foodwaste.model;

import java.time.LocalDateTime;

/** ER entity FOOD. donorName is a joined display field (userId = donor). areaId = PickupArea: the district where the food is collected. */
public class Food {
    private int foodId;
    private String foodName;
    private String category;
    private int quantity;
    private LocalDateTime preparationTime;
    private LocalDateTime expiryTime;
    private String location;
    private String safetyInfo;
    private String priorityLevel;
    private String status;
    private int userId;
    private LocalDateTime createdAt;
    private String donorName;
    private String areaId;

    public int getFoodId() { return foodId; }
    public void setFoodId(int foodId) { this.foodId = foodId; }
    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public LocalDateTime getPreparationTime() { return preparationTime; }
    public void setPreparationTime(LocalDateTime preparationTime) { this.preparationTime = preparationTime; }
    public LocalDateTime getExpiryTime() { return expiryTime; }
    public void setExpiryTime(LocalDateTime expiryTime) { this.expiryTime = expiryTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getSafetyInfo() { return safetyInfo; }
    public void setSafetyInfo(String safetyInfo) { this.safetyInfo = safetyInfo; }
    public String getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(String priorityLevel) { this.priorityLevel = priorityLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getDonorName() { return donorName; }
    public void setDonorName(String donorName) { this.donorName = donorName; }
    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }
}
