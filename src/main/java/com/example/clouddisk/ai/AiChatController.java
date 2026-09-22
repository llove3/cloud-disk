package com.example.clouddisk.ai;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.entity.Share;
import com.example.clouddisk.entity.User;
import com.example.clouddisk.mapper.ShareMapper;
import com.example.clouddisk.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
public class AiChatController {
    public record ChatRequest(String question, String password) {}
    private final AiChatService chat;
    private final AiSearchService search;
    private final ShareService shares;
    private final ShareMapper shareMapper;

    public AiChatController(AiChatService chat, AiSearchService search,
                            ShareService shares, ShareMapper shareMapper) {
        this.chat = chat;
        this.search = search;
        this.shares = shares;
        this.shareMapper = shareMapper;
    }

    @GetMapping("/api/ai/search")
    public List<AiSearchService.Source> search(@RequestParam String q, HttpSession session) {
        return search.search(userId(session), q, null, 20);
    }

    @PostMapping(value = "/api/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request, HttpSession session) {
        return chat.chat(userId(session), request.question(), null, true);
    }

    @PostMapping(value = "/s/{code}/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter shareChat(@PathVariable String code,
                                @RequestBody ChatRequest request, HttpServletRequest http) {
        Share share = shareMapper.findByCode(code);
        if (share == null || Boolean.TRUE.equals(share.getIsPackage()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "仅单文件分享支持问答");
        FileInfo file = shares.getFileByShareCode(code, request.password(), http.getRemoteAddr(), http.getHeader("User-Agent"));
        return chat.chat(file.getUserId(), request.question(), file.getId(), false);
    }

    private Long userId(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return user.getId();
    }
}
