package dev.kunal.kairo.scheduler.service;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSchedulingService {

    private final WorkflowRepository workflowRepository;
    private final TaskRepository taskRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

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

        workflow.setStatus(WorkflowStatus.FAILED);
        workflowRepository.save(workflow);
        log.info("Workflow {} marked as FAILED due to task {}", workflow.getId(), taskId);
    }

    private void publishTaskToQueue(Task task) {
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
