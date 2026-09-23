package com.example.clouddisk.service;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.mapper.FileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileZipTest {
    @TempDir Path temp;

    private FileInfo file(long id, String name, String path) {
        FileInfo item = new FileInfo();
        item.setId(id);
        item.setFileName(name);
        item.setFilePath(path);
        item.setFileSize(path.isEmpty() ? 0L : 5L);
        return item;
    }

    @Test
    void batchZipIncludesNestedFolderContents() throws Exception {
        Path source = Files.writeString(temp.resolve("content.txt"), "hello");
        FileInfo root = file(1, "资料", "");
        FileInfo nested = file(2, "子目录", "");
        FileInfo leaf = file(3, "说明.txt", source.toString());
        FileMapper mapper = mock(FileMapper.class);
        when(mapper.findByIdAndUserId(1L, 10L)).thenReturn(root);
        when(mapper.findByUserIdAndParentId(10L, 1L)).thenReturn(List.of(nested));
        when(mapper.findByUserIdAndParentId(10L, 2L)).thenReturn(List.of(leaf));
        FileService service = new FileService();
        ReflectionTestUtils.setField(service, "fileMapper", mapper);

        byte[] zip = service.batchDownloadAsZip(List.of(1L), 10L);
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            assertEquals("资料/", input.getNextEntry().getName());
            assertEquals("资料/子目录/", input.getNextEntry().getName());
            assertEquals("资料/子目录/说明.txt", input.getNextEntry().getName());
            assertEquals("hello", new String(input.readAllBytes()));
            assertNull(input.getNextEntry());
        }
    }

    @Test
    void emptyFolderCannotCreatePackageAndNameIsValidated() {
        FileMapper mapper = mock(FileMapper.class);
        when(mapper.findByIdAndUserId(1L, 10L)).thenReturn(file(1, "空目录", ""));
        FileService service = new FileService();
        ReflectionTestUtils.setField(service, "fileMapper", mapper);

        assertThrows(IllegalArgumentException.class, () -> service.batchDownloadAsZip(List.of(1L), 10L));
        assertEquals("我的打包文件.zip", FileService.zipName(null));
        assertEquals("课程资料.zip", FileService.zipName("课程资料"));
        assertThrows(IllegalArgumentException.class, () -> FileService.zipName("../private"));
        assertThrows(IllegalArgumentException.class, () -> FileService.zipName("con"));
    }
}
