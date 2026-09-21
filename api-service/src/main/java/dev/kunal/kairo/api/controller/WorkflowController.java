package dev.kunal.kairo.api.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.kunal.kairo.api.dto.request.WorkflowRequest;
import dev.kunal.kairo.api.dto.response.WorkflowListResponse;
import dev.kunal.kairo.api.dto.response.WorkflowResponse;
import dev.kunal.kairo.api.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping("/workflows")
    public ResponseEntity<WorkflowResponse> createWorkflow(@Valid @RequestBody WorkflowRequest workflowRequest) {
        WorkflowResponse response = workflowService.createWorkflow(workflowRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<WorkflowResponse> getWorkflow(@PathVariable UUID id) {
        WorkflowResponse response = workflowService.getWorkflow(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/workflows")
    public ResponseEntity<Page<WorkflowListResponse>> getWorkflows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort) {

        List<Sort.Order> orders = new ArrayList<>();
        if (sort[0].contains(",")) {
            for (String sortOrder : sort) {
                String[] _sort = sortOrder.split(",");
                orders.add(new Sort.Order(Sort.Direction.fromString(_sort[1]), _sort[0]));
            }
        } else {
            orders.add(new Sort.Order(Sort.Direction.fromString(sort[1]), sort[0]));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(orders));
        return ResponseEntity.ok(workflowService.getWorkflows(pageable));
    }
}
