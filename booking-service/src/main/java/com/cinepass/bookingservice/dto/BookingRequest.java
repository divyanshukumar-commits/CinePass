package com.cinepass.bookingservice.dto;

import java.util.List;

public class BookingRequest {
    private Long showId;
    private List<String> seatNumbers;
    private Long userId; // May be provided in request or extracted from JWT

    public BookingRequest() {
    }

    public BookingRequest(Long showId, List<String> seatNumbers, Long userId) {
        this.showId = showId;
        this.seatNumbers = seatNumbers;
        this.userId = userId;
    }

    public Long getShowId() {
        return showId;
    }

    public void setShowId(Long showId) {
        this.showId = showId;
    }

    public List<String> getSeatNumbers() {
        return seatNumbers;
    }

    public void setSeatNumbers(List<String> seatNumbers) {
        this.seatNumbers = seatNumbers;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
