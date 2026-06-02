package com.restaurantplanner.common.api;

import java.util.Map;

public class ConflictException extends RuntimeException {

    private final Map<String, Object> details;

    public ConflictException(String message) {
        super(message);
        this.details = Map.of();
    }

    public ConflictException(String message, Map<String, Object> details) {
        super(message);
        this.details = details == null ? Map.of() : details;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
