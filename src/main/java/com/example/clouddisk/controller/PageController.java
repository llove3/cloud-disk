package com.example.clouddisk.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping({"/", "/login", "/register", "/forgot-password", "/files", "/ai", "/recycle", "/profile", "/shares", "/s/{code}"})
    public String page() { return "forward:/index.html"; }

}
