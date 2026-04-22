package com.example.clouddisk.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

public class ShareAccessLog {
    private Long id;
    private Long shareId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date accessTime;
    private String ipAddress;
    private String userAgent;
    private Boolean success;
    private String errorReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getShareId() { return shareId; }
    public void setShareId(Long shareId) { this.shareId = shareId; }
    public Date getAccessTime() { return accessTime; }
    public void setAccessTime(Date accessTime) { this.accessTime = accessTime; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }
}