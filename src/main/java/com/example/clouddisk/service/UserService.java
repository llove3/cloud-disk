package com.example.clouddisk.service;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.FileVersion;
import com.example.clouddisk.entity.User;
import com.example.clouddisk.mapper.FileMapper;
import com.example.clouddisk.mapper.FileVersionMapper;
import com.example.clouddisk.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private FileVersionMapper fileVersionMapper;

    @Autowired
    private MailService mailService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final Map<String, CodeInfo> verificationCodes = new ConcurrentHashMap<>();

    private static class CodeInfo {
        String code;
        long expireTime;
        CodeInfo(String code, long expireTime) { this.code = code; this.expireTime = expireTime; }
    }

    public boolean register(String email, String password, String code) {
        CodeInfo info = verificationCodes.get(email);
        if (info == null || System.currentTimeMillis() > info.expireTime || !info.code.equals(code)) {
            return false;
        }
        if (userMapper.findByEmail(email) != null) {
            return false;
        }
        String salt = generateSalt();
        String encryptedPwd = passwordEncoder.encode(password);
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setPassword(encryptedPwd);
        user.setSalt(salt);
        userMapper.insert(user);
        verificationCodes.remove(email);
        return true;
    }

    public User loginByEmail(String email, String password) {
        User user = userMapper.findByEmail(email);
        if (user == null) return null;
        if (passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    public boolean sendVerificationCode(String email) {
        String code = String.format("%06d", new SecureRandom().nextInt(1000000));
        verificationCodes.put(email, new CodeInfo(code, System.currentTimeMillis() + 5 * 60 * 1000));
        return mailService.sendSimpleMail(email, "云盘验证码", "您的验证码是：" + code + "，5分钟内有效。");
    }

    public boolean verifyCode(String email, String code) {
        CodeInfo info = verificationCodes.get(email);
        return info != null && System.currentTimeMillis() <= info.expireTime && info.code.equals(code);
    }

    public boolean resetPasswordByEmail(String email, String newPassword, String code) {
        if (!verifyCode(email, code)) return false;
        User user = userMapper.findByEmail(email);
        if (user == null) return false;
        String salt = generateSalt();
        String encryptedPwd = passwordEncoder.encode(newPassword);
        userMapper.updatePassword(user.getId(), encryptedPwd, salt);
        verificationCodes.remove(email);
        return true;
    }

    public User findById(Long id) {
        return userMapper.findById(id);
    }

    @Transactional
    public void recalculateUsedSpace(Long userId) {
        List<FileInfo> activeFiles = fileMapper.findByUserIdAndDeletedFalse(userId);
        long totalUsed = 0L;
        for (FileInfo file : activeFiles) {
            totalUsed += file.getFileSize();
        }
        List<FileVersion> allVersions = fileVersionMapper.findVersionsByUserId(userId);
        for (FileVersion version : allVersions) {
            totalUsed += version.getFileSize();
        }
        userMapper.updateUsedSpace(userId, totalUsed);
    }

    @Transactional
    public User getUpdatedUser(Long userId) {
        return userMapper.findById(userId);
    }

    public boolean updateUsername(Long userId, String newUsername) {
        if (userMapper.findByUsername(newUsername) != null) return false;
        return userMapper.updateUsername(userId, newUsername) > 0;
    }

    public boolean updateEmail(Long userId, String newEmail, String code) {
        if (!verifyCode(newEmail, code)) return false;
        if (userMapper.findByEmail(newEmail) != null) return false;
        userMapper.updateEmail(userId, newEmail);
        verificationCodes.remove(newEmail);
        return true;
    }

    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.findById(userId);
        if (user == null) return false;
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) return false;
        String newEncryptedPwd = passwordEncoder.encode(newPassword);
        String newSalt = generateSalt();
        return userMapper.updatePassword(userId, newEncryptedPwd, newSalt) > 0;
    }

    public void updateAvatar(Long userId, String avatarUrl) {
        userMapper.updateAvatar(userId, avatarUrl);
    }

    private String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return java.util.Base64.getEncoder().encodeToString(salt);
    }
}