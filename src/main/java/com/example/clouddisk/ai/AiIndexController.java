package com.example.clouddisk.ai;

import com.example.clouddisk.entity.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai/index")
public class AiIndexController {
    private final AiIndexTaskService tasks;
    public AiIndexController(AiIndexTaskService tasks) { this.tasks = tasks; }
    @GetMapping("/{fileId}")
    public AiIndexTask status(@PathVariable Long fileId, HttpSession session) {
        return tasks.status(fileId, userId(session));
    }
    @PostMapping("/{fileId}/retry")
    public AiIndexTask retry(@PathVariable Long fileId, HttpSession session) {
        return tasks.retry(fileId, userId(session));
    }
    private Long userId(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return user.getId();
    }
}
