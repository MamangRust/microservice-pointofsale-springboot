package com.seeder.seeder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.seeder.seeder", "com.common"})
@EnableScheduling
public class SeederApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeederApplication.class, args);
    }
}