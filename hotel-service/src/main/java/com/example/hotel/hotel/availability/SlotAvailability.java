package com.example.hotel.hotel.availability;

import com.example.hotel.hotel.room.Room;
import com.example.hotel.hotel.room.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.NoSuchElementException;

/**
 * Занимает и освобождает номер. Номер блокируется на время транзакции,
 * поэтому конкурентные запросы на один номер проверяют пересечения по очереди.
 */
@Service
@Transactional
final class SlotAvailability implements Availability {

    private final RoomRepository rooms;
    private final SlotLockRepository locks;

    SlotAvailability(final RoomRepository rooms, final SlotLockRepository locks) {
        this.rooms = rooms;
        this.locks = locks;
    }

    @Override
    public Long confirm(
            final Long room,
            final LocalDate arrival,
            final LocalDate departure,
            final String request
    ) {
        final Room target = this.lock(room);
        return this.locks.findByRequest(request)
                .map(SlotLock::id)
                .orElseGet(() -> this.occupy(target, arrival, departure, request));
    }

    @Override
    public void release(final Long room, final String request) {
        final Room target = this.lock(room);
        this.locks.findByRequest(request)
                .filter(lock -> lock.belongsTo(room))
                .ifPresent(lock -> this.free(target, lock));
    }

    private Room lock(final Long room) {
        return this.rooms.lockById(room)
                .orElseThrow(
                        () -> new NoSuchElementException(
                                String.format("Room %d was not found", room)
                        )
                );
    }

    private Long occupy(
            final Room target,
            final LocalDate arrival,
            final LocalDate departure,
            final String request
    ) {
        final SlotLock lock = new SlotLock(target, arrival, departure, request);
        if (!this.locks.findOverlapping(target, arrival, departure).isEmpty()) {
            throw new IllegalStateException(
                    String.format("Room %d is busy from %s to %s", target.id(), arrival, departure)
            );
        }
        target.book();
        return this.locks.save(lock).id();
    }

    private void free(final Room target, final SlotLock lock) {
        target.unbook();
        this.locks.delete(lock);
    }
}