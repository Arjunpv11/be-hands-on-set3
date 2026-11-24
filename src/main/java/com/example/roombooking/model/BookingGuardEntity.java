package com.example.roombooking.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "booking_guard")
public class BookingGuardEntity {
    @Id
    private Integer id;

    protected BookingGuardEntity() {
    }
}
