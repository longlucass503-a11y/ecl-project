package com.bank.ecl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.bank.ecl")
@EnableScheduling
public class EclApplication {

    public static void main(String[] args) {
        SpringApplication.run(EclApplication.class, args);
    }

}
