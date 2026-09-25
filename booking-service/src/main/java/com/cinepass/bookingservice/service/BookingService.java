package com.cinepass.bookingservice.service;

import com.cinepass.bookingservice.client.ShowServiceClient;
import com.cinepass.bookingservice.dto.BookingRequest;
import com.cinepass.bookingservice.dto.BookingResponse;
import com.cinepass.bookingservice.model.Booking;
import com.cinepass.bookingservice.model.BookingSeat;
import com.cinepass.bookingservice.model.BookingStatus;
import com.cinepass.bookingservice.repository.BookingRepository;
import com.cinepass.bookingservice.repository.BookingSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowServiceClient showServiceClient;

    public BookingService(BookingRepository bookingRepository, BookingSeatRepository bookingSeatRepository, ShowServiceClient showServiceClient) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.showServiceClient = showServiceClient;
    }

    /**
     * Atomically process booking request under concurrent conditions.
     */
    @Transactional
    public BookingResponse createBooking(BookingRequest request, Long userId) {
        if (request.getShowId() == null) {
            throw new IllegalArgumentException("Show ID is required");
        }
        if (request.getSeatNumbers() == null || request.getSeatNumbers().isEmpty()) {
            throw new IllegalArgumentException("At least one seat must be selected");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User identification is required");
        }

        // 1. Prevent duplicate seats within the same request
        java.util.Set<String> uniqueSeats = new java.util.HashSet<>(request.getSeatNumbers());
        if (uniqueSeats.size() != request.getSeatNumbers().size()) {
            throw new IllegalArgumentException("Duplicate seat numbers specified in booking request");
        }

        // 2. Validate Show existence
        Map<String, Object> show = showServiceClient.getShowById(request.getShowId());
        if (show == null || show.isEmpty()) {
            throw new IllegalArgumentException("Show not found with id: " + request.getShowId());
        }

        // 3. Pre-check database for seat conflict
        for (String seatNumber : request.getSeatNumbers()) {
            if (bookingSeatRepository.existsByShowIdAndSeatNumber(request.getShowId(), seatNumber)) {
                throw new IllegalStateException("Seat " + seatNumber + " is already booked for this show");
            }
        }

        // 4. Atomically book seats via Show Service (invokes Pessimistic Row-Level Lock: SELECT ... FOR UPDATE)
        List<Map<String, Object>> bookedSeatsData = showServiceClient.bookSeats(
                request.getShowId(),
                request.getSeatNumbers(),
                userId
        );

        // 5. Compute total amount from confirmed seat pricing
        double totalAmount = 0.0;
        for (Map<String, Object> seatData : bookedSeatsData) {
            Object priceObj = seatData.get("price");
            if (priceObj instanceof Number) {
                totalAmount += ((Number) priceObj).doubleValue();
            }
        }

        // 6. Generate unique booking reference
        String bookingReference = "CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Booking booking = new Booking(
                bookingReference,
                userId,
                request.getShowId(),
                totalAmount,
                BookingStatus.CONFIRMED
        );

        for (Map<String, Object> seatData : bookedSeatsData) {
            String seatNum = (String) seatData.get("seatNumber");
            Object p = seatData.get("price");
            Double seatPrice = (p instanceof Number n) ? n.doubleValue() : 0.0;
            booking.addSeat(new BookingSeat(request.getShowId(), seatNum, seatPrice));
        }

        Booking savedBooking;
        try {
            savedBooking = bookingRepository.save(booking);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalStateException("Seat conflict: Selected seat(s) have already been booked by another customer");
        }

        return new BookingResponse(
                savedBooking.getId(),
                savedBooking.getBookingReference(),
                savedBooking.getUserId(),
                savedBooking.getShowId(),
                savedBooking.getTotalAmount(),
                savedBooking.getStatus(),
                request.getSeatNumbers(),
                savedBooking.getCreatedAt(),
                "Tickets successfully booked! Enjoy your movie."
        );
    }

    public Optional<Booking> getBookingById(Long id) {
        return bookingRepository.findById(id);
    }

    public Optional<Booking> getBookingByReference(String reference) {
        return bookingRepository.findByBookingReference(reference);
    }

    public List<Booking> getUserBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Map<String, Object>> getAvailableSeats(Long showId) {
        return showServiceClient.getSeatsForShow(showId).stream()
                .filter(s -> "AVAILABLE".equals(s.get("status")))
                .collect(Collectors.toList());
    }

    @Transactional
    public Booking cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with id: " + bookingId));

        if (!booking.getUserId().equals(userId)) {
            throw new IllegalStateException("You are not authorized to cancel this booking");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking is already cancelled");
        }

        List<String> seatNumbers = booking.getSeats().stream()
                .map(BookingSeat::getSeatNumber)
                .collect(Collectors.toList());

        // Release seats back to Show Service
        showServiceClient.releaseSeats(booking.getShowId(), seatNumbers, userId);

        // Delete seat allocations so they can be re-booked if needed
        bookingSeatRepository.deleteAll(booking.getSeats());
        booking.getSeats().clear();

        booking.setStatus(BookingStatus.CANCELLED);
        return bookingRepository.save(booking);
    }
}
