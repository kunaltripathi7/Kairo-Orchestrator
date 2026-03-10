package dev.kunal.kairo.common.dto;

public record ErrorResponse(
        String timestamp,
        String path,
        String errorCode,
        String message) {
}
