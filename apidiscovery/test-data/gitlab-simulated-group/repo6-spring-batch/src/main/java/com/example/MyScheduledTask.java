package com.example;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class MyScheduledTask {
    @Scheduled(fixedRate = 5000)
    public void performTask() {
        System.out.println("Running batch task...");
    }
}
