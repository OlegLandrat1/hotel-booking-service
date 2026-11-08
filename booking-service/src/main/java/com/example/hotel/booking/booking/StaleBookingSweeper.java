package com.example.hotel.booking.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Периодически доводит бронирования, зависшие в PENDING после падения процесса.
 */
@Component
public class StaleBookingSweeper {

    private final BookingService service;
    private final Duration staleAfter;

    /**
     * @param service Сервис бронирования
     * @param staleAfter Возраст, после которого PENDING считается зависшим
     */
    public StaleBookingSweeper(
            final BookingService service,
            @Value("${app.recovery.stale-after:PT10M}") final Duration staleAfter
    ) {
        this.service = service;
        this.staleAfter = staleAfter;
    }

    /**
     * Один проход планировщика.
     */
    @Scheduled(fixedDelayString = "${app.recovery.interval:PT1M}")
    public void sweep() {
        this.service.recoverStale(Instant.now().minus(this.staleAfter));
    }
}