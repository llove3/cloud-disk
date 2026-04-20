package com.example.clouddisk.controller;

import com.example.clouddisk.entity.User;
import com.example.clouddisk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;

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
}