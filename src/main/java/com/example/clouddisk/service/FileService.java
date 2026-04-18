package com.example.clouddisk.service;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.FileVersion;
import com.example.clouddisk.mapper.FileMapper;
import com.example.clouddisk.mapper.FileVersionMapper;
import com.example.clouddisk.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    @Autowired
    private FileVersionMapper fileVersionMapper;

    @Autowired
    private UserMapper userMapper;

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

    @Transactional
    public FileInfo saveFile(Long userId, Long parentId, MultipartFile file) throws Exception {
        String md5 = getFileMd5(file);
        System.out.println("MD5计算完成：" + md5);

        String userDir = uploadDir + userId + "/";
        Path userPath = Paths.get(userDir);
        if (!Files.exists(userPath)) {
            Files.createDirectories(userPath);
        }

        String originalName = file.getOriginalFilename();
        String baseName = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf(".");
        if (dotIndex > 0) {
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }
        String uniqueName = baseName + "_" + System.currentTimeMillis() + extension;
        String storePath = userDir + uniqueName;

        file.transferTo(Paths.get(storePath));
        System.out.println("物理文件保存成功：" + storePath);

        FileInfo existing = fileMapper.findByUserIdAndParentIdAndFileName(userId, parentId, originalName);

        if (existing == null) {
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
            System.out.println("新增文件，影响行数：" + rows + "，生成ID：" + fileInfo.getId());
            userMapper.addUsedSpace(userId, file.getSize());
            return fileInfo;
        } else {
            FileVersion version = new FileVersion();
            version.setFileId(existing.getId());
            version.setVersionNumber(existing.getVersion());
            version.setFileName(existing.getFileName());
            version.setFileSize(existing.getFileSize());
            version.setFilePath(existing.getFilePath());
            version.setFileMd5(existing.getFileMd5());
            fileVersionMapper.insert(version);

            existing.setFileSize(file.getSize());
            existing.setFilePath(storePath);
            existing.setFileMd5(md5);
            existing.setVersion(existing.getVersion() + 1);
            fileMapper.update(existing);
            System.out.println("覆盖上传，版本号更新为：" + existing.getVersion());
            userMapper.addUsedSpace(userId, file.getSize());
            return existing;
        }
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

    public List<FileInfo> listRecycleBin(Long userId) {
        return fileMapper.findRecycleBinByUserId(userId);
    }

    public void restoreFile(Long fileId, Long userId) {
        FileInfo file = fileMapper.findByIdAndUserIdIncludeDeleted(fileId, userId);
        if (file != null && file.getDeleted()) {
            fileMapper.restoreById(fileId);
            System.out.println("还原文件成功，ID：" + fileId);
        } else {
            System.out.println("还原失败：文件不存在或未被删除，ID：" + fileId);
        }
    }

    public void permanentDelete(Long fileId, Long userId) throws IOException {
        FileInfo file = fileMapper.findByIdAndUserIdIncludeDeleted(fileId, userId);
        if (file != null && file.getDeleted()) {
            Path currentPath = Paths.get(file.getFilePath());
            Files.deleteIfExists(currentPath);
            System.out.println("删除当前版本物理文件：" + file.getFilePath());

            List<FileVersion> versions = fileVersionMapper.findByFileId(fileId);
            for (FileVersion version : versions) {
                Path versionPath = Paths.get(version.getFilePath());
                Files.deleteIfExists(versionPath);
                System.out.println("删除历史版本物理文件：" + version.getFilePath());
            }

            fileVersionMapper.deleteByFileId(fileId);
            fileMapper.permanentDeleteById(fileId);
            System.out.println("彻底删除文件完成，ID：" + fileId);
            userMapper.subUsedSpace(userId, file.getFileSize());
        }
    }

    public List<FileVersion> getFileVersions(Long fileId, Long userId) {
        FileInfo file = fileMapper.findByIdAndUserId(fileId, userId);
        if (file == null) {
            return null;
        }
        return fileVersionMapper.findByFileId(fileId);
    }

    @Transactional
    public void rollbackToVersion(Long fileId, Long userId, Integer versionNumber) throws Exception {
        FileInfo current = fileMapper.findByIdAndUserId(fileId, userId);
        if (current == null) {
            throw new RuntimeException("文件不存在或无权访问");
        }

        FileVersion targetVersion = fileVersionMapper.findByFileIdAndVersion(fileId, versionNumber);
        if (targetVersion == null) {
            throw new RuntimeException("指定版本不存在");
        }

        FileVersion currentVersionBackup = new FileVersion();
        currentVersionBackup.setFileId(current.getId());
        currentVersionBackup.setVersionNumber(current.getVersion());
        currentVersionBackup.setFileName(current.getFileName());
        currentVersionBackup.setFileSize(current.getFileSize());
        currentVersionBackup.setFilePath(current.getFilePath());
        currentVersionBackup.setFileMd5(current.getFileMd5());
        fileVersionMapper.insert(currentVersionBackup);

        current.setFileName(targetVersion.getFileName());
        current.setFileSize(targetVersion.getFileSize());
        current.setFilePath(targetVersion.getFilePath());
        current.setFileMd5(targetVersion.getFileMd5());
        current.setVersion(current.getVersion() + 1);
        fileMapper.update(current);

        System.out.println("回滚成功，新版本号：" + current.getVersion());
    }
}