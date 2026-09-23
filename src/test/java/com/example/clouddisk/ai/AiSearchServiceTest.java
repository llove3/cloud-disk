package com.example.clouddisk.ai;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.mapper.FileMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSearchServiceTest {
    HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void filtersOtherUsersHitsAndStaleVersionsEvenIfSearchEngineReturnsThem() throws Exception {
        List<String> bodies = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cloud_disk_chunks", exchange -> {
            if (exchange.getRequestMethod().equals("HEAD")) { exchange.sendResponseHeaders(200, -1); exchange.close(); return; }
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String json = """
                    {"hits":{"hits":[
                      {"_id":"1:2:0","_source":{"fileId":1,"generation":2,"version":1,"content":"private current"}},
                      {"_id":"2:1:0","_source":{"fileId":2,"generation":1,"version":1,"content":"other user"}},
                      {"_id":"1:1:0","_source":{"fileId":1,"generation":1,"version":1,"content":"old version"}}
                    ]}}
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed("question")).thenReturn(new float[384]);
        FileMapper files = mock(FileMapper.class);
        FileInfo mine = new FileInfo(); mine.setId(1L); mine.setFilePath("content.txt");
        mine.setFileName("mine.txt"); mine.setIndexGeneration(2L); mine.setVersion(1);
        when(files.findByIdAndUserId(1L, 10L)).thenReturn(mine);
        AiSearchService search = new AiSearchService("http://127.0.0.1:" + server.getAddress().getPort(), model, files);

        var hits = search.search(10L, "question", List.of(), 10);

        assertEquals(1, hits.size());
        assertEquals(1L, hits.get(0).fileId());
        assertEquals("private current", hits.get(0).snippet());
        assertEquals(2, bodies.size());
        assertTrue(bodies.stream().allMatch(body -> body.contains("ownerId") && body.contains("10")));
    }

    @Test
    void summaryQuestionReturnsSelectedFilesWithoutSimilarityThreshold() throws Exception {
        List<String> bodies = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cloud_disk_chunks", exchange -> {
            if (exchange.getRequestMethod().equals("HEAD")) { exchange.sendResponseHeaders(200, -1); exchange.close(); return; }
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = """
                    {"hits":{"hits":[{"_id":"1:1:0","_source":{"fileId":1,"generation":1,"version":1,"content":"a useful passage"}}]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        FileMapper files = mock(FileMapper.class);
        FileInfo mine = new FileInfo(); mine.setId(1L); mine.setFilePath("content.txt");
        mine.setFileName("mine.txt"); mine.setIndexGeneration(1L); mine.setVersion(1);
        when(files.findByIdAndUserId(1L, 10L)).thenReturn(mine);
        AiSearchService search = new AiSearchService("http://127.0.0.1:" + server.getAddress().getPort(), mock(EmbeddingModel.class), files);

        var hits = search.search(10L, "这几份文档讲了啥", List.of(1L, 3L), 6);

        assertEquals(1, hits.size());
        assertEquals("a useful passage", hits.get(0).snippet());
        assertEquals(1, bodies.size());
        assertTrue(bodies.get(0).contains("ownerId"));
        assertTrue(bodies.get(0).contains("fileId"));
        assertTrue(bodies.get(0).contains("collapse"));
    }
}
