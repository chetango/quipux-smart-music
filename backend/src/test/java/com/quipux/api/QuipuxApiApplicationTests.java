package com.quipux.api;

import com.quipux.api.repository.PlayListRepository;
import com.quipux.api.repository.SongRepository;
import com.quipux.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QuipuxApiApplicationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlayListRepository playListRepository;

    @Autowired
    private SongRepository songRepository;

    @Test
    void contextLoads() {
        // Verifica que el contexto de Spring Boot arranca correctamente
    }

    @Test
    void precargadoUserExists() {
        // Verifica que data.sql insertó el usuario precargado
        assertThat(userRepository.findByUsername("admin")).isPresent();
    }

    @Test
    void schemaCreatedCorrectly() {
        // Verifica que las tres tablas existen y están operativas
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(playListRepository.count()).isZero();
        assertThat(songRepository.count()).isZero();
    }
}
