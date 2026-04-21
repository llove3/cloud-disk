package com.example.clouddisk.controller;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.FileVersion;
import com.example.clouddisk.entity.User;
import com.example.clouddisk.service.FileService;
import com.example.clouddisk.service.UserService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/file")
public class FileController {

    @Autowired
    private FileService fileService;
    @Autowired
    private UserService userService;

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "parentId", defaultValue = "0") Long parentId,
                                      HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }
        User fullUser = userService.findById(sessionUser.getId());
        if (fullUser.getUsedSpace() + file.getSize() > fullUser.getTotalSpace()) {
            result.put("success", false);
            result.put("message", "空间不足");
            return result;
        }
        try {
            fileService.saveFile(fullUser.getId(), parentId, file);
            User updatedUser = userService.getUpdatedUser(sessionUser.getId());
            result.put("success", true);
            result.put("message", "上传成功");
            result.put("usedSpace", updatedUser.getUsedSpace());
            result.put("totalSpace", updatedUser.getTotalSpace());
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "上传失败：" + e.getMessage());
        }
        return result;
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

    @PostMapping("/folder/create")
    public Map<String, Object> createFolder(@RequestParam String folderName,
                                            @RequestParam(value = "parentId", defaultValue = "0") Long parentId,
                                            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }
        try {
            fileService.createFolder(user.getId(), parentId, folderName);
            result.put("success", true);
            result.put("message", "文件夹创建成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/preview/{fileId}")
    public ResponseEntity<?> preview(@PathVariable Long fileId, HttpSession session) throws IOException {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401).body("请先登录");
        }
        FileInfo fileInfo = fileService.getFile(fileId, user.getId());
        if (fileInfo == null || fileInfo.getFileSize() == 0) {
            return ResponseEntity.status(404).body("文件不存在");
        }
        Path path = Paths.get(fileInfo.getFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.status(404).body("文件不存在");
        }
        String fileName = fileInfo.getFileName().toLowerCase();
        String mimeType = Files.probeContentType(path);
        if (mimeType == null) {
            mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        if (fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".java") || fileName.endsWith(".xml") || fileName.endsWith(".json") || fileName.endsWith(".properties") || fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
            String content = Files.readString(path);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(content);
        }

        byte[] data = Files.readAllBytes(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .body(data);
    }

    @GetMapping("/version/preview/{versionId}")
    public ResponseEntity<?> previewVersion(@PathVariable Long versionId, HttpSession session) throws IOException {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401).body("请先登录");
        }
        FileVersion version = fileService.getVersionById(versionId, user.getId());
        if (version == null) {
            return ResponseEntity.status(404).body("版本不存在");
        }
        Path path = Paths.get(version.getFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.status(404).body("文件不存在");
        }
        String fileName = version.getFileName().toLowerCase();
        String mimeType = Files.probeContentType(path);
        if (mimeType == null) {
            mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        if (fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".java") || fileName.endsWith(".xml") || fileName.endsWith(".json") || fileName.endsWith(".properties") || fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
            String content = Files.readString(path);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(content);
        }

        byte[] data = Files.readAllBytes(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .body(data);
    }
}