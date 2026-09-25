package com.cinepass.showservice.service;

import com.cinepass.showservice.dto.CreateShowRequest;
import com.cinepass.showservice.model.SeatStatus;
import com.cinepass.showservice.model.Show;
import com.cinepass.showservice.model.ShowSeat;
import com.cinepass.showservice.repository.ShowRepository;
import com.cinepass.showservice.repository.ShowSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public ShowService(ShowRepository showRepository, ShowSeatRepository showSeatRepository) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @Transactional
    public Show createShow(CreateShowRequest request) {
        LocalDateTime start = request.getStartTime() != null ? request.getStartTime() : LocalDateTime.now().plusHours(2);
        LocalDateTime end = request.getEndTime() != null ? request.getEndTime() : start.plusHours(2).plusMinutes(30);
        Double price = request.getBasePrice() != null ? request.getBasePrice() : 250.0;
        Long screen = request.getScreenId() != null ? request.getScreenId() : 1L;
        Long movie = request.getMovieId() != null ? request.getMovieId() : 1L;

        Show show = new Show(movie, screen, start, end, price);
        Show savedShow = showRepository.save(show);

        // Auto-generate realistic cinema seat matrix (Rows A-D, 10 seats each = 40 seats)
        List<ShowSeat> seats = new ArrayList<>();
        String[] rows = {"A", "B", "C", "D"};
        for (String row : rows) {
            String tier;
            double priceMultiplier;
            if (row.equals("A")) {
                tier = "VIP";
                priceMultiplier = 1.5;
            } else if (row.equals("B") || row.equals("C")) {
                tier = "PREMIUM";
                priceMultiplier = 1.25;
            } else {
                tier = "STANDARD";
                priceMultiplier = 1.0;
            }

            double seatPrice = savedShow.getBasePrice() * priceMultiplier;

            for (int i = 1; i <= 10; i++) {
                String seatNumber = row + i;
                seats.add(new ShowSeat(
                        savedShow.getId(),
                        seatNumber,
                        tier,
                        seatPrice,
                        SeatStatus.AVAILABLE
                ));
            }
        }

        showSeatRepository.saveAll(seats);
        return savedShow;
    }

    public List<Show> getAllShows(Long movieId) {
        if (movieId != null) {
            return showRepository.findByMovieId(movieId);
        }
        return showRepository.findAll();
    }

    public Optional<Show> getShowById(Long id) {
        return showRepository.findById(id);
    }

    @Transactional
    public Optional<Show> updateShow(Long id, CreateShowRequest request) {
        return showRepository.findById(id).map(existingShow -> {
            if (request.getMovieId() != null) existingShow.setMovieId(request.getMovieId());
            if (request.getScreenId() != null) existingShow.setScreenId(request.getScreenId());
            if (request.getStartTime() != null) existingShow.setStartTime(request.getStartTime());
            if (request.getEndTime() != null) existingShow.setEndTime(request.getEndTime());
            if (request.getBasePrice() != null) existingShow.setBasePrice(request.getBasePrice());
            return showRepository.save(existingShow);
        });
    }

    @Transactional
    public boolean deleteShow(Long id) {
        if (!showRepository.existsById(id)) {
            return false;
        }
        showSeatRepository.deleteByShowId(id);
        showRepository.deleteById(id);
        return true;
    }

    public List<ShowSeat> getSeatsForShow(Long showId) {
        return showSeatRepository.findByShowIdOrderBySeatNumberAsc(showId);
    }

    /**
     * Atomically lock seats under high concurrency using Database Pessimistic Write Locking.
     */
    @Transactional
    public List<ShowSeat> lockSeats(Long showId, List<String> seatNumbers, Long userId) {
        // Acquire Pessimistic Write Lock on requested seat rows (SELECT ... FOR UPDATE)
        List<ShowSeat> lockedSeats = showSeatRepository.findSeatsForUpdate(showId, seatNumbers);

        if (lockedSeats.size() != seatNumbers.size()) {
            throw new IllegalArgumentException("One or more requested seats do not exist for this show");
        }

        for (ShowSeat seat : lockedSeats) {
            if (seat.getStatus() == SeatStatus.BOOKED) {
                throw new IllegalStateException("Seat " + seat.getSeatNumber() + " is already booked");
            }
            if (seat.getStatus() == SeatStatus.LOCKED && !userId.equals(seat.getLockedByUserId())) {
                // Check if lock expired (lease timeout > 5 minutes)
                if (seat.getLockTimestamp() != null &&
                        seat.getLockTimestamp().isAfter(LocalDateTime.now().minusMinutes(5))) {
                    throw new IllegalStateException("Seat " + seat.getSeatNumber() + " is currently reserved by another customer");
                }
            }

            seat.setStatus(SeatStatus.LOCKED);
            seat.setLockedByUserId(userId);
            seat.setLockTimestamp(LocalDateTime.now());
        }

        return showSeatRepository.saveAll(lockedSeats);
    }

    /**
     * Atomically book seats under high concurrency.
     */
    @Transactional
    public List<ShowSeat> bookSeats(Long showId, List<String> seatNumbers, Long userId) {
        List<ShowSeat> seats = showSeatRepository.findSeatsForUpdate(showId, seatNumbers);

        if (seats.size() != seatNumbers.size()) {
            throw new IllegalArgumentException("One or more requested seats do not exist for this show");
        }

        for (ShowSeat seat : seats) {
            if (seat.getStatus() == SeatStatus.BOOKED) {
                throw new IllegalStateException("Seat " + seat.getSeatNumber() + " has already been booked");
            }

            if (seat.getStatus() == SeatStatus.LOCKED && seat.getLockedByUserId() != null && !seat.getLockedByUserId().equals(userId)) {
                if (seat.getLockTimestamp() != null && seat.getLockTimestamp().isAfter(LocalDateTime.now().minusMinutes(5))) {
                    throw new IllegalStateException("Seat " + seat.getSeatNumber() + " is currently held by another user");
                }
            }

            seat.setStatus(SeatStatus.BOOKED);
            seat.setLockedByUserId(userId);
            seat.setLockTimestamp(LocalDateTime.now());
        }

        return showSeatRepository.saveAll(seats);
    }

    /**
     * Release locked seats back to AVAILABLE.
     */
    @Transactional
    public void releaseSeats(Long showId, List<String> seatNumbers, Long userId) {
        List<ShowSeat> seats = showSeatRepository.findSeatsForUpdate(showId, seatNumbers);
        for (ShowSeat seat : seats) {
            if (seat.getStatus() == SeatStatus.LOCKED && userId.equals(seat.getLockedByUserId())) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setLockedByUserId(null);
                seat.setLockTimestamp(null);
            }
        }
        showSeatRepository.saveAll(seats);
    }
}
