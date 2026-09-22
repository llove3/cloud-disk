package com.example.clouddisk.service;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.FileVersion;
import com.example.clouddisk.entity.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class PrivateMetadataTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void apiJsonDoesNotExposePasswordsStoragePathsOrHashes() {
        User user = new User(); user.setPassword("hash"); user.setSalt("salt");
        FileInfo file = new FileInfo(); file.setFilePath("C:/private/owner/file.txt"); file.setFileMd5("md5");
        FileVersion version = new FileVersion(); version.setFilePath("C:/private/owner/old.txt"); version.setFileMd5("md5");

        assertFalse(json.writeValueAsString(user).contains("hash"));
        assertFalse(json.writeValueAsString(user).contains("salt"));
        assertFalse(json.writeValueAsString(file).contains("C:/private"));
        assertFalse(json.writeValueAsString(file).contains("md5"));
        assertFalse(json.writeValueAsString(version).contains("C:/private"));
        assertFalse(json.writeValueAsString(version).contains("md5"));
        assertTrue(json.writeValueAsString(file).contains("folder"));
    }
}
