package com.example.hotel.hotel.availability;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Внутренние эндпойнты подтверждения и освобождения, которые вызывает только Booking Service.
 * Gateway закрывает их снаружи, а SERVICE-токен проверяется в SecurityConfig.
 */
@RestController
@RequestMapping("/api/rooms/{id}")
public final class AvailabilityController {

    private final Availability availability;

    public AvailabilityController(final Availability availability) {
        this.availability = availability;
    }

    @PostMapping("/confirm-availability")
    @PreAuthorize("hasRole('SERVICE')")
    public Held confirm(@PathVariable("id") final Long room, @RequestBody final Hold body) {
        return new Held(
                this.availability.confirm(room, body.arrival(), body.departure(), body.requestId())
        );
    }

    @PostMapping("/release")
    @PreAuthorize("hasRole('SERVICE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable("id") final Long room, @RequestBody final Release body) {
        this.availability.release(room, body.requestId());
    }

    /**
     * Запрос на занятость номера.
     * @param arrival Дата заезда
     * @param departure Дата выезда
     * @param requestId Идентификатор запроса
     */
    public record Hold(LocalDate arrival, LocalDate departure, String requestId) {
    }

    /**
     * Ответ с идентификатором блокировки.
     * @param lockId Идентификатор блокировки
     */
    public record Held(Long lockId) {
    }

    /**
     * Запрос на снятие блокировки.
     * @param requestId Идентификатор запроса
     */
    public record Release(String requestId) {
    }
}