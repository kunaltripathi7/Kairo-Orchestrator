package dev.kunal.kairo.api.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kunal.kairo.api.dto.request.WorkflowRequest;
import dev.kunal.kairo.api.dto.response.WorkflowResponse;
import dev.kunal.kairo.api.mapper.WorkflowMapper;
import dev.kunal.kairo.api.repository.OutboxEventRepository;
import dev.kunal.kairo.api.repository.TaskRepository;
import dev.kunal.kairo.api.repository.WorkflowRepository;
import dev.kunal.kairo.api.validation.DagValidator;
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
                // Validate DAG structure
                DagValidator.validate(request.tasks());

                Workflow workflow = Workflow.builder()
                                .name(request.name())
                                .maxRetries(request.maxRetries())
                                .taskTimeoutSeconds(request.taskTimeoutSeconds())
                                .build();

                Workflow savedWorkflow = workflowRepository.save(workflow);

                // Compute topological order for display sequencing
                List<String> topoOrder = DagValidator.topologicalOrder(request.tasks());
                Map<String, Integer> sequenceMap = new HashMap<>();
                for (int i = 0; i < topoOrder.size(); i++) {
                        sequenceMap.put(topoOrder.get(i), i + 1);
                }

                // First pass: save tasks without dependsOn resolved (generates UUIDs)
                Map<String, UUID> nameToId = new HashMap<>();
                List<Task> savedTasks = new ArrayList<>();

                for (var taskReq : request.tasks()) {
                        Task task = Task.builder()
                                        .workflowId(savedWorkflow.getId())
                                        .name(taskReq.name())
                                        .sequenceNumber(sequenceMap.get(taskReq.name()))
                                        .handlerName(taskReq.handler())
                                        .payload(taskReq.payload())
                                        .dependsOn(new ArrayList<>())
                                        .build();

                        Task saved = taskRepository.save(task);
                        savedTasks.add(saved);
                        nameToId.put(taskReq.name(), saved.getId());
                }

                // Second pass: resolve dependsOn names → UUIDs
                for (int i = 0; i < request.tasks().size(); i++) {
                        var taskReq = request.tasks().get(i);
                        List<String> deps = taskReq.dependsOn();
                        if (deps != null && !deps.isEmpty()) {
                                List<UUID> resolvedDeps = deps.stream()
                                                .map(nameToId::get)
                                                .toList();
                                savedTasks.get(i).setDependsOn(resolvedDeps);
                                taskRepository.save(savedTasks.get(i));
                        }
                }

                // String correlationId = MDC.get("correlationId"); // Handled automatically by Micrometer Tracing

                WorkflowEvent workflowEvent = new WorkflowEvent(
                                savedWorkflow.getId(),
                                savedWorkflow.getStatus(),
                                EventType.WORKFLOW_CREATED
                                // correlationId
                );

                OutboxEvent outboxEvent = OutboxEvent.builder()
                                .aggregateType(AggregateType.WORKFLOW)
                                .aggregateId(savedWorkflow.getId())
                                .type(EventType.WORKFLOW_CREATED)
                                .payload(objectMapper.valueToTree(workflowEvent))
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
