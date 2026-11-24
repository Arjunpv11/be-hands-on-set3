package com.example.roombooking.service;

import com.example.roombooking.api.CreateBookingRequest;
import com.example.roombooking.error.ApiException;
import com.example.roombooking.model.Booking;
import com.example.roombooking.model.Room;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BookingService {
    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);
    private static final Duration MAX_DURATION = Duration.ofHours(4);
    private final Clock clock;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<UUID, Booking> bookings = new ConcurrentHashMap<>();

    public BookingService(Clock clock) {
        this.clock = clock;
        seedRooms();
    }

    public List<Room> listRooms() {
        return rooms.values().stream()
                .sorted(Comparator.comparing(Room::name))
                .toList();
    }

    public synchronized Booking createBooking(CreateBookingRequest request) {
        validateTimes(request);
        Room room = rooms.get(request.roomName());
        if (room == null || !room.active()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Room not found: " + request.roomName());
        }

        List<String> attendees = uniqueAttendees(request);
        if (attendees.size() > room.capacity()) {
            throw new BookingValidationException(Map.of(
                    "attendeeEmails",
                    List.of("Attendee count exceeds room capacity of " + room.capacity())
            ));
        }

        for (Booking existing : bookings.values()) {
            if (!existing.cancelled()
                    && existing.roomName().equals(room.name())
                    && request.startTime().isBefore(existing.endTime())
                    && existing.startTime().isBefore(request.endTime())) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Booking overlaps with booking " + existing.id());
            }
        }

        Booking booking = new Booking(
                UUID.randomUUID(),
                room.name(),
                request.title().trim(),
                request.organiserEmail().trim(),
                attendees,
                request.startTime(),
                request.endTime(),
                LocalDateTime.now(clock),
                false
        );
        bookings.put(booking.id(), booking);
        return booking;
    }

    public Booking getBooking(UUID id) {
        Booking booking = bookings.get(id);
        if (booking == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Booking not found: " + id);
        }
        return booking;
    }

    public List<Booking> listBookings(String roomName, LocalDate date) {
        Room room = rooms.get(roomName);
        if (room == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Room not found: " + roomName);
        }
        return bookings.values().stream()
                .filter(booking -> !booking.cancelled())
                .filter(booking -> booking.roomName().equals(room.name()))
                .filter(booking -> booking.startTime().toLocalDate().equals(date))
                .sorted(Comparator.comparing(Booking::startTime))
                .toList();
    }

    public synchronized void cancelBooking(UUID id) {
        Booking booking = bookings.get(id);
        if (booking == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Booking not found: " + id);
        }
        bookings.put(id, booking.cancel());
    }

    private void validateTimes(CreateBookingRequest request) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        LocalDateTime start = request.startTime();
        LocalDateTime end = request.endTime();

        if (!start.isBefore(end)) {
            addError(errors, "endTime", "End time must be after start time");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            addError(errors, "endTime", "Booking must start and end on the same day");
        }
        if (start.toLocalTime().isBefore(WORK_START) || start.toLocalTime().isAfter(WORK_END)) {
            addError(errors, "startTime", "Start time must be within working hours (09:00-18:00)");
        }
        if (end.toLocalTime().isBefore(WORK_START) || end.toLocalTime().isAfter(WORK_END)) {
            addError(errors, "endTime", "Booking must be within working hours (09:00-18:00)");
        }
        if (!isOnStep(start.toLocalTime())) {
            addError(errors, "startTime", "Start time must use 15-minute steps");
        }
        if (!isOnStep(end.toLocalTime())) {
            addError(errors, "endTime", "End time must use 15-minute steps");
        }
        if (Duration.between(start, end).compareTo(MAX_DURATION) > 0) {
            addError(errors, "endTime", "Booking cannot last longer than 4 hours");
        }
        if (start.isBefore(LocalDateTime.now(clock))) {
            addError(errors, "startTime", "Booking cannot be in the past");
        }
        if (!errors.isEmpty()) {
            throw new BookingValidationException(errors);
        }
    }

    private boolean isOnStep(LocalTime time) {
        return time.getMinute() % 15 == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }

    private List<String> uniqueAttendees(CreateBookingRequest request) {
        Map<String, String> attendees = new LinkedHashMap<>();
        addAttendee(attendees, request.organiserEmail());
        request.attendeeEmails().forEach(email -> addAttendee(attendees, email));
        return new ArrayList<>(attendees.values());
    }

    private void addAttendee(Map<String, String> attendees, String email) {
        String trimmed = email.trim();
        attendees.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }

    private void addError(Map<String, List<String>> errors, String field, String message) {
        errors.computeIfAbsent(field, ignored -> new ArrayList<>()).add(message);
    }

    private void seedRooms() {
        List.of(
                new Room("Birch", 6, true),
                new Room("Cedar", 10, true),
                new Room("Maple", 8, true),
                new Room("Oak", 14, true),
                new Room("Pine", 4, true)
        ).forEach(room -> rooms.put(room.name(), room));
    }
}
