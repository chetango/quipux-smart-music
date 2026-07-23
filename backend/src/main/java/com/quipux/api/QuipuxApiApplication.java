package com.quipux.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class QuipuxApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuipuxApiApplication.class, args);
    }
}
