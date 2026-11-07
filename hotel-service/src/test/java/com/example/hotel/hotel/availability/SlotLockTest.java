package com.example.hotel.hotel.availability;

import com.example.hotel.hotel.hotel.Hotel;
import com.example.hotel.hotel.room.Room;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

/**
 * Тесты проверки дат блокировки.
 */
final class SlotLockTest {

    @Test
    @DisplayName("lock with departure before arrival was accepted")
    void refusesReversedDates() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new SlotLock(
                        new Room(new Hotel("Dune", "Coast 1"), "101"),
                        LocalDate.of(2026, 11, 10),
                        LocalDate.of(2026, 11, 5),
                        "req-1"
                )
        );
    }
}