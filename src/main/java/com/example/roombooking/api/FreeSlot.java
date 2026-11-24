package com.example.roombooking.api;

import com.example.roombooking.model.Room;

import java.time.LocalDateTime;
import java.util.List;

public record FreeSlot(LocalDateTime startTime, LocalDateTime endTime, List<Room> availableRooms) {
    public FreeSlot {
        availableRooms = List.copyOf(availableRooms);
    }
}
