# Room Booking API

A Spring Boot REST API for booking meeting rooms. The application seeds five rooms on
startup and currently stores rooms and bookings in memory; data is reset when the
application restarts.

## Run

Requires Java 21 and Maven:

```shell
mvn spring-boot:run
```

Run automated tests with:

```shell
mvn test
```

## Endpoints

- `GET /rooms` — list seeded rooms.
- `POST /bookings` — create a booking.
- `GET /bookings/{id}` — retrieve a booking.
- `GET /rooms/{roomName}/bookings?date=YYYY-MM-DD` — list active bookings by start time.
- `DELETE /bookings/{id}` — cancel a booking (returns `204`).

Example create request:

```json
{
  "roomName": "Cedar",
  "title": "Project meeting",
  "organiserEmail": "organiser@example.com",
  "attendeeEmails": ["guest@example.com"],
  "startTime": "2026-01-02T10:00:00",
  "endTime": "2026-01-02T11:00:00"
}
```

Times use local ISO-8601 date-time values and the server's default timezone. Bookings
must be on the same day, between 09:00 and 18:00, aligned to 15-minute increments,
no longer than four hours, and not in the past. The organiser is automatically included
in the attendee list and is counted once. Cancelled bookings are excluded from conflict
checks and room booking lists.

All API errors use the same JSON shape with `timestamp`, `status`, `error`, `message`,
and `fieldErrors`. Validation failures return `400`; unknown rooms/bookings return
`404`; overlapping bookings return `409` and identify the conflicting booking.
