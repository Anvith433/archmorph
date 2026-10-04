package com.anvith.archmorph.common.response;

import com.anvith.archmorph.common.web.RequestIdFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.MDC;

import java.time.Instant;

/**
 * Envelope used by every API response.
 *
 * <pre>
 * { "success": true, "message": "...", "data": {...}, "timestamp": "...", "requestId": "..." }
 * { "success": false, "message": "...", "errorCode": "INVALID_ARCHIVE", "hint": "...", ... }
 * </pre>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;

    private String message;

    private T data;

    private String errorCode;

    /** What the user can do to resolve an error. */
    private String hint;

    private Instant timestamp;

    private String requestId;

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .requestId(MDC.get(RequestIdFilter.MDC_KEY))
                .build();
    }

    public static <T> ApiResponse<T> error(String errorCode, String message, String hint) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .hint(hint)
                .timestamp(Instant.now())
                .requestId(MDC.get(RequestIdFilter.MDC_KEY))
                .build();
    }
}
