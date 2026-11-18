package com.foodwaste.model;

import java.time.LocalDateTime;

/** Administrator message or automatic system notification shown in a user's inbox. */
public class Message {
    private long messageId;
    private Integer senderAdminId;     // null = automatic system notification
    private String senderName;         // joined display field: admin username or "System"
    private int recipientUserId;
    private String recipientName;      // joined display field
    private String subject;
    private String body;
    private LocalDateTime createdAt;
    private boolean read;

    public long getMessageId() { return messageId; }
    public void setMessageId(long messageId) { this.messageId = messageId; }
    public Integer getSenderAdminId() { return senderAdminId; }
    public void setSenderAdminId(Integer senderAdminId) { this.senderAdminId = senderAdminId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public int getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(int recipientUserId) { this.recipientUserId = recipientUserId; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
