package com.quipux.api.service;

import com.quipux.api.dto.request.SongRequest;
import com.quipux.api.dto.response.SongResponse;
import com.quipux.api.entity.PlayList;
import com.quipux.api.entity.Song;
import com.quipux.api.exception.ResourceNotFoundException;
import com.quipux.api.repository.PlayListRepository;
import com.quipux.api.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SongService {

    private final PlayListRepository playListRepository;
    private final SongRepository songRepository;

    public SongService(PlayListRepository playListRepository, SongRepository songRepository) {
        this.playListRepository = playListRepository;
        this.songRepository = songRepository;
    }

    @Transactional
    public SongResponse addSong(String listName, SongRequest request) {
        PlayList playList = playListRepository.findByListName(listName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lista no encontrada: " + listName));

        Song song = new Song();
        song.setTitulo(request.titulo());
        song.setArtista(request.artista());
        song.setAlbum(request.album());
        song.setAnno(request.anno());
        song.setGenero(request.genero());
        song.setPlayList(playList);

        Song saved = songRepository.save(song);
        return toResponse(saved);
    }

    @Transactional
    public void deleteSong(String listName, Long songId) {
        PlayList playList = playListRepository.findByListName(listName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lista no encontrada: " + listName));

        Song song = playList.getSongs().stream()
                .filter(s -> s.getId().equals(songId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Canción con id " + songId + " no encontrada en la lista: " + listName));

        playList.getSongs().remove(song);
        playListRepository.save(playList);
    }

    private SongResponse toResponse(Song song) {
        return new SongResponse(
                song.getId(),
                song.getTitulo(),
                song.getArtista(),
                song.getAlbum(),
                song.getAnno(),
                song.getGenero());
    }
}
