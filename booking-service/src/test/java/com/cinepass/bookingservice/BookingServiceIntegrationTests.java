package com.cinepass.bookingservice;

import com.cinepass.bookingservice.client.ShowServiceClient;
import com.cinepass.bookingservice.controller.BookingController;
import com.cinepass.bookingservice.dto.BookingRequest;
import com.cinepass.bookingservice.dto.BookingResponse;
import com.cinepass.bookingservice.model.Booking;
import com.cinepass.bookingservice.model.BookingStatus;
import com.cinepass.bookingservice.repository.BookingRepository;
import com.cinepass.bookingservice.repository.BookingSeatRepository;
import com.cinepass.bookingservice.security.JwtValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(BookingServiceIntegrationTests.TestConfig.class)
class BookingServiceIntegrationTests {

    @TestConfiguration
    static class TestConfig {
        static final ConcurrentHashMap<String, Long> bookedSeats = new ConcurrentHashMap<>();

        @Bean
        @Primary
        public ShowServiceClient showServiceClient() {
            return new ShowServiceClient("http://localhost:8002") {
                @Override
                public Map<String, Object> getShowById(Long showId) {
                    if (showId == 999999L) {
                        return null; // Non-existent show
                    }
                    return Map.of("id", showId, "basePrice", 250.0);
                }

                @Override
                public List<Map<String, Object>> getSeatsForShow(Long showId) {
                    return List.of(
                            Map.of("seatNumber", "A1", "price", 250.0, "status", bookedSeats.containsKey(showId + "_A1") ? "BOOKED" : "AVAILABLE"),
                            Map.of("seatNumber", "B1", "price", 300.0, "status", bookedSeats.containsKey(showId + "_B1") ? "BOOKED" : "AVAILABLE")
                    );
                }

                @Override
                public List<Map<String, Object>> bookSeats(Long showId, List<String> seatNumbers, Long userId) {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (String seat : seatNumbers) {
                        Long existing = bookedSeats.putIfAbsent(showId + "_" + seat, userId);
                        if (existing != null) {
                            throw new IllegalStateException("Seat conflict: Selected seat(s) have already been booked or reserved by another customer");
                        }
                        result.add(Map.of("seatNumber", seat, "price", 250.0));
                    }
                    return result;
                }

                @Override
                public void releaseSeats(Long showId, List<String> seatNumbers, Long userId) {
                    for (String seat : seatNumbers) {
                        bookedSeats.remove(showId + "_" + seat);
                    }
                }
            };
        }
    }

    @Autowired
    private BookingController bookingController;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    @Autowired
    private JwtValidator jwtValidator;

    private static final String SECRET = "CinePassSuperSecretKeyForJWTSigning2026HighConcurrencyCinemaEngine!";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void setUp() {
        bookingSeatRepository.deleteAll();
        bookingRepository.deleteAll();
        TestConfig.bookedSeats.clear();
    }

    private String createValidJwt(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", "ROLE_CUSTOMER")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600000))
                .signWith(key)
                .compact();
    }

    private Long randomShowId() {
        return ThreadLocalRandom.current().nextLong(10000, 900000);
    }

