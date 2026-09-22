package com.example.clouddisk.ai;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.mapper.FileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiIndexConsumerTest {
    @Mock AiIndexTaskMapper tasks;
    @Mock FileMapper files;
    @Mock AiSearchService search;
    @Mock RabbitTemplate rabbit;

    @Test
    void duplicateDeliveryDoesNotIndexAgain() {
        when(tasks.startAttempt(7L)).thenReturn(0);
        new AiIndexConsumer(tasks, files, search, rabbit).consume("7");
        verifyNoInteractions(search, files);
    }

    @Test
    void failureRetriesThenDeadLetters() {
        AiIndexTask task = new AiIndexTask();
        task.setId(7L); task.setFileId(1L); task.setGeneration(1L); task.setOperation("UPSERT");
        FileInfo file = new FileInfo(); file.setId(1L); file.setIndexGeneration(1L);
        when(tasks.startAttempt(7L)).thenReturn(1);
        when(tasks.findById(7L)).thenReturn(task);
        when(files.findById(1L)).thenReturn(file);
        doThrow(new RuntimeException("Elasticsearch unavailable")).when(search).deleteFile(1L);
        AiIndexConsumer consumer = new AiIndexConsumer(tasks, files, search, rabbit);

        task.setAttempts(1);
        consumer.consume("7");
        verify(tasks).setStatus(7L, "RETRY", "Elasticsearch unavailable");
        verify(rabbit).convertAndSend(AiRabbitConfig.EXCHANGE, "index", "7");

        task.setAttempts(3);
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.consume("7"));
        verify(tasks).setStatus(7L, "FAILED", "Elasticsearch unavailable");
    }

    @Test
    void chunkKeepsOverlap() {
        var chunks = AiIndexConsumer.chunk("a".repeat(500));
        assertEquals(2, chunks.size());
        assertEquals(40, chunks.get(0).substring(240).length());
        assertEquals(chunks.get(0).substring(240), chunks.get(1).substring(0, 40));
    }

    @Test
    void markdownHeadingsKeepWordsWithoutMarkers() {
        var chunks = AiIndexConsumer.chunk("### 部署步骤\n安装依赖\n## 配置\n设置环境变量");
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("部署步骤 安装依赖 配置 设置环境变量"));
        assertFalse(chunks.get(0).contains("#"));
    }
}
