package com.foodwaste.model;

/** ER entity ADMIN. */
public class Admin {
    private int adminId;
    private String username;
    private String passwordHash;
    private String role;
    private boolean mustChangePassword;
    private Integer createdBy;
    private java.time.LocalDateTime createdAt;

    public int getAdminId() { return adminId; }
    public void setAdminId(int adminId) { this.adminId = adminId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
