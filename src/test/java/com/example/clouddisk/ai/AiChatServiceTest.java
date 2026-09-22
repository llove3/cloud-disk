package com.example.clouddisk.ai;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiChatServiceTest {
    static class CapturingEmitter extends SseEmitter {
        final List<Object> data = new ArrayList<>();
        @Override public void send(SseEventBuilder event) throws IOException {
            event.build().forEach(part -> data.add(part.getData()));
        }
    }

    @Test
    void noResultsReturnsGroundedMessageWithoutCallingModel() {
        AiSearchService search = mock(AiSearchService.class);
        when(search.search(10L, "question", null, 6)).thenReturn(List.of());
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        when(builder.build()).thenReturn(client);
        AiChatService service = new AiChatService(search, builder, mock(StringRedisTemplate.class), new ObjectMapper());
        CapturingEmitter emitter = new CapturingEmitter();

        service.answer(10L, "question", null, true, emitter);

        assertTrue(emitter.data.stream().anyMatch(item -> item.toString().contains("未找到可用文档依据")));
        verifyNoInteractions(client);
    }

    @Test
    void searchFailureEmitsSseError() {
        AiSearchService search = mock(AiSearchService.class);
        when(search.search(10L, "question", null, 6)).thenThrow(new RuntimeException("Elasticsearch unavailable"));
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        AiChatService service = new AiChatService(search, builder, mock(StringRedisTemplate.class), new ObjectMapper());
        CapturingEmitter emitter = new CapturingEmitter();

        service.answer(10L, "question", null, false, emitter);

        assertTrue(emitter.data.stream().anyMatch(item -> item.toString().contains("问答暂时不可用")));
        assertTrue(emitter.data.stream().noneMatch(item -> item.toString().contains("Elasticsearch unavailable")));
    }
}
