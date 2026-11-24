package com.example.roombooking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsOverlapAndAllowsBackToBackBooking() throws Exception {
        String response = mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Cedar", "2026-01-02T10:00:00", "2026-01-02T11:00:00")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Cedar", "2026-01-02T10:30:00", "2026-01-02T11:30:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString(id)));

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Cedar", "2026-01-02T11:00:00", "2026-01-02T12:00:00")))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsBookingThatCompletelyCoversExistingBooking() throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Maple", "2026-01-02T10:00:00", "2026-01-02T10:30:00")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Maple", "2026-01-02T09:00:00", "2026-01-02T12:00:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("overlaps")));
    }

    @Test
    void returnsFieldErrorsForInvalidBookingTimes() throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Oak", "2026-01-02T10:07:00", "2026-01-02T10:30:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startTime").exists())
                .andExpect(jsonPath("$.fieldErrors.endTime").doesNotExist());
    }

    private String request(String room, String start, String end) {
        return """
                {
                  "roomName": "%s",
                  "title": "Project meeting",
                  "organiserEmail": "organiser@example.com",
                  "attendeeEmails": ["guest@example.com"],
                  "startTime": "%s",
                  "endTime": "%s"
                }
                """.formatted(room, start, end);
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
