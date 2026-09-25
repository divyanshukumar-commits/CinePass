package com.cinepass.bookingservice.repository;

import com.cinepass.bookingservice.model.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {
    List<BookingSeat> findByBookingId(Long bookingId);
    List<BookingSeat> findByShowId(Long showId);
    boolean existsByShowIdAndSeatNumber(Long showId, String seatNumber);
}
