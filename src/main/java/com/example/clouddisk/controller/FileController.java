package com.example.clouddisk.controller;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.FileVersion;
import com.example.clouddisk.entity.User;
import com.example.clouddisk.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/file")
public class FileController {

    @Autowired
    private FileService fileService;

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "parentId", defaultValue = "0") Long parentId,
                         HttpSession session) {
        User user = (User) session.getAttribute("user");
        System.out.println("=== 上传请求开始 ===");
        System.out.println("当前登录用户：" + (user == null ? "null" : user.getUsername() + ", id=" + user.getId()));
        if (user == null) {
            return "请先登录";
        }
        try {
            FileInfo saved = fileService.saveFile(user.getId(), parentId, file);
            System.out.println("上传成功，文件ID：" + saved.getId());
            return "上传成功";
        } catch (Exception e) {
            e.printStackTrace();
            return "上传失败：" + e.getMessage();
        }
    }

    @GetMapping("/list")
    public List<FileInfo> list(@RequestParam(value = "parentId", defaultValue = "0") Long parentId,
                               HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return null;
        }
        return fileService.listFiles(user.getId(), parentId);
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam Long fileId, HttpSession session) throws IOException {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        FileInfo fileInfo = fileService.getFile(fileId, user.getId());
        if (fileInfo == null) {
            return ResponseEntity.status(404).build();
        }
        Path path = Paths.get(fileInfo.getFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.status(404).build();
        }
        byte[] data = Files.readAllBytes(path);
        String encodedFileName = URLEncoder.encode(fileInfo.getFileName(), StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long fileId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "请先登录";
        }
        fileService.deleteFile(fileId, user.getId());
        return "删除成功";
    }

    @GetMapping("/versions")
    public List<FileVersion> getVersions(@RequestParam Long fileId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return null;
        }
        return fileService.getFileVersions(fileId, user.getId());
    }

    @PostMapping("/rollback")
    public String rollback(@RequestParam Long fileId, @RequestParam Integer version, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "请先登录";
        }
        try {
            fileService.rollbackToVersion(fileId, user.getId(), version);
            return "回滚成功";
        } catch (Exception e) {
            e.printStackTrace();
            return "回滚失败：" + e.getMessage();
        }
    }
}