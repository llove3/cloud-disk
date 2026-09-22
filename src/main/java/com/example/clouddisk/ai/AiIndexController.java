package com.example.clouddisk.ai;

import com.example.clouddisk.entity.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/index")
public class AiIndexController {
    private final AiIndexTaskService tasks;
    private final AiIndexTaskMapper taskMapper;
    public AiIndexController(AiIndexTaskService tasks, AiIndexTaskMapper taskMapper) {
        this.tasks = tasks;
        this.taskMapper = taskMapper;
    }
    @GetMapping("/overview")
    public Map<String, Long> overview(HttpSession session) {
        var statuses = taskMapper.findLatestStatusesForUser(userId(session));
        return Map.of("ready", statuses.stream().filter("DONE"::equals).count(),
                "processing", statuses.stream().filter(s -> s.equals("PENDING") || s.equals("RUNNING") || s.equals("RETRY")).count(),
                "failed", statuses.stream().filter("FAILED"::equals).count());
    }
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
