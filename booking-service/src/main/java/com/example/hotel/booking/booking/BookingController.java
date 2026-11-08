package com.example.hotel.booking.booking;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Бронирования пользователя. Все методы только для USER, владелец проверяется в сервисе.
 * Ответ POST содержит status: CANCELLED означает, что Hotel не подтвердил номер.
 */
@RestController
public class BookingController {

    private final BookingService bookings;

    /**
     * @param bookings Сервис бронирования
     */
    public BookingController(final BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/booking")
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.CREATED)
    public Item create(@AuthenticationPrincipal final Jwt jwt, @RequestBody final NewBooking body) {
        return new Item(this.bookings.create(owner(jwt), body));
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasRole('USER')")
    public List<Item> history(@AuthenticationPrincipal final Jwt jwt) {
        return this.bookings.history(owner(jwt)).stream().map(Item::new).toList();
    }

    @GetMapping("/booking/{id}")
    @PreAuthorize("hasRole('USER')")
    public Item find(@AuthenticationPrincipal final Jwt jwt, @PathVariable("id") final Long id) {
        return new Item(this.bookings.find(owner(jwt), id));
    }

    @DeleteMapping("/booking/{id}")
    @PreAuthorize("hasRole('USER')")
    public Item cancel(@AuthenticationPrincipal final Jwt jwt, @PathVariable("id") final Long id) {
        return new Item(this.bookings.cancel(owner(jwt), id));
    }

    private static Long owner(final Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    /**
     * Бронирование для ответа.
     * @param id Идентификатор
     * @param room Номер в Hotel Service
     * @param arrival Дата заезда
     * @param departure Дата выезда
     * @param status Состояние
     */
    public record Item(
            Long id,
            Long room,
            LocalDate arrival,
            LocalDate departure,
            BookingStatus status
    ) {

        /**
         * @param booking Бронирование
         */
        public Item(final Booking booking) {
            this(
                    booking.id(),
                    booking.roomId(),
                    booking.arrival(),
                    booking.departure(),
                    booking.status()
            );
        }
    }
}