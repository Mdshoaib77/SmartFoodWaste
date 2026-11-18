package com.foodwaste.model;

import java.time.LocalDateTime;

/** ER entity USER (Donor or Receiver/NGO). */
public class User {
    private int userId;
    private String name;
    private String contact;
    private String passwordHash;
    private String role;
    private String areaId;
    private Integer capacity;
    private String status;
    private Integer verifiedBy;
    private LocalDateTime createdAt;

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(Integer verifiedBy) { this.verifiedBy = verifiedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
