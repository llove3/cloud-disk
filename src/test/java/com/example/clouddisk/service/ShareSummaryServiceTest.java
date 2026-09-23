package com.example.clouddisk.service;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.Share;
import com.example.clouddisk.mapper.ShareMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShareSummaryServiceTest {
    @TempDir Path temp;

    @Test
    void oldEmptyPackageReportsClearReasonWithoutCallingModel() throws Exception {
        Path emptyZip = temp.resolve("old-empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyZip))) { }
        FileInfo file = new FileInfo();
        file.setId(7L);
        file.setFileName("旧打包.zip");
        file.setFilePath(emptyZip.toString());
        file.setFileSize(Files.size(emptyZip));
        file.setVersion(1);
        file.setIndexGeneration(0L);
        Share share = new Share();
        share.setIsPackage(true);
        ShareService shares = mock(ShareService.class);
        ShareMapper mapper = mock(ShareMapper.class);
        when(mapper.findByCode("old")).thenReturn(share);
        when(shares.getFileByShareCode("old", "password", "127.0.0.1", "test")).thenReturn(file);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        when(builder.build()).thenReturn(client);
        ShareSummaryService service = new ShareSummaryService(shares, mapper, redis,
                new ObjectMapper(), builder);

        var summary = service.summary("old", "password", "127.0.0.1", "test");

        assertTrue(summary.isPackage());
        assertTrue(summary.entries().isEmpty());
        assertTrue(summary.notice().contains("压缩包为空"));
        verify(shares).getFileByShareCode("old", "password", "127.0.0.1", "test");
        verifyNoInteractions(client);
    }
}
