package com.example.roombooking.service;

import com.example.roombooking.api.CreateBookingRequest;
import com.example.roombooking.api.FreeSlot;
import com.example.roombooking.api.FreeSlotsRequest;
import com.example.roombooking.error.ApiException;
import com.example.roombooking.error.BookingValidationException;
import com.example.roombooking.model.Booking;
import com.example.roombooking.model.BookingEntity;
import com.example.roombooking.model.Room;
import com.example.roombooking.model.RoomEntity;
import com.example.roombooking.repository.BookingGuardRepository;
import com.example.roombooking.repository.BookingRepository;
import com.example.roombooking.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {
    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);
    private static final Duration MAX_DURATION = Duration.ofHours(4);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;

    private final Clock clock;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final BookingGuardRepository guardRepository;

    public BookingService(
            Clock clock,
            RoomRepository roomRepository,
            BookingRepository bookingRepository,
            BookingGuardRepository guardRepository
    ) {
        this.clock = clock;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.guardRepository = guardRepository;
    }

    @Transactional(readOnly = true)
    public List<Room> listRooms() {
        return roomRepository.findAllByActiveTrueOrderByName().stream()
                .map(Room::from)
                .toList();
    }

    @Transactional
    public Booking createBooking(CreateBookingRequest request, String rawIdempotencyKey) {
        String idempotencyKey = validateIdempotencyKey(rawIdempotencyKey);
        String fingerprint = fingerprint(request);

        guardRepository.lockForBookingCreation();
        var existingRequest = bookingRepository.findByIdempotencyKey(idempotencyKey);
        if (existingRequest.isPresent()) {
            BookingEntity previous = existingRequest.get();
            if (!previous.getRequestFingerprint().equals(fingerprint)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different booking request");
            }
            return Booking.from(previous);
        }

        validateTimes(request);
        RoomEntity room = roomRepository.findByNameForUpdate(request.roomName())
                .filter(RoomEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Room not found: " + request.roomName()));
        List<String> attendees = uniqueAttendees(request);
        if (attendees.size() > room.getCapacity()) {
            throw new BookingValidationException(Map.of(
                    "attendeeEmails",
                    List.of("Attendee count exceeds room capacity of " + room.getCapacity())
            ));
        }

        List<BookingEntity> conflicts = bookingRepository.findConflicts(
                room.getName(), request.startTime(), request.endTime());
        if (!conflicts.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Booking overlaps with booking " + conflicts.get(0).getId());
        }

        BookingEntity booking = bookingRepository.save(new BookingEntity(
                room,
                request.title().trim(),
                request.organiserEmail().trim(),
                attendees,
                request.startTime(),
                request.endTime(),
                LocalDateTime.now(clock),
                idempotencyKey,
                fingerprint
        ));
        bookingRepository.flush();
        return Booking.from(booking);
    }

    @Transactional(readOnly = true)
    public Booking getBooking(UUID id) {
        return bookingRepository.findById(id)
                .map(Booking::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Booking> listBookings(String roomName, LocalDate date) {
        if (!roomRepository.existsById(roomName)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Room not found: " + roomName);
        }
        LocalDateTime dayStart = date.atStartOfDay();
        return bookingRepository
                .findByRoom_NameAndCancelledFalseAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTime(
                        roomName, dayStart, date.plusDays(1).atStartOfDay())
                .stream()
                .map(Booking::from)
                .toList();
    }

    @Transactional
    public void cancelBooking(UUID id) {
        BookingEntity booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found: " + id));
        booking.cancel();
    }

    @Transactional(readOnly = true)
    public List<FreeSlot> findFreeSlots(FreeSlotsRequest request) {
        LocalDate date = request.date();
        LocalDateTime workStart = date.atTime(WORK_START);
        LocalDateTime workEnd = date.atTime(WORK_END);
        List<String> attendees = uniqueEmails(request.attendeeEmails());
        List<Interval> busyIntervals = attendees.isEmpty()
                ? List.of()
                : bookingRepository.findAttendeeBookings(attendees, workStart, workEnd)
                        .stream()
                        .map(booking -> new Interval(
                                booking.getStartTime().isBefore(workStart) ? workStart : booking.getStartTime(),
                                booking.getEndTime().isAfter(workEnd) ? workEnd : booking.getEndTime()))
                        .sorted(Comparator.comparing(Interval::start).thenComparing(Interval::end))
                        .toList();

        List<Interval> mergedBusy = mergeIntervals(busyIntervals);
        List<Interval> available = new ArrayList<>();
        LocalDateTime cursor = workStart;
        for (Interval busy : mergedBusy) {
            if (Duration.between(cursor, busy.start()).toMinutes() >= request.minimumDurationMinutes()) {
                available.add(new Interval(cursor, busy.start()));
            }
            if (busy.end().isAfter(cursor)) {
                cursor = busy.end();
            }
        }
        if (Duration.between(cursor, workEnd).toMinutes() >= request.minimumDurationMinutes()) {
            available.add(new Interval(cursor, workEnd));
        }

        int groupSize = attendees.size();
        List<RoomEntity> activeRooms = roomRepository.findAllByActiveTrueOrderByName();
        return available.stream()
                .map(interval -> new FreeSlot(
                        interval.start(),
                        interval.end(),
                        activeRooms.stream()
                                .filter(room -> room.getCapacity() >= groupSize)
                                .filter(room -> !bookingRepository.existsConflict(
                                        room.getName(), interval.start(), interval.end()))
                                .map(Room::from)
                                .toList()
                ))
                .toList();
    }

    private List<Interval> mergeIntervals(List<Interval> sorted) {
        if (sorted.isEmpty()) {
            return List.of();
        }
        List<Interval> merged = new ArrayList<>();
        Interval current = sorted.get(0);
        for (int index = 1; index < sorted.size(); index++) {
            Interval next = sorted.get(index);
            if (!next.start().isAfter(current.end())) {
                current = new Interval(current.start(),
                        next.end().isAfter(current.end()) ? next.end() : current.end());
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
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
            addError(errors, "endTime", "End time must be within working hours (09:00-18:00)");
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

    private String validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BookingValidationException(Map.of(
                    "Idempotency-Key", List.of("Idempotency-Key header is required")
            ));
        }
        String normalized = key.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BookingValidationException(Map.of(
                    "Idempotency-Key",
                    List.of("Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters")
            ));
        }
        return normalized;
    }

    private String fingerprint(CreateBookingRequest request) {
        List<String> values = new ArrayList<>();
        values.add(request.roomName().trim());
        values.add(request.title().trim());
        values.add(request.organiserEmail().trim().toLowerCase(Locale.ROOT));
        values.add(request.startTime().toString());
        values.add(request.endTime().toString());
        values.addAll(uniqueEmails(request.attendeeEmails()));

        StringBuilder canonical = new StringBuilder();
        for (String value : values) {
            canonical.append(value.length()).append(':').append(value);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private List<String> uniqueAttendees(CreateBookingRequest request) {
        List<String> attendees = new ArrayList<>();
        attendees.add(request.organiserEmail().trim());
        attendees.addAll(request.attendeeEmails());
        return uniqueEmails(attendees);
    }

    private List<String> uniqueEmails(List<String> emails) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String email : emails) {
            String trimmed = email.trim();
            unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return new ArrayList<>(unique.values());
    }

    private boolean isOnStep(LocalTime time) {
        return time.getMinute() % 15 == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }

    private void addError(Map<String, List<String>> errors, String field, String message) {
        errors.computeIfAbsent(field, ignored -> new ArrayList<>()).add(message);
    }

    private record Interval(LocalDateTime start, LocalDateTime end) {
    }
}
