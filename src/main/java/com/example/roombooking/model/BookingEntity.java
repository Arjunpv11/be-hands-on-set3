package com.example.roombooking.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_booking_idempotency_key", columnNames = "idempotency_key")
})
public class BookingEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "room_name", nullable = false)
    private RoomEntity room;

    @Column(nullable = false)
    private String title;

    @Column(name = "organiser_email", nullable = false)
    private String organiserEmail;

    @ElementCollection
    @CollectionTable(name = "booking_attendees", joinColumns = @JoinColumn(name = "booking_id"))
    @OrderColumn(name = "attendee_order")
    @Column(name = "email", nullable = false)
    private List<String> attendeeEmails = new ArrayList<>();

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean cancelled;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
    private String requestFingerprint;

    protected BookingEntity() {
    }

    public BookingEntity(
            RoomEntity room,
            String title,
            String organiserEmail,
            List<String> attendeeEmails,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime createdAt,
            String idempotencyKey,
            String requestFingerprint
    ) {
        this.room = room;
        this.title = title;
        this.organiserEmail = organiserEmail;
        this.attendeeEmails = new ArrayList<>(attendeeEmails);
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdAt = createdAt;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
    }

    public UUID getId() {
        return id;
    }

    public RoomEntity getRoom() {
        return room;
    }

    public String getTitle() {
        return title;
    }

    public String getOrganiserEmail() {
        return organiserEmail;
    }

    public List<String> getAttendeeEmails() {
        return List.copyOf(attendeeEmails);
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void cancel() {
        cancelled = true;
    }
}
