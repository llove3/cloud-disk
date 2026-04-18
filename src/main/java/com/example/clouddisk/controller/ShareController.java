package com.example.clouddisk.controller;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.Share;
import com.example.clouddisk.entity.User;
import com.example.clouddisk.service.FileService;
import com.example.clouddisk.service.ShareService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class ShareController {

    @Autowired
    private ShareService shareService;

    @Autowired
    private FileService fileService;

    @PostMapping("/api/share/create")
    public Share createShare(@RequestParam Long fileId,
                             @RequestParam(required = false) String password,
                             @RequestParam(required = false) Integer expireDays,
                             HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            throw new RuntimeException("请先登录");
        }
        return shareService.createShare(user.getId(), fileId, password, expireDays);
    }



    @PostMapping("/s/{code}/verify")
    @ResponseBody
    public ResponseEntity<?> verifyAndDownload(@PathVariable String code,
                                               @RequestParam(required = false) String password) {
        try {
            FileInfo file = shareService.getFileByShareCode(code, password);
            Path path = Paths.get(file.getFilePath());
            if (!Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }
            byte[] data = Files.readAllBytes(path);
            String encodedFileName = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFileName)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(data);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("文件读取失败");
        }
    }
}