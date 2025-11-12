package com.example.accountable;

public class FriendControl {
    private String friendName;
    private String friendEmail;
    private String accessCode;
    private boolean hasAccess;

    public FriendControl(String friendName, String friendEmail, String accessCode) {
        this.friendName = friendName;
        this.friendEmail = friendEmail;
        this.accessCode = accessCode;
        this.hasAccess = false;
    }

    // Getters and Setters
    public String getFriendName() { return friendName; }
    public String getFriendEmail() { return friendEmail; }
    public String getAccessCode() { return accessCode; }
    public boolean hasAccess() { return hasAccess; }
    public void setHasAccess(boolean hasAccess) { this.hasAccess = hasAccess; }
}
