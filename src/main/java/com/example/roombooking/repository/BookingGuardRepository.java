package com.example.roombooking.repository;

import com.example.roombooking.model.BookingGuardEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BookingGuardRepository extends JpaRepository<BookingGuardEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from BookingGuardEntity g where g.id = 1")
    BookingGuardEntity lockForBookingCreation();
}
