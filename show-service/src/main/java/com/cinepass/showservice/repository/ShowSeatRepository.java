package com.cinepass.showservice.repository;

import com.cinepass.showservice.model.SeatStatus;
import com.cinepass.showservice.model.ShowSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowIdOrderBySeatNumberAsc(Long showId);

    List<ShowSeat> findByShowIdAndStatus(Long showId, SeatStatus status);

    Optional<ShowSeat> findByShowIdAndSeatNumber(Long showId, String seatNumber);

    // Pessimistic Locking query for high-concurrency seat reservation
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShowSeat s WHERE s.showId = :showId AND s.seatNumber IN :seatNumbers")
    List<ShowSeat> findSeatsForUpdate(@Param("showId") Long showId, @Param("seatNumbers") List<String> seatNumbers);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM ShowSeat s WHERE s.showId = :showId")
    void deleteByShowId(@Param("showId") Long showId);
}
