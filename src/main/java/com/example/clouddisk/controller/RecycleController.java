package com.example.clouddisk.controller;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.User;
import com.example.clouddisk.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/recycle")
public class RecycleController {

    @Autowired
    private FileService fileService;

    @GetMapping("/list")
    public List<FileInfo> list(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return null;
        return fileService.listRecycleBin(user.getId());
    }

    @PostMapping("/restore")
    public String restore(@RequestParam Long fileId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "请先登录";
        fileService.restoreFile(fileId, user.getId());
        return "还原成功";
    }

    @PostMapping("/permanent")
    public String permanent(@RequestParam Long fileId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "请先登录";
        try {
            fileService.permanentDelete(fileId, user.getId());
            return "彻底删除成功";
        } catch (IOException e) {
            e.printStackTrace();
            return "彻底删除失败：" + e.getMessage();
        }
    }
}