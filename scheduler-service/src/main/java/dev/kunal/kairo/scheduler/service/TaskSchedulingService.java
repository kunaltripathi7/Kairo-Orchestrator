package dev.kunal.kairo.scheduler.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSchedulingService {

    private final WorkflowRepository workflowRepository;
    private final TaskRepository taskRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void scheduleFirstTask(UUID workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));

        if (workflow.getStatus() != WorkflowStatus.PENDING) {
            log.warn("Workflow {} is not PENDING, skipping. Current status: {}", workflowId, workflow.getStatus());
            return;
        }

        List<Task> tasks = taskRepository.findByWorkflowIdOrderBySequenceNumberAsc(workflowId);

        if (tasks.isEmpty()) {
            log.warn("Workflow {} has no tasks, marking as COMPLETED", workflowId);
            workflow.setStatus(WorkflowStatus.COMPLETED);
            workflowRepository.save(workflow);
            return;
        }

        Task firstTask = tasks.get(0);
        firstTask.setStatus(TaskStatus.SCHEDULED);
        taskRepository.save(firstTask);

        workflow.setStatus(WorkflowStatus.IN_PROGRESS);
        workflowRepository.save(workflow);

        publishTaskToQueue(firstTask);

        log.info("Scheduled first task {} for workflow {}", firstTask.getId(), workflowId);
    }

    @Transactional
    public void onTaskCompleted(UUID taskId) {
        Task completedTask = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        UUID workflowId = completedTask.getWorkflowId();
        List<Task> allTasks = taskRepository.findByWorkflowIdOrderBySequenceNumberAsc(workflowId);

        Task nextTask = allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .findFirst()
                .orElse(null);

        if (nextTask != null) {
            nextTask.setStatus(TaskStatus.SCHEDULED);
            taskRepository.save(nextTask);
            publishTaskToQueue(nextTask);
            log.info("Scheduled next task {} for workflow {}", nextTask.getId(), workflowId);
        } else {
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

        Workflow workflow = workflowRepository.findById(failedTask.getWorkflowId())
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + failedTask.getWorkflowId()));

        if (failedTask.getAttemptCount() < workflow.getMaxRetries()) {
            failedTask.setAttemptCount(failedTask.getAttemptCount() + 1);
            failedTask.setStatus(TaskStatus.RETRY_PENDING);
            
            // Exponential backoff: 2^attemptCount seconds
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

            workflow.setStatus(WorkflowStatus.FAILED);
            workflowRepository.save(workflow);
            log.info("Workflow {} marked as FAILED due to task {}", workflow.getId(), taskId);
        }
    }

    public void publishTaskToDLQ(Task task, String reason) {
        try {
            String key = task.getWorkflowId().toString();
            String value = objectMapper.writeValueAsString(new TaskMessage(
                    task.getId(),
                    task.getWorkflowId(),
                    task.getHandlerName(),
                    "Reason: " + reason + ", Payload: " + (task.getPayload() != null ? task.getPayload().toString() : null)
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
                    task.getPayload() != null ? task.getPayload().toString() : null));
            kafkaTemplate.send(KafkaTopic.TASK_QUEUE.getTopicName(), key, value);
            log.info("Published task {} to task-queue", task.getId());
        } catch (Exception e) {
            log.error("Failed to publish task {} to Kafka", task.getId(), e);
            throw new RuntimeException("Failed to publish task to queue", e);
        }
    }
}
