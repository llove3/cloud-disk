package com.example.clouddisk.controller;

import com.example.clouddisk.entity.User;
import com.example.clouddisk.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/file/version")
public class FileVersionController {

    @Autowired
    private FileService fileService;

    @PostMapping("/delete")
    public String deleteVersion(@RequestParam Long versionId,
                                @RequestParam Long fileId,
                                HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "请先登录";
        }
        try {
            fileService.deleteFileVersion(versionId, fileId, user.getId());
            return "版本删除成功";
        } catch (Exception e) {
            e.printStackTrace();
            return "删除失败：" + e.getMessage();
        }
    }
}