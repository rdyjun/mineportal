package com.mineportal.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MineportalServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MineportalServerApplication.class, args);
    }

}
