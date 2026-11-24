package com.example.roombooking.error;

import java.util.List;
import java.util.Map;

public class BookingValidationException extends RuntimeException {
    private final Map<String, List<String>> fieldErrors;

    public BookingValidationException(Map<String, List<String>> fieldErrors) {
        super("Request validation failed");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, List<String>> fieldErrors() {
        return fieldErrors;
    }
}
