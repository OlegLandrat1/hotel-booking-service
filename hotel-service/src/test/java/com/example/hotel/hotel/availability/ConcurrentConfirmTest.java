package com.example.hotel.hotel.availability;

import com.example.hotel.hotel.hotel.Hotel;
import com.example.hotel.hotel.hotel.HotelRepository;
import com.example.hotel.hotel.room.Room;
import com.example.hotel.hotel.room.RoomRepository;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Восемь одновременных подтверждений на пересекающиеся даты одного номера.
 * Каждый тест создаёт собственный номер.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
class ConcurrentConfirmTest {

    @Autowired
    private Availability availability;

    @Autowired
    private HotelRepository hotels;

    @Autowired
    private RoomRepository rooms;

    @Test
    void overlappingConfirmsAreNotAllAccepted() throws Exception {
        final Long room = this.rooms.save(
                new Room(this.hotels.save(new Hotel("Dune", "Coast 1")), "R-" + UUID.randomUUID())
        ).id();
        final int threads = 8;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch gate = new CountDownLatch(1);
        final List<Future<Boolean>> outcomes = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String request = "race-" + room + "-" + i;
            outcomes.add(pool.submit(() -> this.attempt(gate, room, request)));
        }
        gate.countDown();
        int accepted = 0;
        for (final Future<Boolean> outcome : outcomes) {
            if (outcome.get(30, TimeUnit.SECONDS)) {
                accepted += 1;
            }
        }
        pool.shutdown();
        MatcherAssert.assertThat(
                "several overlapping confirms were accepted for one room",
                accepted,
                Matchers.equalTo(1)
        );
    }

    private boolean attempt(final CountDownLatch gate, final Long room, final String request)
            throws InterruptedException {
        gate.await();
        try {
            this.availability.confirm(
                    room, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), request
            );
            return true;
        } catch (final IllegalStateException busy) {
            return false;
        }
    }
}