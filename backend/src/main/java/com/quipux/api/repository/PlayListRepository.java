package com.quipux.api.repository;

import com.quipux.api.entity.PlayList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayListRepository extends JpaRepository<PlayList, Long> {

    Optional<PlayList> findByListName(String listName);

    boolean existsByListName(String listName);
}
