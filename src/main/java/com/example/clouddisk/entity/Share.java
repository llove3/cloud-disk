package com.example.clouddisk.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

public class Share {
    private Long id;
    private Long fileId;
    private Long userId;
    private String shareCode;
    private String password;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date expireTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    private String fileName;
    private Integer visitCount;
    private Boolean isPackage;
    private Integer maxVisits;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getShareCode() { return shareCode; }
    public void setShareCode(String shareCode) { this.shareCode = shareCode; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getVisitCount() { return visitCount; }
    public void setVisitCount(Integer visitCount) { this.visitCount = visitCount; }
    public Boolean getIsPackage() { return isPackage; }
    public void setIsPackage(Boolean isPackage) { this.isPackage = isPackage; }
    public Integer getMaxVisits() { return maxVisits; }
    public void setMaxVisits(Integer maxVisits) { this.maxVisits = maxVisits; }
}