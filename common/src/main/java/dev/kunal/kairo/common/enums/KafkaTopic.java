package dev.kunal.kairo.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum KafkaTopic {
    WORKFLOW_EVENTS("workflow-events");

    private final String topicName;

}
