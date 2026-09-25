package com.cinepass.bookingservice.repository;

import com.cinepass.bookingservice.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByBookingReference(String bookingReference);
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Booking> findByShowId(Long showId);
}
