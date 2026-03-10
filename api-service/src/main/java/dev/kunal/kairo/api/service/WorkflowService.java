package dev.kunal.kairo.api.service;

import java.util.UUID;

import dev.kunal.kairo.api.dto.request.WorkflowRequest;
import dev.kunal.kairo.api.dto.response.WorkflowResponse;

public interface WorkflowService {
    WorkflowResponse createWorkflow(WorkflowRequest request);

    WorkflowResponse getWorkflow(UUID id);
}
