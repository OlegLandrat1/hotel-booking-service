package com.example.hotel.booking.booking;

import com.example.hotel.booking.booking.BookingLedger.Moved;
import com.example.hotel.booking.hotel.HotelClient;
import com.example.hotel.booking.hotel.HotelClient.HotelFailure;
import com.example.hotel.booking.hotel.HotelClient.RoomUnavailable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Оркестрация бронирования: PENDING → CONFIRMED, либо компенсация в CANCELLED.
 * Сам класс не открывает транзакцию, поэтому HTTP-вызовы не держат соединение с базой.
 */
@Service
public class BookingService {

    private static final Logger LOG = LoggerFactory.getLogger(BookingService.class);
    private static final int MAX_CANDIDATES = 5;

    private final BookingLedger ledger;
    private final HotelClient hotel;

    /**
     * @param ledger Локальные транзакции
     * @param hotel Клиент Hotel Service
     */
    public BookingService(final BookingLedger ledger, final HotelClient hotel) {
        this.ledger = ledger;
        this.hotel = hotel;
    }

    /**
     * Создаёт бронирование и доводит его до CONFIRMED или CANCELLED.
     * Повтор с тем же requestId возвращает уже созданное бронирование.
     * @param userId Пользователь
     * @param cmd Запрос
     * @return Бронирование в конечном состоянии
     */
    public Booking create(final Long userId, final NewBooking cmd) {
        final Optional<Booking> replay = this.ledger.findByRequest(userId, cmd.requestId());
        if (replay.isPresent()) {
            LOG.info("requestId={} already processed, bookingId={}",
                    cmd.requestId(), replay.get().id());
            return replay.get();
        }
        final List<Long> candidates = this.candidates(cmd);
        final Booking pending;
        try {
            pending = this.ledger.createPending(
                    userId, candidates.get(0), cmd.arrival(), cmd.departure(), cmd.requestId()
            );
        } catch (final DataIntegrityViolationException race) {
            LOG.info("requestId={} created concurrently, returning stored booking", cmd.requestId());
            return this.ledger.findByRequest(userId, cmd.requestId()).orElseThrow(() -> race);
        }
        LOG.info("bookingId={} PENDING requestId={} candidates={}",
                pending.id(), cmd.requestId(), candidates);
        return this.confirmOrCompensate(pending.id(), candidates, cmd);
    }

    /**
     * Отмена пользователем. Для CONFIRMED снимает блокировку в Hotel Service.
     * @param userId Пользователь
     * @param id Бронирование
     * @return Бронирование в состоянии CANCELLED
     */
    public Booking cancel(final Long userId, final Long id) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("bookingId", String.valueOf(id))) {
            final Moved moved = this.ledger.cancelByOwner(id, userId);
            LOG.info("{} -> CANCELLED by user", moved.from());
            if (moved.from() == BookingStatus.CONFIRMED) {
                this.releaseQuietly(id, moved.booking().roomId(),
                        holdKey(id, moved.booking().roomId()));
            }
            return moved.booking();
        }
    }

    /**
     * @param userId Пользователь
     * @return История бронирований
     */
    public List<Booking> history(final Long userId) {
        return this.ledger.history(userId);
    }

    /**
     * @param userId Пользователь
     * @param id Бронирование
     * @return Бронирование пользователя
     */
    public Booking find(final Long userId, final Long id) {
        return this.ledger.findOwned(id, userId).orElseThrow(
                () -> new NoSuchElementException(String.format("Booking %d was not found", id))
        );
    }

    private List<Long> candidates(final NewBooking cmd) {
        if (!cmd.autoSelect()) {
            return List.of(cmd.roomId());
        }
        final List<Long> free = this.hotel.recommend(cmd.arrival(), cmd.departure())
                .stream()
                .map(HotelClient.RoomView::id)
                .limit(MAX_CANDIDATES)
                .toList();
        if (free.isEmpty()) {
            throw new IllegalStateException(
                    String.format("No rooms free from %s to %s", cmd.arrival(), cmd.departure())
            );
        }
        return free;
    }

    private Booking confirmOrCompensate(
            final Long id,
            final List<Long> candidates,
            final NewBooking cmd
    ) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("bookingId", String.valueOf(id))) {
            for (int i = 0; i < candidates.size(); i++) {
                final Long room = candidates.get(i);
                if (i > 0) {
                    this.ledger.retarget(id, room);
                    LOG.info("PENDING retargeted to room={}", room);
                }
                final String key = holdKey(id, room);
                try {
                    this.hotel.confirm(room, cmd.arrival(), cmd.departure(), key);
                } catch (final RoomUnavailable busy) {
                    LOG.info("room={} rejected: {}", room, busy.getMessage());
                    if (!cmd.autoSelect()) {
                        break;
                    }
                    continue;
                } catch (final HotelFailure failure) {
                    LOG.error("hotel failed after retries, compensating room={} key={}",
                            room, key, failure);
                    return this.compensate(id, room, key);
                }
                final Booking confirmed = this.ledger.confirm(id);
                LOG.info("PENDING -> CONFIRMED room={} key={}", room, key);
                return confirmed;
            }
            LOG.info("no candidate accepted, PENDING -> CANCELLED");
            return this.ledger.cancel(id).booking();
        }
    }

    private Booking compensate(final Long id, final Long room, final String key) {
        final Booking cancelled = this.ledger.cancel(id).booking();
        LOG.info("PENDING -> CANCELLED, compensation starts");
        this.releaseQuietly(id, room, key);
        return cancelled;
    }

    private void releaseQuietly(final Long id, final Long room, final String key) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("bookingId", String.valueOf(id))) {
            this.hotel.release(room, key);
            LOG.info("compensation: lock released room={} key={}", room, key);
        } catch (final RuntimeException ex) {
            LOG.error("compensation failed: room={} key={} needs manual release",
                    room, key, ex);
        }
    }

    private static String holdKey(final Long id, final Long room) {
        return "booking-" + id + "-room-" + room;
    }

    /**
     * Доводит зависшие PENDING-бронирования до CONFIRMED или CANCELLED.
     * Одно сбойное бронирование не останавливает остальные.
     * @param before Порог по времени создания
     */
    public void recoverStale(final Instant before) {
        for (final Booking stale : this.ledger.stalePending(before)) {
            try {
                this.recover(stale);
            } catch (final RuntimeException ex) {
                LOG.error("stale recovery failed bookingId={}", stale.id(), ex);
            }
        }
    }

    private void recover(final Booking stale) {
        final Long id = stale.id();
        final Long room = stale.roomId();
        final String key = holdKey(id, room);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("bookingId", String.valueOf(id))) {
            LOG.info("stale PENDING found, retrying confirm room={} key={}", room, key);
            try {
                this.hotel.confirm(room, stale.arrival(), stale.departure(), key);
            } catch (final RoomUnavailable busy) {
                LOG.info("hotel refused stale PENDING, PENDING -> CANCELLED");
                this.ledger.cancel(id);
                return;
            } catch (final HotelFailure failure) {
                LOG.error("hotel still unavailable, compensating room={} key={}", room, key, failure);
                this.compensate(id, room, key);
                return;
            }
            this.ledger.confirm(id);
            LOG.info("stale PENDING -> CONFIRMED room={} key={}", room, key);
        }
    }

}