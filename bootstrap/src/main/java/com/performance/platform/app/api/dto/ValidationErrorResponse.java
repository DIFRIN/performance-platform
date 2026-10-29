package com.performance.platform.app.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Structured validation error response DTO.
 * JSON shape is backward-compatible with the existing error format:
 * {@code {error, message, details:[{field, message, path}]}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationErrorResponse(String error, String message, List<FieldError> details) {

    /**
     * A single field-level validation error.
     * {@code path} is nullable and omitted from JSON when null.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldError(String field, String message, String path) {
    }
}
