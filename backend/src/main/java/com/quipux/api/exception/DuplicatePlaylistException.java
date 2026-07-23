package com.quipux.api.exception;

public class DuplicatePlaylistException extends RuntimeException {

    public DuplicatePlaylistException(String listName) {
        super("Ya existe una lista con el nombre: " + listName);
    }
}
