package com.example.roombooking.repository;

import com.example.roombooking.model.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<RoomEntity, String> {
    List<RoomEntity> findAllByActiveTrueOrderByName();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomEntity r where r.name = :name")
    Optional<RoomEntity> findByNameForUpdate(@Param("name") String name);
}
