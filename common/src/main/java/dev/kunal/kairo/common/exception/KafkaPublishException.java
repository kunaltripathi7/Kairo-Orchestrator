package dev.kunal.kairo.common.exception;

import lombok.Getter;

@Getter
public class KafkaPublishException extends RuntimeException {
    private final String topic;
    private final String key;

    public KafkaPublishException(String message, String topic, String key, Throwable cause) {
        super(message, cause);
        this.topic = topic;
        this.key = key;
    }
}
