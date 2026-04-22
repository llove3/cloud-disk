package com.example.clouddisk.service;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.Share;
import com.example.clouddisk.entity.ShareAccessLog;
import com.example.clouddisk.mapper.FileMapper;
import com.example.clouddisk.mapper.ShareAccessLogMapper;
import com.example.clouddisk.mapper.ShareMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ShareService {

    @Autowired
    private ShareMapper shareMapper;

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private ShareAccessLogMapper shareAccessLogMapper;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateShareCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    public Share createShare(Long userId, Long fileId, String password, Integer expireDays, Integer maxVisits) {
        FileInfo file = fileMapper.findByIdAndUserId(fileId, userId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权分享");
        }
        String code = generateShareCode();
        Date expireTime = null;
        if (expireDays != null && expireDays > 0) {
            expireTime = new Date(System.currentTimeMillis() + expireDays * 24L * 60 * 60 * 1000);
        }
        Share share = new Share();
        share.setFileId(fileId);
        share.setUserId(userId);
        share.setShareCode(code);
        share.setPassword(password != null && !password.isEmpty() ? password : null);
        share.setExpireTime(expireTime);
        share.setIsPackage(false);
        share.setMaxVisits(maxVisits);
        shareMapper.insert(share);
        return share;
    }

    @Transactional
    public Share createPackageShare(Long userId, List<Long> fileIds, String zipName, String password, Integer expireDays, Integer maxVisits) throws IOException {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new RuntimeException("请至少选择一个文件");
        }
        String safeZipName = (zipName != null && !zipName.trim().isEmpty()) ? zipName.trim() : "打包文件";
        if (!safeZipName.toLowerCase().endsWith(".zip")) {
            safeZipName += ".zip";
        }
        String userDir = uploadDir + userId + "/packages/";
        Path userPath = Paths.get(userDir);
        if (!Files.exists(userPath)) {
            Files.createDirectories(userPath);
        }
        String zipFileName = System.currentTimeMillis() + "_" + safeZipName;
        String zipFilePath = userDir + zipFileName;

        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (Long fileId : fileIds) {
                FileInfo file = fileMapper.findByIdAndUserId(fileId, userId);
                if (file == null || file.getFileSize() == 0) continue;
                Path sourcePath = Paths.get(file.getFilePath());
                if (!Files.exists(sourcePath)) continue;
                ZipEntry entry = new ZipEntry(file.getFileName());
                zos.putNextEntry(entry);
                Files.copy(sourcePath, zos);
                zos.closeEntry();
            }
        }

        FileInfo packageFile = new FileInfo();
        packageFile.setUserId(userId);
        packageFile.setFileName(safeZipName);
        packageFile.setFileSize(new File(zipFilePath).length());
        packageFile.setFilePath(zipFilePath);
        packageFile.setFileMd5("");
        packageFile.setParentId(0L);
        packageFile.setVersion(1);
        packageFile.setDeleted(false);
        packageFile.setStarred(false);
        fileMapper.insert(packageFile);

        String code = generateShareCode();
        Date expireTime = null;
        if (expireDays != null && expireDays > 0) {
            expireTime = new Date(System.currentTimeMillis() + expireDays * 24L * 60 * 60 * 1000);
        }
        Share share = new Share();
        share.setFileId(packageFile.getId());
        share.setUserId(userId);
        share.setShareCode(code);
        share.setPassword(password != null && !password.isEmpty() ? password : null);
        share.setExpireTime(expireTime);
        share.setIsPackage(true);
        share.setMaxVisits(maxVisits);
        shareMapper.insert(share);
        return share;
    }

    @Transactional
    public FileInfo getFileByShareCode(String code, String inputPassword, String ipAddress, String userAgent) {
        Share share = shareMapper.findByCode(code);
        ShareAccessLog log = new ShareAccessLog();
        log.setShareId(share != null ? share.getId() : null);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);

        if (share == null) {
            log.setSuccess(false);
            log.setErrorReason("分享链接不存在");
            shareAccessLogMapper.insert(log);
            throw new RuntimeException("分享链接不存在");
        }
        log.setShareId(share.getId());

        if (share.getExpireTime() != null && share.getExpireTime().before(new Date())) {
            log.setSuccess(false);
            log.setErrorReason("分享链接已过期");
            shareAccessLogMapper.insert(log);
            throw new RuntimeException("分享链接已过期");
        }
        if (share.getMaxVisits() != null && share.getVisitCount() >= share.getMaxVisits()) {
            log.setSuccess(false);
            log.setErrorReason("分享链接已达到最大访问次数");
            shareAccessLogMapper.insert(log);
            throw new RuntimeException("分享链接已达到最大访问次数");
        }
        if (share.getPassword() != null && !share.getPassword().equals(inputPassword)) {
            log.setSuccess(false);
            log.setErrorReason("提取码错误");
            shareAccessLogMapper.insert(log);
            throw new RuntimeException("提取码错误");
        }
        FileInfo file = fileMapper.findById(share.getFileId());
        if (file == null || file.getDeleted()) {
            log.setSuccess(false);
            log.setErrorReason("原文件已被删除");
            shareAccessLogMapper.insert(log);
            throw new RuntimeException("原文件已被删除");
        }
        shareMapper.incrementVisitCount(code);
        log.setSuccess(true);
        shareAccessLogMapper.insert(log);
        return file;
    }

    public List<Share> listShares(Long userId) {
        return shareMapper.findByUserId(userId);
    }

    public void deleteShare(Long shareId, Long userId) {
        Share share = shareMapper.findById(shareId);
        if (share != null && share.getUserId().equals(userId)) {
            shareMapper.deleteById(shareId);
        } else {
            throw new RuntimeException("无权删除此分享");
        }
    }

    @Transactional
    public void updateShare(Long shareId, Long userId, String password, Integer expireDays, Integer maxVisits) {
        Share share = shareMapper.findById(shareId);
        if (share == null || !share.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改此分享");
        }
        if (password != null) {
            share.setPassword(password.isEmpty() ? null : password);
        }
        if (expireDays != null && expireDays > 0) {
            share.setExpireTime(new Date(System.currentTimeMillis() + expireDays * 24L * 60 * 60 * 1000));
        } else if (expireDays != null && expireDays == 0) {
            share.setExpireTime(null);
        }
        if (maxVisits != null) {
            share.setMaxVisits(maxVisits);
        }
        shareMapper.update(share);
    }

    public List<ShareAccessLog> getAccessLogs(Long shareId, Long userId) {
        Share share = shareMapper.findById(shareId);
        if (share == null || !share.getUserId().equals(userId)) {
            throw new RuntimeException("无权查看此分享日志");
        }
        return shareAccessLogMapper.findByShareId(shareId);
    }
}