package dev.kunal.kairo.scheduler.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.kunal.kairo.common.dto.TaskMessage;
import dev.kunal.kairo.common.entity.Task;
import dev.kunal.kairo.common.entity.Workflow;
import dev.kunal.kairo.common.enums.KafkaTopic;
import dev.kunal.kairo.common.enums.TaskStatus;
import dev.kunal.kairo.common.enums.WorkflowStatus;
import dev.kunal.kairo.common.exception.ResourceNotFoundException;
import dev.kunal.kairo.scheduler.repository.TaskRepository;
import dev.kunal.kairo.scheduler.repository.WorkflowRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Service
public class TaskSchedulingService {

    private final WorkflowRepository workflowRepository;
    private final TaskRepository taskRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final String nodeId;

    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    public TaskSchedulingService(WorkflowRepository workflowRepository,
                                 TaskRepository taskRepository,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 ObjectMapper objectMapper,
                                 StringRedisTemplate redisTemplate) {
        this.workflowRepository = workflowRepository;
        this.taskRepository = taskRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.nodeId = "scheduler-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("TaskSchedulingService initialized with nodeId: {}", nodeId);
    }

    @Transactional
    public void scheduleFirstTask(UUID workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));

        if (workflow.getStatus() != WorkflowStatus.PENDING) {
            log.warn("Workflow {} is not PENDING, skipping. Current status: {}", workflowId, workflow.getStatus());
            return;
        }

        List<Task> allTasks = taskRepository.findByWorkflowId(workflowId);

        if (allTasks.isEmpty()) {
            log.warn("Workflow {} has no tasks, marking as COMPLETED", workflowId);
            workflow.setStatus(WorkflowStatus.COMPLETED);
            workflowRepository.save(workflow);
            return;
        }

        workflow.setStatus(WorkflowStatus.IN_PROGRESS);
        workflowRepository.save(workflow);

        // Schedule all root tasks (no dependencies)
        List<Task> rootTasks = allTasks.stream()
                .filter(t -> t.getDependsOn() == null || t.getDependsOn().isEmpty())
                .toList();

        for (Task rootTask : rootTasks) {
            claimAndPublish(rootTask);
        }

        log.info("Scheduled {} root task(s) for workflow {} (node={})", rootTasks.size(), workflowId, nodeId);
    }

    @Transactional
    public void onTaskCompleted(UUID taskId) {
        Task completedTask = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        if (completedTask.getStatus() != TaskStatus.SCHEDULED) {
            log.warn("Task {} already processed (status={}), skipping duplicate result", taskId, completedTask.getStatus());
            return;
        }

        completedTask.setStatus(TaskStatus.COMPLETED);
        taskRepository.releaseLock(taskId);

        UUID workflowId = completedTask.getWorkflowId();
        List<Task> allTasks = taskRepository.findByWorkflowId(workflowId);

        // Find completed task IDs for dependency checking
        Set<UUID> completedIds = allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .map(Task::getId)
                .collect(Collectors.toSet());

        // Find PENDING tasks whose dependencies are now all satisfied
        List<Task> readyTasks = allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .filter(t -> t.getDependsOn() != null && !t.getDependsOn().isEmpty())
                .filter(t -> completedIds.containsAll(t.getDependsOn()))
                .toList();

        for (Task readyTask : readyTasks) {
            claimAndPublish(readyTask);
        }

        if (!readyTasks.isEmpty()) {
            log.info("Scheduled {} newly unblocked task(s) for workflow {} (node={})",
                    readyTasks.size(), workflowId, nodeId);
        }

        // Check if workflow is done: no PENDING, SCHEDULED, RUNNING, or RETRY_PENDING tasks remain
        boolean workflowDone = allTasks.stream()
                .noneMatch(t -> t.getStatus() == TaskStatus.PENDING
                        || t.getStatus() == TaskStatus.SCHEDULED
                        || t.getStatus() == TaskStatus.RUNNING
                        || t.getStatus() == TaskStatus.RETRY_PENDING);

        if (workflowDone) {
            Workflow workflow = workflowRepository.findById(workflowId)
                    .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));
            workflow.setStatus(WorkflowStatus.COMPLETED);
            workflowRepository.save(workflow);
            log.info("All tasks completed for workflow {}", workflowId);
        }
    }

    @Transactional
    public void onTaskFailed(UUID taskId) {
        Task failedTask = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        if (failedTask.getStatus() != TaskStatus.SCHEDULED) {
            log.warn("Task {} already processed (status={}), skipping duplicate result", taskId, failedTask.getStatus());
            return;
        }

        taskRepository.releaseLock(taskId);

        Workflow workflow = workflowRepository.findById(failedTask.getWorkflowId())
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + failedTask.getWorkflowId()));

        if (failedTask.getAttemptCount() < workflow.getMaxRetries()) {
            failedTask.setAttemptCount(failedTask.getAttemptCount() + 1);
            failedTask.setStatus(TaskStatus.RETRY_PENDING);

            long delaySeconds = (long) Math.pow(2, failedTask.getAttemptCount());
            Instant nextRetry = Instant.now().plusSeconds(delaySeconds);
            failedTask.setNextRetryTime(nextRetry);

            taskRepository.save(failedTask);

            try {
                redisTemplate.opsForZSet().add("retry-queue", failedTask.getId().toString(), nextRetry.getEpochSecond());
                log.info("Scheduled task {} for retry at {}", taskId, nextRetry);
            } catch (Exception e) {
                log.warn("Failed to add task {} to redis retry queue, will rely on postgres fallback", taskId, e);
            }
        } else {
            failedTask.setStatus(TaskStatus.FAILED);
            taskRepository.save(failedTask);

            publishTaskToDLQ(failedTask, "Max retries exhausted");

            // Skip all downstream dependents
            skipDownstreamTasks(failedTask, taskRepository.findByWorkflowId(workflow.getId()));

            workflow.setStatus(WorkflowStatus.FAILED);
            workflowRepository.save(workflow);
            log.info("Workflow {} marked as FAILED due to task {}", workflow.getId(), taskId);
        }
    }

    // making downstreams task invalid if parent task fails.
    private void skipDownstreamTasks(Task failedTask, List<Task> allTasks) {
        Queue<UUID> toSkip = new LinkedList<>();
        toSkip.add(failedTask.getId());
        List<Task> tasksToSave = new LinkedList<>();

        while (!toSkip.isEmpty()) {
            UUID currentId = toSkip.poll();
            for (Task task : allTasks) {
                if (task.getStatus() == TaskStatus.PENDING
                        && task.getDependsOn() != null
                        && task.getDependsOn().contains(currentId)) {
                    task.setStatus(TaskStatus.SKIPPED);
                    tasksToSave.add(task);
                    toSkip.add(task.getId());
                    log.info("Skipped downstream task {} (depends on failed task {})", task.getId(), failedTask.getId());
                }
            }
        }

        if (!tasksToSave.isEmpty()) {
            taskRepository.saveAll(tasksToSave);
        }
    }

    private void claimAndPublish(Task task) {
        Instant now = Instant.now();
        int claimed = taskRepository.claimTask(
                task.getId(),
                TaskStatus.PENDING,
                TaskStatus.SCHEDULED,
                nodeId,
                now.plus(LEASE_DURATION),
                now);

        if (claimed == 0) {
            log.warn("Task {} already claimed by another node, skipping", task.getId());
            return;
        }

        publishTaskToQueue(task);
    }

    public void publishTaskToDLQ(Task task, String reason) {
        try {
            String key = task.getWorkflowId().toString();
            String value = objectMapper.writeValueAsString(new TaskMessage(
                    task.getId(),
                    task.getWorkflowId(),
                    task.getHandlerName(),
                    "Reason: " + reason + ", Payload: " + (task.getPayload() != null ? task.getPayload().toString() : null)
                    // MDC.get("correlationId") // Handled automatically by Micrometer Tracing
            ));
            kafkaTemplate.send(KafkaTopic.DEAD_LETTER_QUEUE.getTopicName(), key, value);
            log.info("Published task {} to dead-letter-queue", task.getId());
        } catch (Exception e) {
            log.error("Failed to publish task {} to DLQ", task.getId(), e);
        }
    }

    public void publishTaskToQueue(Task task) {
        try {
            String key = task.getWorkflowId().toString();
            String value = objectMapper.writeValueAsString(new TaskMessage(
                    task.getId(),
                    task.getWorkflowId(),
                    task.getHandlerName(),
                    task.getPayload() != null ? task.getPayload().toString() : null
                    // MDC.get("correlationId") // Handled automatically by Micrometer Tracing
            ));
            kafkaTemplate.send(KafkaTopic.TASK_QUEUE.getTopicName(), key, value);
            log.info("Published task {} to task-queue", task.getId());
        } catch (Exception e) {
            log.error("Failed to publish task {} to Kafka", task.getId(), e);
            throw new RuntimeException("Failed to publish task to queue", e);
        }
    }
}
