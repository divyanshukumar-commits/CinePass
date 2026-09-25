package com.cinepass.showservice;

import com.cinepass.showservice.controller.ShowController;
import com.cinepass.showservice.dto.CreateShowRequest;
import com.cinepass.showservice.dto.SeatActionRequest;
import com.cinepass.showservice.model.SeatStatus;
import com.cinepass.showservice.model.Show;
import com.cinepass.showservice.model.ShowSeat;
import com.cinepass.showservice.repository.ShowRepository;
import com.cinepass.showservice.repository.ShowSeatRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ShowServiceIntegrationTests {

    @Autowired
    private ShowController showController;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Test
    @DisplayName("Create show auto-generates 40 seats with tiered pricing and persists in database")
    void testCreateShowAndSeatGeneration() {
        CreateShowRequest request = new CreateShowRequest(
                101L,
                1L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2).plusMinutes(30),
                300.0
        );

        ResponseEntity<Show> response = showController.createShow(request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        Long showId = response.getBody().getId();
        assertNotNull(showId);

        // Verify seats generated
        List<ShowSeat> seats = showController.getSeatsForShow(showId);
        assertEquals(40, seats.size());

        // Check Row A (VIP) = 300 * 1.5 = 450.0
        ShowSeat seatA1 = seats.stream().filter(s -> "A1".equals(s.getSeatNumber())).findFirst().orElseThrow();
        assertEquals("VIP", seatA1.getSeatTier());
        assertEquals(450.0, seatA1.getPrice());
        assertEquals(SeatStatus.AVAILABLE, seatA1.getStatus());

        // Check Row B (PREMIUM) = 300 * 1.25 = 375.0
        ShowSeat seatB1 = seats.stream().filter(s -> "B1".equals(s.getSeatNumber())).findFirst().orElseThrow();
        assertEquals("PREMIUM", seatB1.getSeatTier());
        assertEquals(375.0, seatB1.getPrice());

        // Check Row D (STANDARD) = 300 * 1.0 = 300.0
        ShowSeat seatD1 = seats.stream().filter(s -> "D1".equals(s.getSeatNumber())).findFirst().orElseThrow();
        assertEquals("STANDARD", seatD1.getSeatTier());
        assertEquals(300.0, seatD1.getPrice());
    }

    @Test
    @DisplayName("PUT /shows/{id} updates show details and DELETE /shows/{id} removes show and seats")
    void testUpdateAndDeleteShow() {
        // 1. Create show
        CreateShowRequest createReq = new CreateShowRequest(
                202L,
                1L,
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(2),
                200.0
        );
        ResponseEntity<Show> createdResp = showController.createShow(createReq);
        Long showId = createdResp.getBody().getId();

        // 2. Update show
        CreateShowRequest updateReq = new CreateShowRequest(
                202L,
                2L, // Updated screen
                LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusDays(3).plusHours(3),
                250.0 // Updated base price
        );
        ResponseEntity<Show> updateResp = showController.updateShow(showId, updateReq);
        assertEquals(HttpStatus.OK, updateResp.getStatusCode());
        assertEquals(2L, updateResp.getBody().getScreenId());
        assertEquals(250.0, updateResp.getBody().getBasePrice());

        // Verify update persisted
        Show fetched = showController.getShowById(showId).getBody();
        assertNotNull(fetched);
        assertEquals(2L, fetched.getScreenId());
        assertEquals(250.0, fetched.getBasePrice());

        // 3. Delete show
        ResponseEntity<String> deleteResp = showController.deleteShow(showId);
        assertEquals(HttpStatus.OK, deleteResp.getStatusCode());
        assertEquals("Show deleted successfully", deleteResp.getBody());

        // Verify show is gone
        ResponseEntity<Show> notFoundResp = showController.getShowById(showId);
        assertEquals(HttpStatus.NOT_FOUND, notFoundResp.getStatusCode());

        // Verify seats are cascade deleted
        List<ShowSeat> orphanSeats = showSeatRepository.findByShowIdOrderBySeatNumberAsc(showId);
        assertTrue(orphanSeats.isEmpty());

        // Verify deleting non-existent show returns 404
        ResponseEntity<String> deleteNonExistent = showController.deleteShow(showId);
        assertEquals(HttpStatus.NOT_FOUND, deleteNonExistent.getStatusCode());
    }

    @Test
    @DisplayName("Seat locking, booking, and duplicate-booking prevention")
    void testSeatLockingAndBookingWorkflow() {
        CreateShowRequest createReq = new CreateShowRequest(
                303L,
                1L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2),
                200.0
        );
        Show show = showController.createShow(createReq).getBody();
        Long showId = show.getId();

        // 1. Lock seat A1 by User 1
        SeatActionRequest lockReq = new SeatActionRequest();
        lockReq.setSeatNumbers(List.of("A1"));
        lockReq.setUserId(1L);
        ResponseEntity<?> lockResp = showController.lockSeats(showId, lockReq);
        assertEquals(HttpStatus.OK, lockResp.getStatusCode());

        // Verify seat is locked
        ShowSeat lockedSeat = showSeatRepository.findByShowIdAndSeatNumber(showId, "A1").orElseThrow();
        assertEquals(SeatStatus.LOCKED, lockedSeat.getStatus());
        assertEquals(1L, lockedSeat.getLockedByUserId());

        // 2. User 2 attempts to lock same seat A1 -> 409 Conflict
        SeatActionRequest lockReq2 = new SeatActionRequest();
        lockReq2.setSeatNumbers(List.of("A1"));
        lockReq2.setUserId(2L);
        ResponseEntity<?> lockConflictResp = showController.lockSeats(showId, lockReq2);
        assertEquals(HttpStatus.CONFLICT, lockConflictResp.getStatusCode());

        // 3. User 1 books seat A1
        SeatActionRequest bookReq = new SeatActionRequest();
        bookReq.setSeatNumbers(List.of("A1"));
        bookReq.setUserId(1L);
        ResponseEntity<?> bookResp = showController.bookSeats(showId, bookReq);
        assertEquals(HttpStatus.OK, bookResp.getStatusCode());

        // Verify seat is booked
        ShowSeat bookedSeat = showSeatRepository.findByShowIdAndSeatNumber(showId, "A1").orElseThrow();
        assertEquals(SeatStatus.BOOKED, bookedSeat.getStatus());

        // 4. User 2 attempts to book same seat A1 -> 409 Conflict (Double booking prevented!)
        SeatActionRequest bookReq2 = new SeatActionRequest();
        bookReq2.setSeatNumbers(List.of("A1"));
        bookReq2.setUserId(2L);
        ResponseEntity<?> bookConflictResp = showController.bookSeats(showId, bookReq2);
        assertEquals(HttpStatus.CONFLICT, bookConflictResp.getStatusCode());

        // 5. Test release on locked seat
        SeatActionRequest lockReqB1 = new SeatActionRequest();
        lockReqB1.setSeatNumbers(List.of("B1"));
        lockReqB1.setUserId(1L);
        showController.lockSeats(showId, lockReqB1);

        SeatActionRequest releaseReq = new SeatActionRequest();
        releaseReq.setSeatNumbers(List.of("B1"));
        releaseReq.setUserId(1L);
        ResponseEntity<?> releaseResp = showController.releaseSeats(showId, releaseReq);
        assertEquals(HttpStatus.OK, releaseResp.getStatusCode());

        ShowSeat releasedSeat = showSeatRepository.findByShowIdAndSeatNumber(showId, "B1").orElseThrow();
        assertEquals(SeatStatus.AVAILABLE, releasedSeat.getStatus());
    }

    @Test
    @DisplayName("High-concurrency test: Multiple simultaneous attempts for the same show + seat result in exactly one successful booking and the others fail cleanly")
    void testConcurrentSameSeatBooking() throws InterruptedException {
        CreateShowRequest createReq = new CreateShowRequest(
                404L,
                1L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2),
                250.0
        );
        Show show = showController.createShow(createReq).getBody();
        assertNotNull(show);
        Long showId = show.getId();

        int totalThreads = 10;
        java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(totalThreads);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finishLatch = new java.util.concurrent.CountDownLatch(totalThreads);

        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger conflictCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 1; i <= totalThreads; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // wait for start signal
                    SeatActionRequest request = new SeatActionRequest();
                    request.setSeatNumbers(List.of("C5"));
                    request.setUserId(userId);

                    ResponseEntity<?> response = showController.bookSeats(showId, request);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all 10 threads concurrently
        assertTrue(finishLatch.await(15, java.util.concurrent.TimeUnit.SECONDS));
        executorService.shutdown();

        // Exactly 1 user succeeded, 9 failed with clean conflict
        assertEquals(1, successCount.get(), "Exactly one user should successfully book seat C5");
        assertEquals(totalThreads - 1, conflictCount.get(), "All other simultaneous attempts must fail cleanly with conflict");

        // Verify in database: seat is booked
        ShowSeat seat = showSeatRepository.findByShowIdAndSeatNumber(showId, "C5").orElseThrow();
        assertEquals(SeatStatus.BOOKED, seat.getStatus());
        assertNotNull(seat.getLockedByUserId());
    }
}
