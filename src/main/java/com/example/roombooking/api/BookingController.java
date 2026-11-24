package com.example.roombooking.api;

import com.example.roombooking.model.Booking;
import com.example.roombooking.model.Room;
import com.example.roombooking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/rooms")
    public List<Room> listRooms() {
        return bookingService.listRooms();
    }

    @PostMapping("/bookings")
    public ResponseEntity<Booking> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        Booking booking = bookingService.createBooking(request, idempotencyKey);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(booking.id())
                .toUri();
        return ResponseEntity.created(location).body(booking);
    }

    @GetMapping("/bookings/{id}")
    public Booking getBooking(@PathVariable UUID id) {
        return bookingService.getBooking(id);
    }

    @GetMapping("/rooms/{roomName}/bookings")
    public List<Booking> listBookings(
            @PathVariable String roomName,
            @RequestParam LocalDate date
    ) {
        return bookingService.listBookings(roomName, date);
    }

    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<Void> cancelBooking(@PathVariable UUID id) {
        bookingService.cancelBooking(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/free-slots")
    public List<FreeSlot> findFreeSlots(@Valid @RequestBody FreeSlotsRequest request) {
        return bookingService.findFreeSlots(request);
    }
}
