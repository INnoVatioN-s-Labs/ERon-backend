package com.toyproject.eron;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EronApplication {

    public static void main(String[] args) {
        SpringApplication.run(EronApplication.class, args);
    }

}
