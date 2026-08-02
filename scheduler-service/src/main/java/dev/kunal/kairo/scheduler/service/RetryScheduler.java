package dev.kunal.kairo.scheduler.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.kunal.kairo.common.entity.Task;
import dev.kunal.kairo.common.enums.TaskStatus;
import dev.kunal.kairo.scheduler.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryScheduler {

    private final StringRedisTemplate redisTemplate;
    private final TaskRepository taskRepository;
    private final TaskSchedulingService taskSchedulingService;

    private static final String RETRY_QUEUE_KEY = "retry-queue";

    @Scheduled(fixedDelay = 1000)
    public void pollRedisForRetries() {
        long now = Instant.now().getEpochSecond();
        
        Set<String> taskIds = redisTemplate.opsForZSet().rangeByScore(RETRY_QUEUE_KEY, 0, now);
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }

        for (String taskIdStr : taskIds) {
            try {
                UUID taskId = UUID.fromString(taskIdStr);
                processRetry(taskId);
                redisTemplate.opsForZSet().remove(RETRY_QUEUE_KEY, taskIdStr);
            } catch (Exception e) {
                log.error("Failed to process retry for task {}", taskIdStr, e);
            }
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void pollPostgresForRetriesFallback() {
        List<Task> pendingRetries = taskRepository.findByStatusAndNextRetryTimeLessThanEqual(
                TaskStatus.RETRY_PENDING, Instant.now());
                
        for (Task task : pendingRetries) {
            try {
                processRetry(task.getId());
            } catch (Exception e) {
                log.error("Failed to process retry fallback for task {}", task.getId(), e);
            }
        }
    }

    @Transactional
    public void processRetry(UUID taskId) {
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != TaskStatus.RETRY_PENDING) {
            return; // Task already processed or not found
        }

        task.setStatus(TaskStatus.SCHEDULED);
        taskRepository.save(task);
        taskSchedulingService.publishTaskToQueue(task);
        log.info("Successfully re-enqueued task {} for retry", taskId);
    }
}
