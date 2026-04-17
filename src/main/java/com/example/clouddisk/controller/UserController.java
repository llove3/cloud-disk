package com.example.clouddisk.controller;

import com.example.clouddisk.entity.User;
import com.example.clouddisk.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public String register(@RequestParam String username, @RequestParam String password) {
        boolean ok = userService.register(username, password);
        return ok ? "注册成功" : "用户名已存在";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, HttpSession session) {
        User user = userService.login(username, password);
        if (user != null) {
            session.setAttribute("user", user);
            return "登录成功";
        } else {
            return "用户名或密码错误";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "已退出";
    }

    @GetMapping("/info")
    public User info(HttpSession session) {
        return (User) session.getAttribute("user");
    }
}