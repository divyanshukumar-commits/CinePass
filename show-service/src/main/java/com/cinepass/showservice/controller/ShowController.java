package com.cinepass.showservice.controller;

import com.cinepass.showservice.dto.CreateShowRequest;
import com.cinepass.showservice.dto.SeatActionRequest;
import com.cinepass.showservice.model.Show;
import com.cinepass.showservice.model.ShowSeat;
import com.cinepass.showservice.service.ShowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping
    public ResponseEntity<Show> createShow(@RequestBody CreateShowRequest request) {
        Show show = showService.createShow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(show);
    }

    @GetMapping
    public List<Show> getAllShows(@RequestParam(required = false) Long movieId) {
        return showService.getAllShows(movieId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Show> getShowById(@PathVariable Long id) {
        return showService.getShowById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Show> updateShow(@PathVariable Long id, @RequestBody CreateShowRequest request) {
        return showService.updateShow(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteShow(@PathVariable Long id) {
        if (showService.deleteShow(id)) {
            return ResponseEntity.ok("Show deleted successfully");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Show not found with id: " + id);
    }

    @GetMapping("/{id}/seats")
    public List<ShowSeat> getSeatsForShow(@PathVariable Long id) {
        return showService.getSeatsForShow(id);
    }

    @PutMapping("/{id}/seats/lock")
    public ResponseEntity<?> lockSeats(@PathVariable Long id, @RequestBody SeatActionRequest request) {
        try {
            List<ShowSeat> lockedSeats = showService.lockSeats(id, request.getSeatNumbers(), request.getUserId());
            return ResponseEntity.ok(lockedSeats);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/seats/book")
    public ResponseEntity<?> bookSeats(@PathVariable Long id, @RequestBody SeatActionRequest request) {
        try {
            List<ShowSeat> bookedSeats = showService.bookSeats(id, request.getSeatNumbers(), request.getUserId());
            return ResponseEntity.ok(bookedSeats);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/seats/release")
    public ResponseEntity<?> releaseSeats(@PathVariable Long id, @RequestBody SeatActionRequest request) {
        showService.releaseSeats(id, request.getSeatNumbers(), request.getUserId());
        return ResponseEntity.ok(Map.of("message", "Seats released successfully"));
    }
}
