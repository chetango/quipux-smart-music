package com.quipux.api.controller;

import com.quipux.api.dto.request.SongRequest;
import com.quipux.api.dto.response.SongResponse;
import com.quipux.api.service.SongService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lists/{listName}/songs")
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    @PostMapping
    public ResponseEntity<SongResponse> addSong(
            @PathVariable String listName,
            @Valid @RequestBody SongRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(songService.addSong(listName, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSong(
            @PathVariable String listName,
            @PathVariable Long id) {
        songService.deleteSong(listName, id);
        return ResponseEntity.noContent().build();
    }
}
