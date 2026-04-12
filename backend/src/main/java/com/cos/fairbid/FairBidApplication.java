package com.cos.fairbid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

@SpringBootApplication
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.cos.fairbid")
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class FairBidApplication {

    public static void main(String[] args) {
        SpringApplication.run(FairBidApplication.class, args);
    }

}
