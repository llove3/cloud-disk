package com.example.clouddisk.service;

import com.example.clouddisk.entity.Share;
import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.mapper.FileMapper;
import com.example.clouddisk.mapper.ShareAccessLogMapper;
import com.example.clouddisk.mapper.ShareMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {
    @Mock ShareMapper shares;
    @Mock FileMapper files;
    @Mock ShareAccessLogMapper logs;
    @InjectMocks ShareService service;

    @Test
    void wrongPasswordNeverReadsFileOrConsumesVisit() {
        Share share = new Share(); share.setId(1L); share.setPassword("secret");
        when(shares.findByCode("code")).thenReturn(share);
        assertThrows(RuntimeException.class, () -> service.getFileByShareCode("code", "wrong", "127.0.0.1", "test"));
        verifyNoInteractions(files);
        verify(shares, never()).incrementVisitCount(anyString());
        verify(logs).insert(argThat(log -> !log.getSuccess()));
    }

    @Test
    void expiredShareNeverReadsFile() {
        Share share = new Share(); share.setId(1L); share.setExpireTime(new Date(0));
        when(shares.findByCode("code")).thenReturn(share);
        assertThrows(RuntimeException.class, () -> service.getFileByShareCode("code", null, "127.0.0.1", "test"));
        verifyNoInteractions(files);
    }

    @Test
    void folderRequiresPackageShare() {
        FileInfo folder = new FileInfo();
        folder.setFilePath("");
        when(files.findByIdAndUserId(4L, 8L)).thenReturn(folder);
        assertThrows(IllegalArgumentException.class,
                () -> service.createShare(8L, 4L, null, null, null));
        verifyNoInteractions(shares);
    }
}
