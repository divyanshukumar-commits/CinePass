package com.cinepass.bookingservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class ShowServiceClient {

    private final RestClient restClient;

    public ShowServiceClient(@Value("${services.show-service.url:http://localhost:8002}") String showServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(showServiceUrl)
                .build();
    }

    /**
     * Call show-service to atomically lock and book the requested seats.
     * Throws IllegalStateException if seats are already booked/locked by another user.
     */
    public List<Map<String, Object>> bookSeats(Long showId, List<String> seatNumbers, Long userId) {
        Map<String, Object> request = Map.of(
                "seatNumbers", seatNumbers,
                "userId", userId
        );

        return restClient.put()
                .uri("/shows/{id}/seats/book", showId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new IllegalStateException("Seat conflict: Selected seat(s) have already been booked or reserved by another customer");
                })
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    /**
     * Retrieve show details from show-service for show validation.
     */
    public Map<String, Object> getShowById(Long showId) {
        try {
            return restClient.get()
                    .uri("/shows/{id}", showId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Retrieve seat layout and availability from show-service.
     */
    public List<Map<String, Object>> getSeatsForShow(Long showId) {
        try {
            return restClient.get()
                    .uri("/shows/{id}/seats", showId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Release seats in case of cancellation or payment failure.
     */
    public void releaseSeats(Long showId, List<String> seatNumbers, Long userId) {
        Map<String, Object> request = Map.of(
                "seatNumbers", seatNumbers,
                "userId", userId
        );

        try {
            restClient.put()
                    .uri("/shows/{id}/seats/release", showId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
            // Best effort release
        }
    }
}
