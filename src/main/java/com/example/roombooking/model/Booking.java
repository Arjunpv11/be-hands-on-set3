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

    public Booking cancel() {
        return new Booking(id, roomName, title, organiserEmail, attendeeEmails,
                startTime, endTime, createdAt, true);
    }
}
