package com.cinepass.showservice.dto;

import java.util.List;

public class SeatActionRequest {
    private List<String> seatNumbers;
    private Long userId;

    public SeatActionRequest() {
    }

    public SeatActionRequest(List<String> seatNumbers, Long userId) {
        this.seatNumbers = seatNumbers;
        this.userId = userId;
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
