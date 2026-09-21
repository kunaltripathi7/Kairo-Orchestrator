package dev.kunal.kairo.api.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.kunal.kairo.api.dto.request.WorkflowRequest;
import dev.kunal.kairo.api.dto.response.WorkflowListResponse;
import dev.kunal.kairo.api.dto.response.WorkflowResponse;

public interface WorkflowService {
    WorkflowResponse createWorkflow(WorkflowRequest request);

    WorkflowResponse getWorkflow(UUID id);

    Page<WorkflowListResponse> getWorkflows(Pageable pageable);
}
