package com.assine.content.adapters.inbound.rest.common;

import java.time.Instant;

public record ErrorResponse(String error, String message, int status, Instant timestamp) {
    public static ErrorResponse of(String error, String message, int status) {
        return new ErrorResponse(error, message, status, Instant.now());
    }
}
