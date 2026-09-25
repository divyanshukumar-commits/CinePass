package com.cinepass.bookingservice.dto;

import com.cinepass.bookingservice.model.BookingStatus;

import java.time.LocalDateTime;
import java.util.List;

public class BookingResponse {
    private Long id;
    private String bookingReference;
    private Long userId;
    private Long showId;
    private Double totalAmount;
    private BookingStatus status;
    private List<String> seatNumbers;
    private LocalDateTime createdAt;
    private String message;

    public BookingResponse() {
    }

    public BookingResponse(Long id, String bookingReference, Long userId, Long showId,
                           Double totalAmount, BookingStatus status, List<String> seatNumbers,
                           LocalDateTime createdAt, String message) {
        this.id = id;
        this.bookingReference = bookingReference;
        this.userId = userId;
        this.showId = showId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.seatNumbers = seatNumbers;
        this.createdAt = createdAt;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getShowId() {
        return showId;
    }

    public void setShowId(Long showId) {
        this.showId = showId;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public List<String> getSeatNumbers() {
        return seatNumbers;
    }

    public void setSeatNumbers(List<String> seatNumbers) {
        this.seatNumbers = seatNumbers;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
