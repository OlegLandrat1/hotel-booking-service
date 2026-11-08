package com.example.hotel.booking.booking;

import com.example.hotel.booking.user.User;
import com.example.hotel.booking.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Локальные транзакции Booking Service. Каждый метод выполняется в своей транзакции,
 * а HTTP-вызовы в Hotel идут снаружи этих методов.
 */
@Service
@Transactional
public class BookingLedger {

    private final BookingRepository bookings;
    private final UserRepository users;

    /**
     * @param bookings Бронирования
     * @param users Пользователи
     */
    public BookingLedger(final BookingRepository bookings, final UserRepository users) {
        this.bookings = bookings;
        this.users = users;
    }

    /**
     * Фиксирует бронирование в состоянии PENDING.
     * @param userId Пользователь
     * @param room Номер
     * @param arrival Дата заезда
     * @param departure Дата выезда
     * @param requestId Идентификатор запроса клиента
     * @return Сохранённое бронирование
     */
    public Booking createPending(
            final Long userId,
            final Long room,
            final LocalDate arrival,
            final LocalDate departure,
            final String requestId
    ) {
        final User owner = this.users.findById(userId).orElseThrow(
                () -> new NoSuchElementException(String.format("User %d was not found", userId))
        );
        return this.bookings.save(new Booking(owner, room, arrival, departure, requestId));
    }

    /**
     * Переносит PENDING-бронирование на другой номер.
     * @param id Бронирование
     * @param room Новый номер
     */
    public void retarget(final Long id, final Long room) {
        this.locked(id).retarget(room);
    }

    /**
     * PENDING → CONFIRMED.
     * @param id Бронирование
     * @return Подтверждённое бронирование
     */
    public Booking confirm(final Long id) {
        final Booking booking = this.locked(id);
        booking.confirm();
        return booking;
    }

    /**
     * Компенсация: переводит бронирование в CANCELLED из любого состояния.
     * @param id Бронирование
     * @return Прежнее состояние и бронирование
     */
    public Moved cancel(final Long id) {
        return this.move(this.locked(id));
    }

    /**
     * Отмена пользователем. Бронирование в PENDING отменить нельзя: его ещё обрабатывают.
     * @param id Бронирование
     * @param userId Пользователь
     * @return Прежнее состояние и бронирование
     */
    public Moved cancelByOwner(final Long id, final Long userId) {
        final Booking booking = this.locked(id);
        if (!booking.isOwnedBy(userId)) {
            throw new NoSuchElementException(String.format("Booking %d was not found", id));
        }
        if (booking.status() == BookingStatus.PENDING) {
            throw new IllegalStateException(
                    String.format("Booking %d is still being processed", id)
            );
        }
        return this.move(booking);
    }

    /**
     * @param userId Пользователь
     * @param requestId Идентификатор запроса клиента
     * @return Бронирование, если запрос уже обрабатывался
     */
    @Transactional(readOnly = true)
    public Optional<Booking> findByRequest(final Long userId, final String requestId) {
        return this.bookings.findByUserIdAndRequestId(userId, requestId);
    }

    /**
     * @param id Бронирование
     * @param userId Пользователь
     * @return Бронирование пользователя
     */
    @Transactional(readOnly = true)
    public Optional<Booking> findOwned(final Long id, final Long userId) {
        return this.bookings.findByIdAndUserId(id, userId);
    }

    /**
     * @param userId Пользователь
     * @return История, новые первыми
     */
    @Transactional(readOnly = true)
    public List<Booking> history(final Long userId) {
        return this.bookings.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private Moved move(final Booking booking) {
        final BookingStatus from = booking.status();
        booking.cancel();
        return new Moved(booking, from);
    }

    private Booking locked(final Long id) {
        return this.bookings.lockById(id).orElseThrow(
                () -> new NoSuchElementException(String.format("Booking %d was not found", id))
        );
    }

    /**
     * Результат перехода: бронирование и состояние до перехода.
     * @param booking Бронирование
     * @param from Прежнее состояние
     */
    public record Moved(Booking booking, BookingStatus from) {
    }

    /**
     * PENDING-бронирования, созданные раньше порога.
     * @param before Порог по времени создания
     * @return Зависшие бронирования
     */
    @Transactional(readOnly = true)
    public List<Booking> stalePending(final Instant before) {
        return this.bookings.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, before);
    }
}