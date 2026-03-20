package dev.kunal.kairo.api.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kunal.kairo.api.dto.request.WorkflowRequest;
import dev.kunal.kairo.api.dto.response.WorkflowResponse;
import dev.kunal.kairo.api.mapper.WorkflowMapper;
import dev.kunal.kairo.api.repository.OutboxEventRepository;
import dev.kunal.kairo.api.repository.TaskRepository;
import dev.kunal.kairo.api.repository.WorkflowRepository;
import dev.kunal.kairo.common.dto.WorkflowEvent;
import dev.kunal.kairo.common.entity.OutboxEvent;
import dev.kunal.kairo.common.entity.Task;
import dev.kunal.kairo.common.entity.Workflow;
import dev.kunal.kairo.common.enums.AggregateType;
import dev.kunal.kairo.common.enums.EventType;
import dev.kunal.kairo.common.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

        private final WorkflowRepository workflowRepository;
        private final TaskRepository taskRepository;
        private final WorkflowMapper workflowMapper;
        private final OutboxEventRepository outboxEventRepository;
        private final ObjectMapper objectMapper;

        @Override
        @Transactional
        public WorkflowResponse createWorkflow(WorkflowRequest request) {
                Workflow workflow = Workflow.builder()
                                .name(request.name())
                                .maxRetries(request.maxRetries())
                                .taskTimeoutSeconds(request.taskTimeoutSeconds())
                                .build();

                Workflow savedWorkflow = workflowRepository.save(workflow);

                AtomicInteger sequence = new AtomicInteger(1);
                List<Task> tasks = request.tasks().stream()
                                .map(taskReq -> Task.builder()
                                                .workflowId(savedWorkflow.getId())
                                                .sequenceNumber(sequence.getAndIncrement())
                                                .handlerName(taskReq.handler())
                                                .payload(taskReq.payload())
                                                .build())
                                .toList();

                List<Task> savedTasks = taskRepository.saveAll(tasks);

                WorkflowEvent workflowEvent = new WorkflowEvent(
                                savedWorkflow.getId(),
                                savedWorkflow.getStatus(),
                                EventType.WORKFLOW_CREATED);

                OutboxEvent outboxEvent = OutboxEvent.builder()
                                .aggregateType(AggregateType.WORKFLOW)
                                .aggregateId(savedWorkflow.getId())
                                .type(EventType.WORKFLOW_CREATED)
                                .payload(objectMapper.valueToTree(workflowEvent)) // gives json obj
                                .build();

                outboxEventRepository.save(outboxEvent);

                return workflowMapper.toResponse(savedWorkflow, savedTasks);
        }

        @Override
        @Transactional
        public WorkflowResponse getWorkflow(UUID id) {
                Workflow workflow = workflowRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Workflow not found with the Id:" + id));
                List<Task> tasks = taskRepository.findByWorkflowIdOrderBySequenceNumber(id);
                WorkflowResponse response = workflowMapper.toResponse(workflow, tasks);
                return response;
        }
}
