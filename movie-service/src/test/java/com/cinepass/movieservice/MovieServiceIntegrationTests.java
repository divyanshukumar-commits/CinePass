package com.cinepass.movieservice;

import com.cinepass.movieservice.controller.MovieController;
import com.cinepass.movieservice.model.Movie;
import com.cinepass.movieservice.repository.MovieRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MovieServiceIntegrationTests {

    @Autowired
    private MovieController movieController;

    @Autowired
    private MovieRepository movieRepository;

    @Test
    @DisplayName("Movie full CRUD lifecycle: create, read, update, delete, and filter by genre/language")
    void testMovieCrudLifecycle() {
        // 1. Create Movie
        Movie movie = new Movie(
                "Inception - Test Edition",
                "Sci-Fi",
                "English",
                148,
                "A thief who steals corporate secrets through the use of dream-sharing technology."
        );
        ResponseEntity<Movie> createResp = movieController.addMovie(movie);
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        assertNotNull(createResp.getBody());
        Long movieId = createResp.getBody().getId();
        assertNotNull(movieId);

        // 2. Read Movie by ID
        ResponseEntity<Movie> getResp = movieController.getMovieById(movieId);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertEquals("Inception - Test Edition", getResp.getBody().getTitle());
        assertEquals("Sci-Fi", getResp.getBody().getGenre());

        // 3. Filter by genre
        List<Movie> sciFiMovies = movieController.getAllMovies("Sci-Fi", null);
        assertFalse(sciFiMovies.isEmpty());
        assertTrue(sciFiMovies.stream().anyMatch(m -> m.getId().equals(movieId)));

        // 4. Update Movie
        Movie updateInfo = new Movie(
                "Inception - IMAX Remaster",
                "Sci-Fi/Action",
                "English",
                150,
                "Remastered for IMAX screens."
        );
        ResponseEntity<Movie> updateResp = movieController.updateMovie(movieId, updateInfo);
        assertEquals(HttpStatus.OK, updateResp.getStatusCode());
        assertEquals("Inception - IMAX Remaster", updateResp.getBody().getTitle());
        assertEquals("Sci-Fi/Action", updateResp.getBody().getGenre());
        assertEquals(150, updateResp.getBody().getDuration());

        // 5. Delete Movie
        ResponseEntity<String> deleteResp = movieController.deleteMovie(movieId);
        assertEquals(HttpStatus.OK, deleteResp.getStatusCode());
        assertEquals("Movie deleted successfully", deleteResp.getBody());

        // Verify gone
        ResponseEntity<Movie> notFoundResp = movieController.getMovieById(movieId);
        assertEquals(HttpStatus.NOT_FOUND, notFoundResp.getStatusCode());

        // Verify deleting non-existent movie returns 404
        ResponseEntity<String> deleteNonExistent = movieController.deleteMovie(movieId);
        assertEquals(HttpStatus.NOT_FOUND, deleteNonExistent.getStatusCode());
    }
}
