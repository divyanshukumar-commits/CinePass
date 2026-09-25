package com.cinepass.showservice.controller;

import com.cinepass.showservice.model.Screen;
import com.cinepass.showservice.repository.ScreenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/screens")
public class ScreenController {

    private final ScreenRepository screenRepository;

    public ScreenController(ScreenRepository screenRepository) {
        this.screenRepository = screenRepository;
    }

    @PostMapping
    public ResponseEntity<Screen> createScreen(@RequestBody Screen screen) {
        Screen saved = screenRepository.save(screen);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<Screen> getAllScreens() {
        return screenRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Screen> getScreenById(@PathVariable Long id) {
        return screenRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
