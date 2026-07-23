package com.quipux.api.controller;

import com.quipux.api.dto.request.PlayListRequest;
import com.quipux.api.dto.response.PlayListResponse;
import com.quipux.api.service.PlayListService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/lists")
public class PlayListController {

    private final PlayListService playListService;

    public PlayListController(PlayListService playListService) {
        this.playListService = playListService;
    }

    @GetMapping
    public ResponseEntity<List<PlayListResponse>> findAll() {
        return ResponseEntity.ok(playListService.findAll());
    }

    @PostMapping
    public ResponseEntity<PlayListResponse> create(@Valid @RequestBody PlayListRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(playListService.create(request));
    }

    @GetMapping("/{listName}")
    public ResponseEntity<PlayListResponse> findByListName(@PathVariable String listName) {
        return ResponseEntity.ok(playListService.findByListName(listName));
    }

    @PutMapping("/{listName}")
    public ResponseEntity<PlayListResponse> update(
            @PathVariable String listName,
            @Valid @RequestBody PlayListRequest request) {
        return ResponseEntity.ok(playListService.update(listName, request));
    }

    @DeleteMapping("/{listName}")
    public ResponseEntity<Void> delete(@PathVariable String listName) {
        playListService.delete(listName);
        return ResponseEntity.noContent().build();
    }
}
