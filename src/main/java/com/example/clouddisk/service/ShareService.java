package com.example.clouddisk.service;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.Share;
import com.example.clouddisk.mapper.FileMapper;
import com.example.clouddisk.mapper.ShareMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Date;

@Service
public class ShareService {

    @Autowired
    private ShareMapper shareMapper;

    @Autowired
    private FileMapper fileMapper;

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

    public Share createShare(Long userId, Long fileId, String password, Integer expireDays) {
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
        shareMapper.insert(share);
        return share;
    }

    public FileInfo getFileByShareCode(String code, String inputPassword) {
        Share share = shareMapper.findByCode(code);
        if (share == null) {
            throw new RuntimeException("分享链接不存在");
        }
        if (share.getExpireTime() != null && share.getExpireTime().before(new Date())) {
            throw new RuntimeException("分享链接已过期");
        }
        if (share.getPassword() != null && !share.getPassword().equals(inputPassword)) {
            throw new RuntimeException("提取码错误");
        }
        FileInfo file = fileMapper.findById(share.getFileId());
        if (file == null || file.getDeleted()) {
            throw new RuntimeException("原文件已被删除");
        }
        return file;
    }
}