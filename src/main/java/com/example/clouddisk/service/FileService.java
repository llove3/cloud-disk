package com.example.clouddisk.service;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.FileVersion;
import com.example.clouddisk.entity.User;
import com.example.clouddisk.mapper.FileMapper;
import com.example.clouddisk.mapper.FileVersionMapper;
import com.example.clouddisk.mapper.UserMapper;
import com.example.clouddisk.ai.AiIndexTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Autowired
    private AiIndexTaskService aiIndexTaskService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private static final Map<String, List<String>> CATEGORY_EXTENSIONS = new HashMap<>();

    static {
        CATEGORY_EXTENSIONS.put("image", Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"));
        CATEGORY_EXTENSIONS.put("document", Arrays.asList("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md"));
        CATEGORY_EXTENSIONS.put("video", Arrays.asList("mp4", "avi", "mov", "wmv", "flv", "mkv"));
        CATEGORY_EXTENSIONS.put("audio", Arrays.asList("mp3", "wav", "flac", "aac", "ogg"));
        CATEGORY_EXTENSIONS.put("archive", Arrays.asList("zip", "rar", "7z", "tar", "gz"));
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "unnamed";
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

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
        validateFolder(userId, parentId);
        String md5 = getFileMd5(file);
        Long fileSize = file.getSize();
        String originalName = sanitizeFileName(file.getOriginalFilename());

        FileInfo sameMd5File = fileMapper.findByMd5AndSize(md5, fileSize);
        String storePath;
        if (sameMd5File != null && Files.exists(Paths.get(sameMd5File.getFilePath()))) {
            storePath = sameMd5File.getFilePath();
        } else {
            Path userPath = Paths.get(uploadDir, String.valueOf(userId));
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
            storePath = userPath.resolve(uniqueName).toString();
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
            fileInfo.setStarred(false);
            fileMapper.insert(fileInfo);
            userMapper.addUsedSpace(userId, fileSize);
            aiIndexTaskService.enqueue(fileInfo.getId(), userId, "UPSERT");
        } else {
            if (existing.isFolder()) throw new IllegalArgumentException("同名文件夹已存在");
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
            aiIndexTaskService.enqueue(existing.getId(), userId, "UPSERT");
        }
        return userService.getUpdatedUser(userId);
    }

    public List<FileInfo> listFiles(Long userId, Long parentId) {
        return listFiles(userId, parentId, null, "name", "asc");
    }

    public List<FileInfo> listFiles(Long userId, Long parentId, String category, String sortBy, String order) {
        List<FileInfo> files = fileMapper.findByUserIdAndParentId(userId, parentId);
        if (category != null && !category.isEmpty()) {
            files = files.stream()
                    .filter(file -> file.getFileSize() > 0 && matchesCategory(file.getFileName(), category))
                    .collect(Collectors.toList());
        }
        Comparator<FileInfo> comparator = getComparator(sortBy, order);
        files.sort(comparator);
        return files;
    }

    private Comparator<FileInfo> getComparator(String sortBy, String order) {
        Comparator<FileInfo> comparator;
        switch (sortBy) {
            case "size":
                comparator = Comparator.comparing(FileInfo::getFileSize);
                break;
            case "time":
                comparator = Comparator.comparing(FileInfo::getCreatedAt);
                break;
            default:
                comparator = Comparator.comparing(FileInfo::getFileName, String.CASE_INSENSITIVE_ORDER);
        }
        if ("desc".equalsIgnoreCase(order)) {
            comparator = comparator.reversed();
        }
        Comparator<FileInfo> folderFirst = (f1, f2) -> {
            boolean f1IsFolder = (f1.getFileSize() == 0 && (f1.getFilePath() == null || f1.getFilePath().isEmpty()));
            boolean f2IsFolder = (f2.getFileSize() == 0 && (f2.getFilePath() == null || f2.getFilePath().isEmpty()));
            if (f1IsFolder && !f2IsFolder) return -1;
            if (!f1IsFolder && f2IsFolder) return 1;
            return 0;
        };
        return folderFirst.thenComparing(comparator);
    }

    private boolean matchesCategory(String fileName, String category) {
        if (fileName == null) return false;
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex == -1) return false;
        String ext = fileName.substring(dotIndex + 1).toLowerCase();
        List<String> exts = CATEGORY_EXTENSIONS.get(category);
        if (exts == null) return false;
        return exts.contains(ext);
    }

    public List<FileInfo> getFilesByCategory(Long userId, String category) {
        List<FileInfo> allNonFolderFiles = fileMapper.findAllNonFolderFilesByUserId(userId);
        List<FileInfo> filtered = allNonFolderFiles.stream()
                .filter(file -> matchesCategory(file.getFileName(), category))
                .collect(Collectors.toList());
        for (FileInfo file : filtered) {
            file.setLocation(buildLocationPath(file.getParentId()));
        }
        return filtered;
    }

    public List<FileInfo> searchFiles(Long userId, String keyword, String category) {
        List<FileInfo> files = fileMapper.searchByName(userId, keyword);
        if (category != null && !category.isEmpty()) {
            files = files.stream()
                    .filter(file -> matchesCategory(file.getFileName(), category))
                    .collect(Collectors.toList());
        }
        for (FileInfo file : files) {
            file.setLocation(buildLocationPath(file.getParentId()));
        }
        return files;
    }

    private String buildLocationPath(Long parentId) {
        if (parentId == 0) {
            return "根目录";
        }
        List<String> names = new ArrayList<>();
        Long currentParentId = parentId;
        while (currentParentId != 0) {
            FileInfo parent = fileMapper.findById(currentParentId);
            if (parent == null) break;
            names.add(0, parent.getFileName());
            currentParentId = parent.getParentId();
        }
        names.add(0, "根目录");
        return String.join(" / ", names);
    }

    public FileInfo getFile(Long fileId, Long userId) {
        return fileMapper.findByIdAndUserId(fileId, userId);
    }

    @Transactional
    public void deleteFile(Long fileId, Long userId) {
        FileInfo file = getFile(fileId, userId);
        if (file != null) {
            fileMapper.softDeleteById(fileId);
            aiIndexTaskService.enqueue(fileId, userId, "DELETE");
        }
    }

    public List<FileInfo> listRecycleBin(Long userId) {
        return fileMapper.findRecycleBinByUserId(userId);
    }

    @Transactional
    public void restoreFile(Long fileId, Long userId) {
        FileInfo file = fileMapper.findByIdAndUserIdIncludeDeleted(fileId, userId);
        if (file != null && file.getDeleted()) {
            fileMapper.restoreById(fileId);
            aiIndexTaskService.enqueue(fileId, userId, "UPSERT");
        }
    }

    @Transactional
    public void permanentDelete(Long fileId, Long userId) throws IOException {
        FileInfo file = fileMapper.findByIdAndUserIdIncludeDeleted(fileId, userId);
        if (file != null && file.getDeleted()) {
            List<FileVersion> versions = fileVersionMapper.findByFileId(fileId);
            Set<String> paths = new HashSet<>();
            if (file.getFilePath() != null && !file.getFilePath().isEmpty()) paths.add(file.getFilePath());
            long totalVersionSize = 0L;
            for (FileVersion version : versions) {
                totalVersionSize += version.getFileSize();
                if (version.getFilePath() != null && !version.getFilePath().isEmpty()) paths.add(version.getFilePath());
            }
            aiIndexTaskService.enqueue(fileId, userId, "DELETE");
            fileVersionMapper.deleteByFileId(fileId);
            fileMapper.permanentDeleteById(fileId);
            userMapper.subUsedSpace(userId, file.getFileSize());
            userMapper.subUsedSpace(userId, totalVersionSize);
            deleteUnreferencedAfterCommit(paths);
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
        aiIndexTaskService.enqueue(fileId, userId, "UPSERT");
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
        fileVersionMapper.deleteById(versionId);
        userMapper.subUsedSpace(userId, version.getFileSize());
        if (version.getFilePath() != null && !version.getFilePath().isEmpty())
            deleteUnreferencedAfterCommit(Set.of(version.getFilePath()));
    }

    public void createFolder(Long userId, Long parentId, String folderName) {
        validateFolder(userId, parentId);
        String safeName = sanitizeFileName(folderName);
        FileInfo existing = fileMapper.findByUserIdAndParentIdAndFileName(userId, parentId, safeName);
        if (existing != null) {
            throw new RuntimeException("文件夹已存在");
        }
        FileInfo folder = new FileInfo();
        folder.setUserId(userId);
        folder.setFileName(safeName);
        folder.setFileSize(0L);
        folder.setFilePath("");
        folder.setFileMd5("");
        folder.setParentId(parentId);
        folder.setVersion(1);
        folder.setDeleted(false);
        folder.setStarred(false);
        fileMapper.insert(folder);
    }

    private void validateFolder(Long userId, Long parentId) {
        if (parentId == null || parentId == 0) return;
        FileInfo parent = fileMapper.findByIdAndUserId(parentId, userId);
        if (parent == null || !parent.isFolder()) throw new IllegalArgumentException("目标文件夹不存在或无权访问");
    }

    @Transactional
    public void batchDelete(List<Long> fileIds, Long userId) {
        for (Long fileId : fileIds) {
            FileInfo file = getFile(fileId, userId);
            if (file != null) {
                fileMapper.softDeleteById(fileId);
                aiIndexTaskService.enqueue(fileId, userId, "DELETE");
            }
        }
    }

    public byte[] batchDownloadAsZip(List<Long> fileIds, Long userId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeSelectedToZip(zos, fileIds, userId);
        }
        return baos.toByteArray();
    }

    public void writeSelectedToZip(ZipOutputStream zos, List<Long> fileIds, Long userId) throws IOException {
        if (fileIds == null || fileIds.isEmpty()) throw new IllegalArgumentException("请至少选择一个文件");
        Set<String> entries = new HashSet<>();
        int count = 0;
        for (Long fileId : new LinkedHashSet<>(fileIds)) {
            FileInfo file = getFile(fileId, userId);
            if (file == null) throw new IllegalArgumentException("选中的文件不存在或无权访问");
            count += addSelectedToZip(zos, file, userId, "", entries);
        }
        if (count == 0) throw new IllegalArgumentException("所选文件夹中没有可打包的文件");
    }

    private int addSelectedToZip(ZipOutputStream zos, FileInfo file, Long userId,
                                 String parent, Set<String> entries) throws IOException {
        String name = file.getFileName().replaceAll("[\\\\/]", "_");
        String entry = parent + name;
        if (file.isFolder()) {
            String directory = entry + "/";
            if (!entries.add(directory)) throw new IllegalArgumentException("打包文件中存在同名路径：" + directory);
            zos.putNextEntry(new ZipEntry(directory));
            zos.closeEntry();
            int count = 0;
            for (FileInfo child : fileMapper.findByUserIdAndParentId(userId, file.getId()))
                count += addSelectedToZip(zos, child, userId, directory, entries);
            return count;
        }
        Path path = Paths.get(file.getFilePath());
        if (!Files.isRegularFile(path)) throw new IOException("文件不存在：" + file.getFileName());
        if (!entries.add(entry)) throw new IllegalArgumentException("打包文件中存在同名路径：" + entry);
        zos.putNextEntry(new ZipEntry(entry));
        Files.copy(path, zos);
        zos.closeEntry();
        return 1;
    }

    public static String zipName(String name) {
        String value = name == null || name.isBlank() ? "我的打包文件" : name.trim();
        if (value.matches(".*[\\\\/:*?\"<>|].*") || value.endsWith(".")
                || value.equalsIgnoreCase(".zip")
                || value.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\.zip)?")
                || value.length() > 120) throw new IllegalArgumentException("打包名称含非法字符或过长");
        return value.toLowerCase(Locale.ROOT).endsWith(".zip") ? value : value + ".zip";
    }

    public byte[] downloadFolderAsZip(Long folderId, Long userId) throws IOException {
        FileInfo folder = fileMapper.findByIdAndUserId(folderId, userId);
        if (folder == null || folder.getFileSize() != 0 || !folder.getFilePath().isEmpty()) {
            throw new RuntimeException("文件夹不存在或无权访问");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addFolderToZip(zos, folder, userId, "");
        }
        return baos.toByteArray();
    }

    private void addFolderToZip(ZipOutputStream zos, FileInfo folder, Long userId, String parentPath) throws IOException {
        String folderPath = parentPath + folder.getFileName() + "/";
        ZipEntry folderEntry = new ZipEntry(folderPath);
        zos.putNextEntry(folderEntry);
        zos.closeEntry();

        List<FileInfo> children = fileMapper.findByUserIdAndParentId(userId, folder.getId());
        for (FileInfo child : children) {
            if (child.getFileSize() == 0 && child.getFilePath().isEmpty()) {
                addFolderToZip(zos, child, userId, folderPath);
            } else {
                Path filePath = Paths.get(child.getFilePath());
                if (Files.exists(filePath)) {
                    ZipEntry fileEntry = new ZipEntry(folderPath + child.getFileName());
                    zos.putNextEntry(fileEntry);
                    Files.copy(filePath, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    @Transactional
    public void renameFile(Long fileId, Long userId, String newName) {
        FileInfo file = fileMapper.findByIdAndUserId(fileId, userId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权访问");
        }
        String safeName = sanitizeFileName(newName);
        FileInfo existing = fileMapper.findByUserIdAndParentIdAndFileName(userId, file.getParentId(), safeName);
        if (existing != null && !existing.getId().equals(fileId)) {
            throw new RuntimeException("该目录下已存在同名文件或文件夹");
        }
        file.setFileName(safeName);
        fileMapper.update(file);
        aiIndexTaskService.enqueue(fileId, userId, "UPSERT");
    }

    @Transactional
    public void moveFile(Long fileId, Long userId, Long targetParentId) {
        FileInfo file = fileMapper.findByIdAndUserId(fileId, userId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权访问");
        }
        if (targetParentId.equals(file.getParentId())) {
            return;
        }
        if (targetParentId != 0) {
            FileInfo targetFolder = fileMapper.findByIdAndUserId(targetParentId, userId);
            if (targetFolder == null || targetFolder.getFileSize() != 0 || !targetFolder.getDeleted().equals(false)) {
                throw new RuntimeException("目标文件夹不存在");
            }
            if (!targetFolder.isFolder()) throw new RuntimeException("目标不是文件夹");
            Long ancestor = targetParentId;
            while (ancestor != null && ancestor != 0) {
                if (ancestor.equals(fileId)) throw new RuntimeException("不能移入自身或子文件夹");
                FileInfo parent = fileMapper.findByIdAndUserId(ancestor, userId);
                ancestor = parent == null ? 0L : parent.getParentId();
            }
        }
        FileInfo conflict = fileMapper.findByUserIdAndParentIdAndFileName(userId, targetParentId, file.getFileName());
        if (conflict != null && !conflict.getId().equals(fileId)) {
            throw new RuntimeException("目标文件夹已存在同名文件或文件夹");
        }
        file.setParentId(targetParentId);
        fileMapper.update(file);
    }

    private void deleteUnreferencedAfterCommit(Set<String> paths) {
        Runnable cleanup = () -> {
            for (String path : paths) {
                if (path == null || path.isEmpty() || fileMapper.countReferencesByPath(path) != 0) continue;
                try { Files.deleteIfExists(Paths.get(path)); }
                catch (IOException e) { throw new RuntimeException("无法删除无引用文件: " + path, e); }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup.run(); }
            });
        } else cleanup.run();
    }

    @Transactional
    public void batchMove(List<Long> fileIds, Long userId, Long targetParentId) {
        for (Long fileId : fileIds) {
            moveFile(fileId, userId, targetParentId);
        }
    }

    @Transactional
    public void toggleStar(Long fileId, Long userId) {
        FileInfo file = fileMapper.findByIdAndUserId(fileId, userId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权访问");
        }
        file.setStarred(!Boolean.TRUE.equals(file.getStarred()));
        fileMapper.update(file);
    }

    public List<FileInfo> getStarredFiles(Long userId) {
        return fileMapper.findStarredByUserId(userId);
    }

    @Transactional
    public void updateRemark(Long fileId, Long userId, String remark) {
        FileInfo file = fileMapper.findByIdAndUserId(fileId, userId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权访问");
        }
        file.setRemark(remark);
        fileMapper.update(file);
    }

    @Transactional
    public void autoCleanRecycleBin() {
        List<User> users = userMapper.findAll();
        for (User user : users) {
            Integer days = user.getRecycleRetentionDays();
            if (days != null && days > 0) {
                List<FileInfo> oldFiles = fileMapper.findRecycleFilesOlderThan(user.getId(), days);
                for (FileInfo file : oldFiles) {
                    try {
                        permanentDelete(file.getId(), user.getId());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
