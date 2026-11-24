package com.example.roombooking.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record FreeSlotsRequest(
        @NotNull(message = "Date is required")
        LocalDate date,
        @Min(value = 1, message = "Minimum duration must be at least 1 minute")
        @Max(value = 540, message = "Minimum duration cannot exceed 540 minutes")
        int minimumDurationMinutes,
        @NotNull(message = "Attendee emails are required")
        List<@NotBlank(message = "Attendee email must not be blank")
             @Email(message = "Attendee email must be valid") String> attendeeEmails
) {
}
