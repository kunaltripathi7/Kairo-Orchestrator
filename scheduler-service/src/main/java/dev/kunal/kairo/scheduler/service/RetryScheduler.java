package dev.kunal.kairo.scheduler.service;

import java.time.Duration;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RetryScheduler {

    private final StringRedisTemplate redisTemplate;
    private final TaskRepository taskRepository;
    private final TaskSchedulingService taskSchedulingService;
    private final String nodeId;

    private static final String RETRY_QUEUE_KEY = "retry-queue";
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    public RetryScheduler(StringRedisTemplate redisTemplate,
                          TaskRepository taskRepository,
                          TaskSchedulingService taskSchedulingService) {
        this.redisTemplate = redisTemplate;
        this.taskRepository = taskRepository;
        this.taskSchedulingService = taskSchedulingService;
        this.nodeId = "scheduler-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("RetryScheduler initialized with nodeId: {}", nodeId);
    }

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
                boolean claimed = claimAndRetryTask(taskId);
                if (claimed) {
                    redisTemplate.opsForZSet().remove(RETRY_QUEUE_KEY, taskIdStr);
                }
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
                claimAndRetryTask(task.getId());
            } catch (Exception e) {
                log.error("Failed to process retry fallback for task {}", task.getId(), e);
            }
        }
    }

    /**
     * Atomically claims a RETRY_PENDING task and publishes it to the task queue.
     * Returns true if this node successfully claimed the task, false if another node got it first.
     */
    @Transactional
    public boolean claimAndRetryTask(UUID taskId) {
        Instant now = Instant.now();
        Instant leaseUntil = now.plus(LEASE_DURATION);

        int claimed = taskRepository.claimTask(
                taskId,
                TaskStatus.RETRY_PENDING,
                TaskStatus.SCHEDULED,
                nodeId,
                leaseUntil,
                now);

        if (claimed == 0) {
            log.debug("Task {} already claimed by another node, skipping", taskId);
            return false;
        }

        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("Task {} disappeared after claiming, skipping", taskId);
            return false;
        }

        taskSchedulingService.publishTaskToQueue(task);
        log.info("Successfully claimed and re-enqueued task {} for retry (node={})", taskId, nodeId);
        return true;
    }
}

