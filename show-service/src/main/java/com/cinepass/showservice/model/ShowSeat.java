package com.cinepass.showservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "show_seats",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_show_seat", columnNames = {"show_id", "seat_number"})
    }
)
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber; // e.g. "A1", "A2", "B1"

    @Column(name = "seat_tier", nullable = false)
    private String seatTier; // "STANDARD", "PREMIUM", "VIP"

    @Column(nullable = false)
    private Double price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    private Long lockedByUserId;

    private LocalDateTime lockTimestamp;

    public ShowSeat() {
    }

    public ShowSeat(Long showId, String seatNumber, String seatTier, Double price, SeatStatus status) {
        this.showId = showId;
        this.seatNumber = seatNumber;
        this.seatTier = seatTier;
        this.price = price;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShowId() {
        return showId;
    }

    public void setShowId(Long showId) {
        this.showId = showId;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public String getSeatTier() {
        return seatTier;
    }

    public void setSeatTier(String seatTier) {
        this.seatTier = seatTier;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void setStatus(SeatStatus status) {
        this.status = status;
    }

    public Long getLockedByUserId() {
        return lockedByUserId;
    }

    public void setLockedByUserId(Long lockedByUserId) {
        this.lockedByUserId = lockedByUserId;
    }

    public LocalDateTime getLockTimestamp() {
        return lockTimestamp;
    }

    public void setLockTimestamp(LocalDateTime lockTimestamp) {
        this.lockTimestamp = lockTimestamp;
    }
}
