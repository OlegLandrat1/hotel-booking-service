package com.example.hotel.hotel.room;

import com.example.hotel.hotel.hotel.Hotel;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Тесты счётчика бронирований номера.
 */
final class RoomTest {

    @Test
    @DisplayName("booking did not increase the counter")
    void countsBooking() {
        final Room room = new Room(new Hotel("Dune", "Coast 1"), "101");
        room.book();
        MatcherAssert.assertThat(
                "booking did not increase times",
                room.times(),
                Matchers.equalTo(1)
        );
    }

    @Test
    @DisplayName("cancelling a booking did not decrease the counter")
    void uncountsBooking() {
        final Room room = new Room(new Hotel("Dune", "Coast 1"), "101");
        room.book();
        room.unbook();
        MatcherAssert.assertThat(
                "cancelling did not decrease times",
                room.times(),
                Matchers.equalTo(0)
        );
    }

    @Test
    @DisplayName("unbooking a room without bookings was not refused")
    void refusesUnbookWithoutBookings() {
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> new Room(new Hotel("Dune", "Coast 1"), "101").unbook()
        );
    }
}