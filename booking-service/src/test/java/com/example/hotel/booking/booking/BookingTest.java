package com.example.hotel.booking.booking;

import com.example.hotel.booking.user.Role;
import com.example.hotel.booking.user.User;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

/**
 * Переходы состояний бронирования без Spring.
 */
final class BookingTest {

    private static Booking pending() {
        return new Booking(
                new User("ann", "hash", Role.USER),
                1L,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 5),
                "req-1"
        );
    }

    @Test
    void pendingBecomesConfirmed() {
        final Booking booking = pending();
        booking.confirm();
        MatcherAssert.assertThat(
                "confirm did not move PENDING to CONFIRMED",
                booking.status(),
                Matchers.equalTo(BookingStatus.CONFIRMED)
        );
    }

    @Test
    void cancelledBookingCannotBeConfirmed() {
        final Booking booking = pending();
        booking.cancel();
        Assertions.assertThrows(IllegalStateException.class, booking::confirm);
    }

    @Test
    void repeatedCancelKeepsCancelled() {
        final Booking booking = pending();
        booking.cancel();
        booking.cancel();
        MatcherAssert.assertThat(
                "repeated cancel changed the status",
                booking.status(),
                Matchers.equalTo(BookingStatus.CANCELLED)
        );
    }

    @Test
    void roomChangesOnlyWhilePending() {
        final Booking booking = pending();
        booking.confirm();
        Assertions.assertThrows(IllegalStateException.class, () -> booking.retarget(2L));
    }
}