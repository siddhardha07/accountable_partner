package com.example.accountable;

public class AccessRequest {
    private String requestId;
    private String userId;
    private String userName;
    private String appPackage;
    private String appName;
    private long requestTime;
    private long requestedDuration; // in seconds - user selected time
    private long grantedDuration; // in seconds - final approved time
    private String requestType; // "APP_ACCESS" or "UNRESTRICT_APP"
    private String reason; // user provided reason
    private String status; // "pending", "granted", "denied"

    public AccessRequest() {
        // Required for Firebase
    }

    public AccessRequest(String userId, String userName, String appPackage, String appName,
                         String requestType, long requestedDurationSeconds, String reason) {
        this.requestId = userId + "_" + System.currentTimeMillis();
        this.userId = userId;
        this.userName = userName;
        this.appPackage = appPackage;
        this.appName = appName;
        this.requestType = requestType;
        this.requestedDuration = requestedDurationSeconds;
        this.reason = reason;
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
    public long getRequestedDuration() { return requestedDuration; }
    public long getGrantedDuration() { return grantedDuration; }
    public String getRequestType() { return requestType; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }

    public void setRequestedDuration(long requestedDuration) { this.requestedDuration = requestedDuration; }
    public void setGrantedDuration(long grantedDuration) { this.grantedDuration = grantedDuration; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public void setReason(String reason) { this.reason = reason; }
    public void setStatus(String status) { this.status = status; }
}
