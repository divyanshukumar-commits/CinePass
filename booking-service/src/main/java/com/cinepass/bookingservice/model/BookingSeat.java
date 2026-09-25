package com.cinepass.bookingservice.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(
    name = "booking_seats",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_booking_show_seat", columnNames = {"show_id", "seat_number"})
    }
)
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    @JsonIgnore
    private Booking booking;

    @Column(name = "show_id")
    private Long showId;

    @Column(nullable = false)
    private String seatNumber;

    @Column(nullable = false)
    private Double price;

    public BookingSeat() {
    }

    public BookingSeat(String seatNumber, Double price) {
        this.seatNumber = seatNumber;
        this.price = price;
    }

    public BookingSeat(Long showId, String seatNumber, Double price) {
        this.showId = showId;
        this.seatNumber = seatNumber;
        this.price = price;
    }

    public BookingSeat(Booking booking, Long showId, String seatNumber, Double price) {
        this.booking = booking;
        this.showId = showId;
        this.seatNumber = seatNumber;
        this.price = price;
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

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }
}
