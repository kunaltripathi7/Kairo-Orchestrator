package dev.kunal.kairo.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum KafkaTopic {
    WORKFLOW_EVENTS("workflow-events"),
    TASK_QUEUE("task-queue"),
    TASK_RESULTS("task-results"),
    DEAD_LETTER_QUEUE("dead-letter-queue");

    private final String topicName;
}
