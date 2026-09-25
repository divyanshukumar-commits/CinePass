package com.cinepass.bookingservice.controller;

import com.cinepass.bookingservice.dto.BookingRequest;
import com.cinepass.bookingservice.dto.BookingResponse;
import com.cinepass.bookingservice.model.Booking;
import com.cinepass.bookingservice.security.JwtValidator;
import com.cinepass.bookingservice.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final JwtValidator jwtValidator;

    public BookingController(BookingService bookingService, JwtValidator jwtValidator) {
        this.bookingService = bookingService;
        this.jwtValidator = jwtValidator;
    }

    @PostMapping
    public ResponseEntity<?> createBooking(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String gatewayUserId,
            @RequestBody BookingRequest request) {

        Long userId = null;

        // 1. Check if Gateway forwarded authenticated user ID
        if (gatewayUserId != null && !gatewayUserId.isBlank()) {
            try {
                userId = Long.parseLong(gatewayUserId);
            } catch (NumberFormatException ignored) {
            }
        }

        // 2. Otherwise extract and validate from JWT Bearer token
        if (userId == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtValidator.validateToken(token)) {
                userId = jwtValidator.extractUserId(token);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired JWT token. Please log in again."));
            }
        }

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required to book cinema tickets. Please provide a valid Bearer token."));
        }

        try {
            BookingResponse response = bookingService.createBooking(request, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalStateException e) {
            // Concurrent seat conflict / double-booking prevented!
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "Double-Booking Prevented",
                            "message", e.getMessage(),
                            "status", 409
                    ));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "Double-Booking Prevented",
                            "message", "Seat conflict: One or more selected seats have already been booked by another customer",
                            "status", 409
                    ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "status", 400));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Booking failed", "message", e.getMessage() != null ? e.getMessage() : "Internal server error", "status", 500));
        }
    }

    @GetMapping("/shows/{showId}/available-seats")
    public ResponseEntity<?> getAvailableSeats(@PathVariable Long showId) {
        return ResponseEntity.ok(bookingService.getAvailableSeats(showId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBookingById(@PathVariable Long id) {
        return bookingService.getBookingById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/reference/{reference}")
    public ResponseEntity<Booking> getBookingByReference(@PathVariable String reference) {
        return bookingService.getBookingByReference(reference)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public List<Booking> getUserBookings(@PathVariable Long userId) {
        return bookingService.getUserBookings(userId);
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String gatewayUserId,
            @RequestParam(required = false) Long userId) {

        Long effectiveUserId = userId;
        if (gatewayUserId != null && !gatewayUserId.isBlank()) {
            try {
                effectiveUserId = Long.parseLong(gatewayUserId);
            } catch (NumberFormatException ignored) {}
        }

        if (effectiveUserId == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtValidator.validateToken(token)) {
                effectiveUserId = jwtValidator.extractUserId(token);
            }
        }

        if (effectiveUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User authentication required"));
        }

        try {
            Booking cancelled = bookingService.cancelBooking(id, effectiveUserId);
            return ResponseEntity.ok(cancelled);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
