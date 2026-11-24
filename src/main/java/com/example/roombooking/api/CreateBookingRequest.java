package com.example.roombooking.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record CreateBookingRequest(
        @NotBlank(message = "Room name is required")
        String roomName,
        @NotBlank(message = "Title is required")
        String title,
        @NotBlank(message = "Organiser email is required")
        @Email(message = "Organiser email must be valid")
        String organiserEmail,
        @NotNull(message = "Attendee emails are required")
        List<@NotBlank(message = "Attendee email must not be blank")
             @Email(message = "Attendee email must be valid") String> attendeeEmails,
        @NotNull(message = "Start time is required")
        LocalDateTime startTime,
        @NotNull(message = "End time is required")
        LocalDateTime endTime
) {
}
