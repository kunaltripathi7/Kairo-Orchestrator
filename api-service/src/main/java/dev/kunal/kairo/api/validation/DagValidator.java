package dev.kunal.kairo.api.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import dev.kunal.kairo.api.dto.common.TaskRequest;
import dev.kunal.kairo.common.exception.ErrorCode;
import dev.kunal.kairo.common.exception.InvalidRequestException;

public final class DagValidator {

    private DagValidator() {}

    public static void validate(List<TaskRequest> tasks) {
        validateUniqueNames(tasks);
        validateDependencyReferences(tasks);
        detectCycles(tasks);
    }

    public static void validateUniqueNames(List<TaskRequest> tasks) {
        Set<String> seen = new HashSet<>();
        for (TaskRequest task : tasks) {
            if (!seen.add(task.name())) {
                throw new InvalidRequestException("Duplicate task name: " + task.name(), ErrorCode.VALIDATION_FAILED);
            }
        }
    }

    public static void validateDependencyReferences(List<TaskRequest> tasks) {
        Set<String> taskNames = new HashSet<>();
        for (TaskRequest task : tasks) {
            taskNames.add(task.name());
        }

        for (TaskRequest task : tasks) {
            if (task.dependsOn() == null) continue;
            for (String dep : task.dependsOn()) {
                if (!taskNames.contains(dep)) {
                    throw new InvalidRequestException(
                            "Task '" + task.name() + "' depends on unknown task: " + dep, ErrorCode.VALIDATION_FAILED);
                }
                if (dep.equals(task.name())) {
                    throw new InvalidRequestException(
                            "Task '" + task.name() + "' cannot depend on itself", ErrorCode.VALIDATION_FAILED);
                }
            }
        }
    }

    public static void detectCycles(List<TaskRequest> tasks) {
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();

        for (TaskRequest task : tasks) {
            adjacency.put(task.name(), new ArrayList<>());
            indegree.put(task.name(), 0);
        }

        for (TaskRequest task : tasks) {
            if (task.dependsOn() == null) continue;
            for (String dep : task.dependsOn()) {
                adjacency.get(dep).add(task.name());
                indegree.merge(task.name(), 1, Integer::sum);
            }
        }

        // Kahn's algorithm
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        int processed = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            processed++;
            for (String neighbor : adjacency.get(current)) {
                int newDegree = indegree.get(neighbor) - 1;
                indegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (processed != tasks.size()) {
            throw new InvalidRequestException("Workflow contains a cycle in task dependencies", ErrorCode.VALIDATION_FAILED);
        }
    }

    public static List<String> topologicalOrder(List<TaskRequest> tasks) {
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();

        for (TaskRequest task : tasks) {
            adjacency.put(task.name(), new ArrayList<>());
            indegree.put(task.name(), 0);
        }

        for (TaskRequest task : tasks) {
            if (task.dependsOn() == null) continue;
            for (String dep : task.dependsOn()) {
                adjacency.get(dep).add(task.name());
                indegree.merge(task.name(), 1, Integer::sum);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            order.add(current);
            for (String neighbor : adjacency.get(current)) {
                int newDegree = indegree.get(neighbor) - 1;
                indegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }
        return order;
    }
}
