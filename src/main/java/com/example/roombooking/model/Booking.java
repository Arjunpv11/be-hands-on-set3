package com.example.roombooking.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record Booking(
        UUID id,
        String roomName,
        String title,
        String organiserEmail,
        List<String> attendeeEmails,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt,
        boolean cancelled
) {
    public Booking {
        attendeeEmails = List.copyOf(attendeeEmails);
    }

    public static Booking from(BookingEntity entity) {
        return new Booking(
                entity.getId(),
                entity.getRoom().getName(),
                entity.getTitle(),
                entity.getOrganiserEmail(),
                entity.getAttendeeEmails(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getCreatedAt(),
                entity.isCancelled()
        );
    }
}
