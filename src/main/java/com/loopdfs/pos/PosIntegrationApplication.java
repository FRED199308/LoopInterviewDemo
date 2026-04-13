package com.loopdfs.pos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class PosIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PosIntegrationApplication.class, args);
    }
}
