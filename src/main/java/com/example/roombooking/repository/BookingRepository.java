package com.example.roombooking.repository;

import com.example.roombooking.model.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {
    Optional<BookingEntity> findByIdempotencyKey(String idempotencyKey);

    long countByRoom_NameAndStartTimeAndEndTime(
            String roomName,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    List<BookingEntity> findByRoom_NameAndCancelledFalseAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTime(
            String roomName,
            LocalDateTime dayStart,
            LocalDateTime nextDayStart
    );

    @Query("""
            select b from BookingEntity b
            where b.cancelled = false
              and b.startTime < :windowEnd
              and b.endTime > :windowStart
              and (
                  lower(b.organiserEmail) in :attendees
                  or exists (
                      select attendee from b.attendeeEmails attendee
                      where lower(attendee) in :attendees
                  )
              )
            """)
    List<BookingEntity> findAttendeeBookings(
            @Param("attendees") List<String> attendees,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd
    );

    @Query("""
            select b from BookingEntity b
            where b.room.name = :roomName
              and b.cancelled = false
              and b.startTime < :endTime
              and b.endTime > :startTime
            order by b.startTime, b.id
            """)
    List<BookingEntity> findConflicts(
            @Param("roomName") String roomName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("""
            select count(b) > 0 from BookingEntity b
            where b.room.name = :roomName
              and b.cancelled = false
              and b.startTime < :endTime
              and b.endTime > :startTime
            """)
    boolean existsConflict(
            @Param("roomName") String roomName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
