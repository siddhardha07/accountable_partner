package com.example.accountable;

public class AccessRequest {
    private String requestId;
    private String userId;
    private String userName;
    private String appPackage;
    private String appName;
    private long requestTime;
    private long grantedDuration; // in minutes
    private String status; // "pending", "granted", "denied"

    public AccessRequest() {
        // Required for Firebase
    }

    public AccessRequest(String userId, String userName, String appPackage, String appName) {
        this.requestId = userId + "_" + System.currentTimeMillis();
        this.userId = userId;
        this.userName = userName;
        this.appPackage = appPackage;
        this.appName = appName;
        this.requestTime = System.currentTimeMillis();
        this.status = "pending";
    }

    // Getters and Setters
    public String getRequestId() { return requestId; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getAppPackage() { return appPackage; }
    public String getAppName() { return appName; }
    public long getRequestTime() { return requestTime; }
    public long getGrantedDuration() { return grantedDuration; }
    public String getStatus() { return status; }

    public void setGrantedDuration(long minutes) { this.grantedDuration = minutes; }
    public void setStatus(String status) { this.status = status; }
}
