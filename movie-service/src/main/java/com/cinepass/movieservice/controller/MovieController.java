package com.cinepass.movieservice.controller;

import com.cinepass.movieservice.model.Movie;
import com.cinepass.movieservice.repository.MovieRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/movies")
public class MovieController {

    private final MovieRepository movieRepository;

    public MovieController(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @GetMapping
    public List<Movie> getAllMovies(@RequestParam(required = false) String genre,
                                    @RequestParam(required = false) String language) {
        if (genre != null && !genre.isBlank()) {
            return movieRepository.findByGenreIgnoreCase(genre);
        }
        if (language != null && !language.isBlank()) {
            return movieRepository.findByLanguageIgnoreCase(language);
        }
        return movieRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Movie> getMovieById(@PathVariable Long id) {
        return movieRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Movie> addMovie(@RequestBody Movie movie) {
        Movie saved = movieRepository.save(movie);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Movie> updateMovie(@PathVariable Long id, @RequestBody Movie updatedMovie) {
        return movieRepository.findById(id)
                .map(existingMovie -> {
                    existingMovie.setTitle(updatedMovie.getTitle());
                    existingMovie.setGenre(updatedMovie.getGenre());
                    existingMovie.setLanguage(updatedMovie.getLanguage());
                    existingMovie.setDuration(updatedMovie.getDuration());
                    existingMovie.setDescription(updatedMovie.getDescription());
                    return ResponseEntity.ok(movieRepository.save(existingMovie));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteMovie(@PathVariable Long id) {
        if (movieRepository.existsById(id)) {
            movieRepository.deleteById(id);
            return ResponseEntity.ok("Movie deleted successfully");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Movie not found with id: " + id);
    }
}