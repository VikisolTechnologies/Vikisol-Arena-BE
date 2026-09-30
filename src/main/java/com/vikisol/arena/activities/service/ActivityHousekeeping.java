package com.vikisol.arena.activities.service;

import com.vikisol.arena.activities.repository.ActivityEmergencyContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

// Flow §3 (Trekking): emergency contacts are deleted after the trek - once it has ended, or a day
// after the start when it has no end time.
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityHousekeeping {

    private final ActivityEmergencyContactRepository emergencyContactRepository;

    @Scheduled(fixedDelayString = "${app.housekeeping.interval-ms:900000}")
    @Transactional
    public void deleteFinishedEmergencyContacts() {
        Instant now = Instant.now();
        int deleted = emergencyContactRepository.deleteFinished(now, now.minus(Duration.ofDays(1)));
        if (deleted > 0) log.info("Deleted {} emergency contact(s) for finished activities", deleted);
    }
}
