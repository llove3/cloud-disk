package com.example.clouddisk.controller;

import com.example.clouddisk.entity.User;
import com.example.clouddisk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @PostMapping("/send-code")
    public String sendCode(@RequestParam String email) {
        boolean sent = userService.sendVerificationCode(email);
        return sent ? "验证码已发送" : "发送失败";
    }

    @PostMapping("/register")
    public String register(@RequestParam String email,
                           @RequestParam String password,
                           @RequestParam String code) {
        boolean ok = userService.register(email, password, code);
        return ok ? "注册成功" : "注册失败（邮箱已存在或验证码错误）";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpSession session) {
        User user = userService.loginByEmail(email, password);
        if (user != null) {
            session.setAttribute("user", user);
            return "登录成功";
        } else {
            return "邮箱或密码错误";
        }
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email,
                                 @RequestParam String newPassword,
                                 @RequestParam String code) {
        boolean ok = userService.resetPasswordByEmail(email, newPassword, code);
        return ok ? "密码重置成功" : "重置失败";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "已退出";
    }

    @GetMapping("/info")
    public User info(HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) return null;
        return userService.findById(sessionUser.getId());
    }

    @GetMapping("/space")
    public Map<String, Object> getSpaceInfo(HttpSession session) {
        User user = (User) session.getAttribute("user");
        Map<String, Object> result = new HashMap<>();
        if (user == null) {
            result.put("used", 0L);
            result.put("total", 0L);
            return result;
        }
        User fullUser = userService.findById(user.getId());
        result.put("used", fullUser.getUsedSpace());
        result.put("total", fullUser.getTotalSpace());
        return result;
    }

    @PostMapping("/recalculate-space")
    public String recalculateSpace(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "请先登录";
        userService.recalculateUsedSpace(user.getId());
        return "空间校准成功";
    }

    @PostMapping("/update-username")
    public String updateUsername(@RequestParam String newUsername, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "请先登录";
        if (!newUsername.matches("^[a-zA-Z0-9_\\u4e00-\\u9fa5]{1,20}$")) {
            return "用户名只能包含中文、字母、数字、下划线，长度1-20";
        }
        boolean ok = userService.updateUsername(user.getId(), newUsername);
        if (ok) {
            User updated = userService.findById(user.getId());
            session.setAttribute("user", updated);
            return "用户名修改成功";
        }
        return "用户名已存在";
    }

    @PostMapping("/update-email")
    public String updateEmail(@RequestParam String newEmail,
                              @RequestParam String code,
                              HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "请先登录";
        boolean ok = userService.updateEmail(user.getId(), newEmail, code);
        if (ok) {
            User updated = userService.findById(user.getId());
            session.setAttribute("user", updated);
            return "邮箱换绑成功";
        }
        return "换绑失败";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "请先登录";
        boolean ok = userService.changePassword(user.getId(), oldPassword, newPassword);
        return ok ? "密码修改成功" : "原密码错误";
    }

    @PostMapping("/upload-avatar")
    public String uploadAvatar(@RequestParam("avatar") MultipartFile file, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "请先登录";
        if (file.isEmpty()) return "文件为空";
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        if (!ext.matches(".(jpg|jpeg|png|gif)")) {
            return "仅支持 jpg, jpeg, png, gif 格式";
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            return "头像大小不能超过2MB";
        }
        String avatarDir = uploadDir + "avatars/";
        File dir = new File(avatarDir);
        if (!dir.exists()) dir.mkdirs();
        String newFileName = user.getId() + "_" + UUID.randomUUID().toString() + ext;
        File dest = new File(avatarDir + newFileName);
        try {
            file.transferTo(dest);
            String avatarUrl = "/avatars/" + newFileName;
            userService.updateAvatar(user.getId(), avatarUrl);
            User updated = userService.findById(user.getId());
            session.setAttribute("user", updated);
            return avatarUrl;
        } catch (IOException e) {
            e.printStackTrace();
            return "上传失败";
        }
    }

    @PostMapping("/update-recycle-retention")
    public String updateRecycleRetention(@RequestParam Integer days, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "请先登录";
        userService.updateRecycleRetentionDays(user.getId(), days);
        User updated = userService.findById(user.getId());
        session.setAttribute("user", updated);
        return "设置成功";
    }
}