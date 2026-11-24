package com.example.roombooking.model;

public record Room(String name, int capacity, boolean active) {
    public static Room from(RoomEntity entity) {
        return new Room(entity.getName(), entity.getCapacity(), entity.isActive());
    }
}
