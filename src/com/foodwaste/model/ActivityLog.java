package com.foodwaste.model;

import java.time.LocalDateTime;

/** Timestamped audit record. */
public class ActivityLog {
    private long logId;
    private String actorType;
    private Integer actorId;
    private String action;
    private String details;
    private LocalDateTime loggedAt;

    public long getLogId() { return logId; }
    public void setLogId(long logId) { this.logId = logId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public Integer getActorId() { return actorId; }
    public void setActorId(Integer actorId) { this.actorId = actorId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getLoggedAt() { return loggedAt; }
    public void setLoggedAt(LocalDateTime loggedAt) { this.loggedAt = loggedAt; }
}
