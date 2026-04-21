package com.example.clouddisk.service;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.FileVersion;
import com.example.clouddisk.entity.User;
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

    @Autowired
    private UserService userService;

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
    public User saveFile(Long userId, Long parentId, MultipartFile file) throws Exception {
        String md5 = getFileMd5(file);
        Long fileSize = file.getSize();
        String originalName = file.getOriginalFilename();

        FileInfo sameMd5File = fileMapper.findByMd5AndSize(md5, fileSize);
        String storePath;
        if (sameMd5File != null) {
            storePath = sameMd5File.getFilePath();
        } else {
            String userDir = uploadDir + userId + "/";
            Path userPath = Paths.get(userDir);
            if (!Files.exists(userPath)) {
                Files.createDirectories(userPath);
            }
            String baseName = originalName;
            String extension = "";
            int dotIndex = originalName.lastIndexOf(".");
            if (dotIndex > 0) {
                baseName = originalName.substring(0, dotIndex);
                extension = originalName.substring(dotIndex);
            }
            String uniqueName = baseName + "_" + System.currentTimeMillis() + extension;
            storePath = userDir + uniqueName;
            file.transferTo(Paths.get(storePath));
        }

        FileInfo existing = fileMapper.findByUserIdAndParentIdAndFileName(userId, parentId, originalName);
        if (existing == null) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setUserId(userId);
            fileInfo.setFileName(originalName);
            fileInfo.setFileSize(fileSize);
            fileInfo.setFilePath(storePath);
            fileInfo.setFileMd5(md5);
            fileInfo.setParentId(parentId);
            fileInfo.setVersion(1);
            fileInfo.setDeleted(false);
            fileMapper.insert(fileInfo);
            userMapper.addUsedSpace(userId, fileSize);
        } else {
            long oldSize = existing.getFileSize();
            FileVersion version = new FileVersion();
            version.setFileId(existing.getId());
            version.setVersionNumber(existing.getVersion());
            version.setFileName(existing.getFileName());
            version.setFileSize(oldSize);
            version.setFilePath(existing.getFilePath());
            version.setFileMd5(existing.getFileMd5());
            fileVersionMapper.insert(version);
            existing.setFileSize(fileSize);
            existing.setFilePath(storePath);
            existing.setFileMd5(md5);
            existing.setVersion(existing.getVersion() + 1);
            fileMapper.update(existing);
            userMapper.subUsedSpace(userId, oldSize);
            userMapper.addUsedSpace(userId, fileSize);
        }
        return userService.getUpdatedUser(userId);
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
        }
    }

    public List<FileInfo> listRecycleBin(Long userId) {
        return fileMapper.findRecycleBinByUserId(userId);
    }

    public void restoreFile(Long fileId, Long userId) {
        FileInfo file = fileMapper.findByIdAndUserIdIncludeDeleted(fileId, userId);
        if (file != null && file.getDeleted()) {
            fileMapper.restoreById(fileId);
        }
    }

    @Transactional
    public void permanentDelete(Long fileId, Long userId) throws IOException {
        FileInfo file = fileMapper.findByIdAndUserIdIncludeDeleted(fileId, userId);
        if (file != null && file.getDeleted()) {
            boolean isFolder = (file.getFileSize() == 0 && (file.getFilePath() == null || file.getFilePath().isEmpty()));
            if (!isFolder) {
                boolean hasOtherReference = fileMapper.countByMd5AndSizeExcludingId(file.getFileMd5(), file.getFileSize(), fileId) > 0;
                if (!hasOtherReference) {
                    Path currentPath = Paths.get(file.getFilePath());
                    Files.deleteIfExists(currentPath);
                }
            }

            List<FileVersion> versions = fileVersionMapper.findByFileId(fileId);
            long totalVersionSize = 0L;
            for (FileVersion version : versions) {
                totalVersionSize += version.getFileSize();
                boolean versionHasRef = fileVersionMapper.countByMd5ExcludingId(version.getFileMd5(), version.getId()) > 0;
                if (!versionHasRef && version.getFilePath() != null && !version.getFilePath().isEmpty()) {
                    Path versionPath = Paths.get(version.getFilePath());
                    Files.deleteIfExists(versionPath);
                }
            }
            fileVersionMapper.deleteByFileId(fileId);
            fileMapper.permanentDeleteById(fileId);
            userMapper.subUsedSpace(userId, file.getFileSize());
            userMapper.subUsedSpace(userId, totalVersionSize);
        }
    }

    public List<FileVersion> getFileVersions(Long fileId, Long userId) {
        FileInfo file = fileMapper.findByIdAndUserId(fileId, userId);
        if (file == null) {
            return null;
        }
        return fileVersionMapper.findByFileId(fileId);
    }

    public FileVersion getVersionById(Long versionId, Long userId) {
        FileVersion version = fileVersionMapper.findById(versionId);
        if (version == null) return null;
        FileInfo file = fileMapper.findById(version.getFileId());
        if (file == null || !file.getUserId().equals(userId)) return null;
        return version;
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
        long oldSize = current.getFileSize();
        FileVersion currentVersionBackup = new FileVersion();
        currentVersionBackup.setFileId(current.getId());
        currentVersionBackup.setVersionNumber(current.getVersion());
        currentVersionBackup.setFileName(current.getFileName());
        currentVersionBackup.setFileSize(oldSize);
        currentVersionBackup.setFilePath(current.getFilePath());
        currentVersionBackup.setFileMd5(current.getFileMd5());
        fileVersionMapper.insert(currentVersionBackup);
        current.setFileName(targetVersion.getFileName());
        current.setFileSize(targetVersion.getFileSize());
        current.setFilePath(targetVersion.getFilePath());
        current.setFileMd5(targetVersion.getFileMd5());
        current.setVersion(current.getVersion() + 1);
        fileMapper.update(current);
        userMapper.subUsedSpace(userId, oldSize);
        userMapper.addUsedSpace(userId, targetVersion.getFileSize());
    }

    @Transactional
    public void deleteFileVersion(Long versionId, Long fileId, Long userId) throws IOException {
        FileInfo file = fileMapper.findByIdAndUserId(fileId, userId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权访问");
        }
        FileVersion version = fileVersionMapper.findById(versionId);
        if (version == null || !version.getFileId().equals(fileId)) {
            throw new RuntimeException("版本不存在");
        }
        boolean versionHasRef = fileVersionMapper.countByMd5ExcludingId(version.getFileMd5(), versionId) > 0;
        if (!versionHasRef && version.getFilePath() != null && !version.getFilePath().isEmpty()) {
            Path versionPath = Paths.get(version.getFilePath());
            Files.deleteIfExists(versionPath);
        }
        fileVersionMapper.deleteById(versionId);
        userMapper.subUsedSpace(userId, version.getFileSize());
    }

    public void createFolder(Long userId, Long parentId, String folderName) {
        FileInfo existing = fileMapper.findByUserIdAndParentIdAndFileName(userId, parentId, folderName);
        if (existing != null) {
            throw new RuntimeException("文件夹已存在");
        }
        FileInfo folder = new FileInfo();
        folder.setUserId(userId);
        folder.setFileName(folderName);
        folder.setFileSize(0L);
        folder.setFilePath("");
        folder.setFileMd5("");
        folder.setParentId(parentId);
        folder.setVersion(1);
        folder.setDeleted(false);
        fileMapper.insert(folder);
    }
}