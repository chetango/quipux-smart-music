package com.quipux.api.service;

import com.quipux.api.dto.request.PlayListRequest;
import com.quipux.api.dto.response.PlayListResponse;
import com.quipux.api.dto.response.SongResponse;
import com.quipux.api.entity.PlayList;
import com.quipux.api.exception.DuplicatePlaylistException;
import com.quipux.api.exception.ResourceNotFoundException;
import com.quipux.api.repository.PlayListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PlayListService {

    private final PlayListRepository playListRepository;

    public PlayListService(PlayListRepository playListRepository) {
        this.playListRepository = playListRepository;
    }

    public List<PlayListResponse> findAll() {
        return playListRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public PlayListResponse findByListName(String listName) {
        return playListRepository.findByListName(listName)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lista no encontrada: " + listName));
    }

    @Transactional
    public PlayListResponse create(PlayListRequest request) {
        if (playListRepository.existsByListName(request.listName())) {
            throw new DuplicatePlaylistException(request.listName());
        }
        PlayList playList = new PlayList();
        playList.setListName(request.listName());
        playList.setDescription(request.description());
        return toResponse(playListRepository.save(playList));
    }

    @Transactional
    public PlayListResponse update(String listName, PlayListRequest request) {
        PlayList playList = playListRepository.findByListName(listName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lista no encontrada: " + listName));

        if (!listName.equals(request.listName())
                && playListRepository.existsByListName(request.listName())) {
            throw new DuplicatePlaylistException(request.listName());
        }

        playList.setListName(request.listName());
        playList.setDescription(request.description());
        return toResponse(playListRepository.save(playList));
    }

    @Transactional
    public void delete(String listName) {
        PlayList playList = playListRepository.findByListName(listName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lista no encontrada: " + listName));
        playListRepository.delete(playList);
    }

    public PlayListResponse toResponse(PlayList playList) {
        List<SongResponse> songs = playList.getSongs().stream()
                .map(s -> new SongResponse(
                        s.getId(), s.getTitulo(), s.getArtista(),
                        s.getAlbum(), s.getAnno(), s.getGenero()))
                .toList();
        return new PlayListResponse(playList.getListName(), playList.getDescription(), songs);
    }
}
