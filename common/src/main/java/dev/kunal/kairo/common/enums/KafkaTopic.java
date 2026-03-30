package dev.kunal.kairo.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum KafkaTopic {
    WORKFLOW_EVENTS(Topics.WORKFLOW_EVENTS),
    TASK_QUEUE(Topics.TASK_QUEUE),
    TASK_RESULTS(Topics.TASK_RESULTS),
    DEAD_LETTER_QUEUE(Topics.DEAD_LETTER_QUEUE);

    private final String topicName;

    public static class Topics {
        public static final String WORKFLOW_EVENTS = "workflow-events";
        public static final String TASK_QUEUE = "task-queue";
        public static final String TASK_RESULTS = "task-results";
        public static final String DEAD_LETTER_QUEUE = "dead-letter-queue";
    }
}
