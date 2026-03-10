package dev.kunal.kairo.common.dto;

import java.util.Map;

public record ValidationErrorResponse(
        String timestamp,
        String path,
        String errorCode,
        String message,
        Map<String, String> fieldErrors) {
}
