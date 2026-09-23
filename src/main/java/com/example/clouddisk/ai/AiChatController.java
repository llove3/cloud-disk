package com.example.clouddisk.ai;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.User;
import com.example.clouddisk.mapper.FileMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
public class AiChatController {
    public record ChatRequest(String question, Long fileId, List<Long> fileIds) {}
    private final AiChatService chat;
    private final AiSearchService search;
    private final FileMapper fileMapper;

    public AiChatController(AiChatService chat, AiSearchService search, FileMapper fileMapper) {
        this.chat = chat;
        this.search = search;
        this.fileMapper = fileMapper;
    }

    @GetMapping("/api/ai/search")
    public List<AiSearchService.Source> search(@RequestParam String q, HttpSession session) {
        return search.search(userId(session), q, List.of(), 20);
    }

    @PostMapping(value = "/api/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request, HttpSession session) {
        Long owner = userId(session);
        if (request.fileId() != null && request.fileIds() != null && !request.fileIds().isEmpty())
            throw new IllegalArgumentException("请只使用一种文件选择方式");
        List<Long> ids = request.fileIds() != null && !request.fileIds().isEmpty()
                ? request.fileIds().stream().distinct().toList()
                : request.fileId() == null ? List.of() : List.of(request.fileId());
        if (ids.size() > 20 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("最多选择 20 个有效文件");
        for (Long id : ids) {
            FileInfo file = fileMapper.findByIdAndUserId(id, owner);
            if (file == null || file.isFolder())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在或不可问答");
        }
        return chat.chat(owner, request.question(), ids, true);
    }

    private Long userId(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return user.getId();
    }
}
