package com.example.roombooking;

import com.example.roombooking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingApiIntegrationTest {
    private static final String TEST_DATE = "2026-01-02";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void rejectsOverlapAndAllowsBackToBackBooking() throws Exception {
        String response = create("Cedar", "2026-01-02T10:00:00", "2026-01-02T11:00:00", "overlap-first")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        create("Cedar", "2026-01-02T10:30:00", "2026-01-02T11:30:00", "overlap-second")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString(id)));

        create("Cedar", "2026-01-02T11:00:00", "2026-01-02T12:00:00", "overlap-back-to-back")
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsBookingThatCompletelyCoversExistingBooking() throws Exception {
        create("Maple", "2026-01-02T10:00:00", "2026-01-02T10:30:00", "covers-existing")
                .andExpect(status().isCreated());

        create("Maple", "2026-01-02T09:00:00", "2026-01-02T12:00:00", "covers-container")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("overlaps")));
    }

    @Test
    void returnsExactMergedCommonFreeSlotsAndRooms() throws Exception {
        createForAttendee("Cedar", "2026-01-02T10:00:00", "2026-01-02T11:00:00",
                "attendee-a@example.com", "free-a-one");
        createForAttendee("Maple", "2026-01-02T09:30:00", "2026-01-02T10:30:00",
                "attendee-a@example.com", "free-a-two");
        createForAttendee("Oak", "2026-01-02T13:00:00", "2026-01-02T14:00:00",
                "attendee-b@example.com", "free-b-one");
        createForAttendee("Pine", "2026-01-02T13:15:00", "2026-01-02T13:45:00",
                "attendee-b@example.com", "free-b-two");

        mockMvc.perform(post("/free-slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "%s",
                                  "minimumDurationMinutes": 30,
                                  "attendeeEmails": ["attendee-a@example.com", "attendee-b@example.com"]
                                }
                                """.formatted(TEST_DATE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startTime").value("2026-01-02T09:00:00"))
                .andExpect(jsonPath("$[0].endTime").value("2026-01-02T09:30:00"))
                .andExpect(jsonPath("$[1].startTime").value("2026-01-02T11:00:00"))
                .andExpect(jsonPath("$[1].endTime").value("2026-01-02T13:00:00"))
                .andExpect(jsonPath("$[2].startTime").value("2026-01-02T14:00:00"))
                .andExpect(jsonPath("$[2].endTime").value("2026-01-02T18:00:00"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].availableRooms.length()").value(5));
    }

    @Test
    void cancelledBookingsNoLongerBlockAttendeeFreeSlots() throws Exception {
        String response = createForAttendee("Birch", "2026-01-02T10:00:00", "2026-01-02T11:00:00",
                "cancelled-attendee@example.com", "cancelled-free-slot")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/bookings/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/free-slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "%s",
                                  "minimumDurationMinutes": 540,
                                  "attendeeEmails": ["cancelled-attendee@example.com"]
                                }
                                """.formatted(TEST_DATE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startTime").value("2026-01-02T09:00:00"))
                .andExpect(jsonPath("$[0].endTime").value("2026-01-02T18:00:00"));
    }

    @Test
    void idempotencyKeyReturnsTheOriginalBookingForRetries() throws Exception {
        String content = bookingRequest(
                "Oak", "2026-01-02T15:00:00", "2026-01-02T15:30:00", "retry@example.com");
        String first = mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "same-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String second = mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "same-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(
                com.jayway.jsonpath.JsonPath.read(first, "$.id"),
                com.jayway.jsonpath.JsonPath.read(second, "$.id")
        );
    }

    @Test
    void twentyConcurrentOverlappingRequestsCreateExactlyOneBooking() throws Exception {
        int requests = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int index = 0; index < requests; index++) {
                int requestNumber = index;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start concurrent requests");
                    }
                    return mockMvc.perform(post("/bookings")
                                    .header("Idempotency-Key", "simultaneous-" + requestNumber)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(bookingRequest("Birch", "2026-01-02T16:00:00",
                                            "2026-01-02T16:30:00", "load@example.com")))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent request workers did not become ready");
            }
            start.countDown();

            int created = 0;
            int conflicts = 0;
            for (Future<Integer> result : results) {
                int statusCode = result.get(30, TimeUnit.SECONDS);
                if (statusCode == 201) {
                    created++;
                } else if (statusCode == 409) {
                    conflicts++;
                }
            }
            assertEquals(1, created);
            assertEquals(19, conflicts);
            assertEquals(1, bookingRepository.countByRoom_NameAndStartTimeAndEndTime(
                    "Birch",
                    LocalDateTime.parse("2026-01-02T16:00:00"),
                    LocalDateTime.parse("2026-01-02T16:30:00")
            ));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void returnsFieldErrorsForInvalidBookingTimes() throws Exception {
        create("Oak", "2026-01-02T10:07:00", "2026-01-02T10:30:00", "invalid-step")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startTime").exists())
                .andExpect(jsonPath("$.fieldErrors.endTime").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions create(
            String room,
            String start,
            String end,
            String key
    ) throws Exception {
        return mockMvc.perform(post("/bookings")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingRequest(room, start, end, "organiser@example.com")));
    }

    private org.springframework.test.web.servlet.ResultActions createForAttendee(
            String room,
            String start,
            String end,
            String attendee,
            String key
    ) throws Exception {
        return mockMvc.perform(post("/bookings")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingRequest(room, start, end, attendee)));
    }

    private String bookingRequest(String room, String start, String end, String organiser) {
        return """
                {
                  "roomName": "%s",
                  "title": "Project meeting",
                  "organiserEmail": "%s",
                  "attendeeEmails": [],
                  "startTime": "%s",
                  "endTime": "%s"
                }
                """.formatted(room, organiser, start, end);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-01-01T08:00:00Z"), ZoneId.of("UTC"));
        }
    }
}
