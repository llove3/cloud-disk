package com.example.clouddisk.service;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.mapper.FileMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

@Service
public class FileService {

    @Autowired
    private FileMapper fileMapper;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private String getFileMd5(MultipartFile file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int len;
        try (var is = file.getInputStream()) {
            while ((len = is.read(buffer)) != -1) {
                md.update(buffer, 0, len);
            }
        }
        return Base64.getEncoder().encodeToString(md.digest());
    }

    public FileInfo saveFile(Long userId, Long parentId, MultipartFile file) throws Exception {
        // 1. 先计算MD5（必须在transferTo之前，因为之后流会关闭）
        String md5 = getFileMd5(file);
        System.out.println("MD5计算完成：" + md5);

        // 2. 创建用户目录
        String userDir = uploadDir + userId + "/";
        Path userPath = Paths.get(userDir);
        if (!Files.exists(userPath)) {
            Files.createDirectories(userPath);
        }

        // 3. 保存物理文件
        String originalName = file.getOriginalFilename();
        String storePath = userDir + originalName;
        file.transferTo(Paths.get(storePath));
        System.out.println("物理文件保存成功：" + storePath);

        // 4. 插入数据库
        FileInfo fileInfo = new FileInfo();
        fileInfo.setUserId(userId);
        fileInfo.setFileName(originalName);
        fileInfo.setFileSize(file.getSize());
        fileInfo.setFilePath(storePath);
        fileInfo.setFileMd5(md5);
        fileInfo.setParentId(parentId);
        fileInfo.setVersion(1);
        fileInfo.setDeleted(false);

        int rows = fileMapper.insert(fileInfo);
        System.out.println("数据库插入结果，影响行数：" + rows + "，生成ID：" + fileInfo.getId());

        if (rows == 0) {
            throw new RuntimeException("插入数据库失败");
        }
        return fileInfo;
    }

    public List<FileInfo> listFiles(Long userId, Long parentId) {
        return fileMapper.findByUserIdAndParentId(userId, parentId);
    }

    public FileInfo getFile(Long fileId, Long userId) {
        return fileMapper.findByIdAndUserId(fileId, userId);
    }

    public void deleteFile(Long fileId, Long userId) {
        FileInfo file = getFile(fileId, userId);
        if (file != null) {
            fileMapper.softDeleteById(fileId);
            System.out.println("软删除文件，ID：" + fileId);
        }
    }
}