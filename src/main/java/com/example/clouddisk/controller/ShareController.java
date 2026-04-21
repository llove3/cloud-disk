package com.example.clouddisk.controller;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.Share;
import com.example.clouddisk.entity.User;
import com.example.clouddisk.service.FileService;
import com.example.clouddisk.service.ShareService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.List;

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
                             @RequestParam(required = false) Integer maxVisits,
                             HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            throw new RuntimeException("请先登录");
        }
        return shareService.createShare(user.getId(), fileId, password, expireDays, maxVisits);
    }

    @GetMapping("/api/share/list")
    public List<Share> listShares(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            throw new RuntimeException("请先登录");
        }
        return shareService.listShares(user.getId());
    }

    @PostMapping("/api/share/delete")
    public String deleteShare(@RequestParam Long shareId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "请先登录";
        }
        try {
            shareService.deleteShare(shareId, user.getId());
            return "删除成功";
        } catch (Exception e) {
            return e.getMessage();
        }
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

    @PostMapping("/api/share/update")
    public String updateShare(@RequestParam Long shareId,
                              @RequestParam(required = false) String password,
                              @RequestParam(required = false) Integer expireDays,
                              @RequestParam(required = false) Integer maxVisits,
                              HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "请先登录";
        try {
            shareService.updateShare(shareId, user.getId(), password, expireDays, maxVisits);
            return "更新成功";
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}