package com.fashionagent.schedule;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScheduleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduleApplication.class, args);
    }

    /** 지금 시각. 테스트에서 날짜를 고정하려고 빈으로 둡니다. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
