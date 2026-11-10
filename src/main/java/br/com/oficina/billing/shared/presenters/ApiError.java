package br.com.oficina.billing.shared.presenters;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        Instant timestamp,
        List<FieldError> fields
) {
    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, Instant.now(), null);
    }

    public static ApiError of(int status, String error, String message, List<FieldError> fields) {
        return new ApiError(status, error, message, Instant.now(), fields);
    }

    public record FieldError(String field, String message) {}
}