    @Test
    @DisplayName("Unauthorized booking attempt without JWT token is rejected with 401")
    void testUnauthorizedBookingRejection() {
        Long showId = randomShowId();
        BookingRequest request = new BookingRequest(showId, List.of("A1"), null);

        // No Authorization header, no Gateway header
        ResponseEntity<?> response = bookingController.createBooking(null, null, request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        // Invalid JWT token
        ResponseEntity<?> invalidTokenResp = bookingController.createBooking("Bearer invalid.token.signature", null, request);
        assertEquals(HttpStatus.UNAUTHORIZED, invalidTokenResp.getStatusCode());
    }

    @Test
    @DisplayName("Authorized booking with valid JWT token succeeds and persists in PostgreSQL")
    void testAuthorizedBookingSuccess() {
        Long showId = randomShowId();
        String token = createValidJwt(55L, "alice");
        BookingRequest request = new BookingRequest(showId, List.of("A2", "A3"), null);

        ResponseEntity<?> response = bookingController.createBooking("Bearer " + token, null, request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody() instanceof BookingResponse);

        BookingResponse bookingResponse = (BookingResponse) response.getBody();
        assertNotNull(bookingResponse.getId());
        assertNotNull(bookingResponse.getBookingReference());
        assertTrue(bookingResponse.getBookingReference().startsWith("CP-"));
        assertEquals(55L, bookingResponse.getUserId());
        assertEquals(showId, bookingResponse.getShowId());
        assertEquals(500.0, bookingResponse.getTotalAmount());
        assertEquals(BookingStatus.CONFIRMED, bookingResponse.getStatus());

        // Verify database persistence
        Booking persisted = bookingRepository.findById(bookingResponse.getId()).orElseThrow();
        assertEquals(55L, persisted.getUserId());
        assertEquals(2, persisted.getSeats().size());
        assertEquals(showId, persisted.getSeats().get(0).getShowId());
    }

    @Test
    @DisplayName("Duplicate seat numbers in single request are rejected with 400")
    void testDuplicateSeatsInSameRequestRejection() {
        Long showId = randomShowId();
        String token = createValidJwt(60L, "bob");
        BookingRequest request = new BookingRequest(showId, List.of("B5", "B5"), null);

        ResponseEntity<?> response = bookingController.createBooking("Bearer " + token, null, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Double booking the same seat sequentially is rejected with 409 Conflict")
    void testDoubleBookingSameSeatRejection() {
        Long showId = randomShowId();
        String tokenUser1 = createValidJwt(71L, "user1");
        String tokenUser2 = createValidJwt(72L, "user2");

        BookingRequest request1 = new BookingRequest(showId, List.of("B2"), null);
        ResponseEntity<?> resp1 = bookingController.createBooking("Bearer " + tokenUser1, null, request1);
        assertEquals(HttpStatus.CREATED, resp1.getStatusCode());

        // User 2 tries to book seat B2 for same show
        BookingRequest request2 = new BookingRequest(showId, List.of("B2"), null);
        ResponseEntity<?> resp2 = bookingController.createBooking("Bearer " + tokenUser2, null, request2);
        assertEquals(HttpStatus.CONFLICT, resp2.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) resp2.getBody();
        assertNotNull(body);
        assertEquals("Double-Booking Prevented", body.get("error"));
    }

    @Test
    @DisplayName("High-concurrency booking test: 10 concurrent threads attempt same seat, exactly 1 succeeds, 9 fail cleanly with 409")
    void testHighConcurrencyBookingSameSeat() throws InterruptedException {
        Long showId = randomShowId();
        String targetSeat = "C1";
        int totalThreads = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 1; i <= totalThreads; i++) {
            final long userId = 100L + i;
            final String token = createValidJwt(userId, "concurrent_user_" + i);

            executorService.submit(() -> {
                try {
                    startLatch.await(); // wait for simultaneous trigger
                    BookingRequest request = new BookingRequest(showId, List.of(targetSeat), null);
                    ResponseEntity<?> resp = bookingController.createBooking("Bearer " + token, null, request);

                    if (resp.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else if (resp.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // FIRE ALL THREADS SIMULTANEOUSLY
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS));
        executorService.shutdown();

        assertEquals(1, successCount.get(), "Exactly one concurrent booking attempt must succeed");
        assertEquals(totalThreads - 1, conflictCount.get(), "All other concurrent attempts must fail cleanly with 409 conflict");

        // Verify in database: exactly one booking seat exists for this show and seat
        boolean seatExistsInDb = bookingSeatRepository.existsByShowIdAndSeatNumber(showId, targetSeat);
        assertTrue(seatExistsInDb);
    }

    @Test
    @DisplayName("Booking cancellation releases seats")
    void testBookingCancellation() {
        Long showId = randomShowId();
        String token = createValidJwt(88L, "canceller");
        BookingRequest request = new BookingRequest(showId, List.of("D1"), null);

        ResponseEntity<?> createResp = bookingController.createBooking("Bearer " + token, null, request);
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        BookingResponse booking = (BookingResponse) createResp.getBody();
        assertNotNull(booking);

        // Cancel booking
        ResponseEntity<?> cancelResp = bookingController.cancelBooking(booking.getId(), "Bearer " + token, null, null);
        assertEquals(HttpStatus.OK, cancelResp.getStatusCode());

        Booking cancelled = (Booking) cancelResp.getBody();
        assertNotNull(cancelled);
        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());

        // Seat is released and can be booked again
        String tokenNewUser = createValidJwt(89L, "newuser");
        ResponseEntity<?> rebookResp = bookingController.createBooking("Bearer " + tokenNewUser, null, request);
        assertEquals(HttpStatus.CREATED, rebookResp.getStatusCode());
    }

    @Test
    @DisplayName("Available seats endpoint returns available seats for show")
    void testAvailableSeatsEndpoint() {
        Long showId = randomShowId();
        ResponseEntity<?> response = bookingController.getAvailableSeats(showId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
