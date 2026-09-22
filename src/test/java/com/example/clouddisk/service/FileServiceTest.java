package com.example.clouddisk.service;

import com.example.clouddisk.ai.AiIndexTaskService;
import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.FileVersion;
import com.example.clouddisk.mapper.FileMapper;
import com.example.clouddisk.mapper.FileVersionMapper;
import com.example.clouddisk.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {
    @Mock FileMapper files;
    @Mock FileVersionMapper versions;
    @Mock UserMapper users;
    @Mock AiIndexTaskService tasks;
    @InjectMocks FileService service;
    @TempDir Path temp;

    private FileInfo deleted(Long id, Long owner, String path) {
        FileInfo file = new FileInfo();
        file.setId(id); file.setUserId(owner); file.setFilePath(path);
        file.setFileSize(10L); file.setDeleted(true); file.setFileMd5("hash");
        return file;
    }

    @Test
    void permanentDeletePreservesPathReferencedByAnotherCurrentFileOrVersion() throws Exception {
        Path path = Files.writeString(temp.resolve("shared.txt"), "shared");
        FileInfo file = deleted(1L, 10L, path.toString());
        FileVersion version = new FileVersion();
        version.setFilePath(path.toString()); version.setFileSize(10L);
        when(files.findByIdAndUserIdIncludeDeleted(1L, 10L)).thenReturn(file);
        when(versions.findByFileId(1L)).thenReturn(List.of(version));
        when(files.countReferencesByPath(path.toString())).thenReturn(1);

        service.permanentDelete(1L, 10L);

        assertTrue(Files.exists(path));
        verify(versions).deleteByFileId(1L);
        verify(files).permanentDeleteById(1L);
        verify(tasks).enqueue(1L, 10L, "DELETE");
    }

    @Test
    void permanentDeleteRemovesUnreferencedPhysicalPath() throws Exception {
        Path path = Files.writeString(temp.resolve("alone.txt"), "alone");
        when(files.findByIdAndUserIdIncludeDeleted(1L, 10L)).thenReturn(deleted(1L, 10L, path.toString()));
        when(versions.findByFileId(1L)).thenReturn(List.of());
        when(files.countReferencesByPath(path.toString())).thenReturn(0);

        service.permanentDelete(1L, 10L);

        assertFalse(Files.exists(path));
    }

    @Test
    void moveCannotTargetAnotherUsersFolder() {
        FileInfo file = deleted(1L, 10L, "content.txt");
        file.setDeleted(false); file.setParentId(0L);
        when(files.findByIdAndUserId(1L, 10L)).thenReturn(file);
        when(files.findByIdAndUserId(2L, 10L)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.moveFile(1L, 10L, 2L));
        verify(files, never()).update(any());
    }
}
