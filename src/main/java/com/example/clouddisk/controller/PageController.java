package com.example.clouddisk.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    @GetMapping("/login")
    public String loginPage() { return "login"; }

    @GetMapping("/register")
    public String registerPage() { return "register"; }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() { return "forgot-password"; }

    @GetMapping("/files")
    public String filesPage() { return "files"; }

    @GetMapping("/recycle")
    public String recyclePage() { return "recycle"; }

    @GetMapping("/profile")
    public String profilePage() { return "profile"; }

    @GetMapping("/s/{code}")
    public String sharePage(@PathVariable String code) { return "share"; }

    @GetMapping("/shares")
    public String sharesPage() { return "shares"; }



}