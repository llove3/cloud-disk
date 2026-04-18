package com.example.clouddisk.service;

import com.example.clouddisk.entity.User;
import com.example.clouddisk.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String md5(String password, String salt) {
        String input = password + salt;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] result = md.digest(input.getBytes());
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean register(String username, String password) {
        if (userMapper.findByUsername(username) != null) {
            return false;
        }
        String salt = generateSalt();
        String encryptedPwd = md5(password, salt);
        User user = new User();
        user.setUsername(username);
        user.setPassword(encryptedPwd);
        user.setSalt(salt);
        userMapper.insert(user);
        return true;
    }

    public User login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null) return null;
        String encryptedPwd = md5(password, user.getSalt());
        if (encryptedPwd.equals(user.getPassword())) {
            return user;
        }
        return null;
    }
    public User findById(Long id) {
        return userMapper.findById(id);
    }
}