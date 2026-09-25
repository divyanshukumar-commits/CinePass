package com.cinepass.movieservice.repository;

import com.cinepass.movieservice.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {
    List<Movie> findByGenreIgnoreCase(String genre);
    List<Movie> findByLanguageIgnoreCase(String language);
}
