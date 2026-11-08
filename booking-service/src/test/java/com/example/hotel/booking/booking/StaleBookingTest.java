package com.example.hotel.booking.booking;

import com.example.hotel.booking.hotel.HotelClient;
import com.example.hotel.booking.user.Role;
import com.example.hotel.booking.user.User;
import com.example.hotel.booking.user.UserRepository;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Восстановление зависшего PENDING. Каждый тест создаёт собственное бронирование.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "app.bootstrap-admin.password=test-admin"})
class StaleBookingTest {

    @Autowired
    private BookingService service;

    @Autowired
    private BookingLedger ledger;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private UserRepository users;

    @MockitoBean
    private HotelClient hotel;

    @Test
    void staleBookingIsCancelledWhenHotelRefuses() {
        final Long id = this.pending();
        Mockito.doThrow(new HotelClient.RoomUnavailable("busy"))
                .when(this.hotel)
                .confirm(Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.anyString());
        this.service.recoverStale(Instant.now().plusSeconds(1));
        MatcherAssert.assertThat(
                "stale booking stayed PENDING after the hotel refused it",
                this.status(id),
                Matchers.equalTo(BookingStatus.CANCELLED)
        );
    }

    @Test
    void staleBookingIsConfirmedWhenHotelAccepts() {
        final Long id = this.pending();
        this.service.recoverStale(Instant.now().plusSeconds(1));
        MatcherAssert.assertThat(
                "stale booking was not confirmed after the hotel accepted it",
                this.status(id),
                Matchers.equalTo(BookingStatus.CONFIRMED)
        );
    }

    private Long pending() {
        final User owner = this.users.save(
                new User("stale-" + UUID.randomUUID(), "hash", Role.USER)
        );
        return this.ledger.createPending(
                owner.id(),
                7L,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 5),
                UUID.randomUUID().toString()
        ).id();
    }

    private BookingStatus status(final Long id) {
        return this.bookings.findById(id).orElseThrow().status();
    }
}