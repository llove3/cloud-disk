package com.example.clouddisk.ai;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.mapper.FileMapper;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AiIndexTaskService {
    private final AiIndexTaskMapper tasks;
    private final FileMapper files;
    private final RabbitTemplate rabbit;

    public AiIndexTaskService(AiIndexTaskMapper tasks, FileMapper files, RabbitTemplate rabbit) {
        this.tasks = tasks;
        this.files = files;
        this.rabbit = rabbit;
    }

    public void enqueue(Long fileId, Long userId, String operation) {
        files.incrementIndexGeneration(fileId);
        FileInfo file = files.findById(fileId);
        AiIndexTask task = new AiIndexTask();
        task.setFileId(fileId);
        task.setUserId(userId);
        task.setGeneration(file.getIndexGeneration());
        task.setOperation(operation);
        tasks.insert(task);
        Runnable publish = () -> publish(task.getId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else publish.run();
    }

    private void publish(Long id) {
        try {
            rabbit.convertAndSend(AiRabbitConfig.EXCHANGE, "index", String.valueOf(id),
                    message -> { message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT); return message; });
        } catch (RuntimeException ignored) {
            // The scheduled scan republishes durable pending tasks when RabbitMQ returns.
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void republishPending() {
        tasks.recoverStale();
        for (AiIndexTask task : tasks.findPending()) publish(task.getId());
    }

    public AiIndexTask status(Long fileId, Long userId) {
        FileInfo file = files.findByIdAndUserIdIncludeDeleted(fileId, userId);
        if (file == null) throw new IllegalArgumentException("文件不存在或无权访问");
        return tasks.findLatestForFile(fileId);
    }

    public AiIndexTask retry(Long fileId, Long userId) {
        AiIndexTask task = status(fileId, userId);
        if (task == null || !"FAILED".equals(task.getStatus())) throw new IllegalArgumentException("没有可重试的失败任务");
        tasks.retry(task.getId());
        publish(task.getId());
        return tasks.findById(task.getId());
    }
}
