package com.example.hotel.booking.booking;

import com.example.hotel.booking.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Бронирование номера на период. Переходы состояний описаны здесь, без Spring,
 * поэтому их можно проверять отдельными тестами.
 */
@Entity
@Table(
        name = "bookings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "request_id"})
)
public class Booking {

    /**
     * Идентификатор, назначает база.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Пользователь, который забронировал номер.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Номер в Hotel Service. Меняется только пока бронирование PENDING (автоподбор).
     */
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /**
     * Дата заезда (start_date).
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate arrival;

    /**
     * Дата выезда (end_date).
     */
    @Column(name = "end_date", nullable = false)
    private LocalDate departure;

    /**
     * Текущее состояние.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    /**
     * Время создания.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Идентификатор запроса клиента, по нему повтор возвращает то же бронирование.
     */
    @Column(name = "request_id", nullable = false)
    private String requestId;

    /**
     * Конструктор для JPA.
     */
    protected Booking() {
    }

    /**
     * Создаёт бронирование в состоянии PENDING.
     * @param owner Пользователь
     * @param room Номер
     * @param arrival Дата заезда
     * @param departure Дата выезда
     * @param requestId Идентификатор запроса клиента
     */
    public Booking(
            final User owner,
            final Long room,
            final LocalDate arrival,
            final LocalDate departure,
            final String requestId
    ) {
        if (!arrival.isBefore(departure)) {
            throw new IllegalArgumentException(
                    String.format("Booking must start before it ends, got %s and %s", arrival, departure)
            );
        }
        this.user = owner;
        this.roomId = room;
        this.arrival = arrival;
        this.departure = departure;
        this.requestId = requestId;
        this.status = BookingStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /**
     * Переносит PENDING-бронирование на другой номер (автоподбор после отказа).
     * @param room Новый номер
     */
    public void retarget(final Long room) {
        if (this.status != BookingStatus.PENDING) {
            throw new IllegalStateException(
                    String.format("Booking %d is %s, room can change only while PENDING", this.id, this.status)
            );
        }
        this.roomId = room;
    }

    /**
     * PENDING → CONFIRMED. Повтор для CONFIRMED ничего не меняет.
     */
    public void confirm() {
        switch (this.status) {
            case PENDING -> this.status = BookingStatus.CONFIRMED;
            case CONFIRMED -> {
                // повтор подтверждения безопасен
            }
            default -> throw new IllegalStateException(
                    String.format("Booking %d cannot be confirmed from %s", this.id, this.status)
            );
        }
    }

    /**
     * PENDING или CONFIRMED → CANCELLED. Повтор для CANCELLED безопасен.
     */
    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }

    /**
     * @param userId Идентификатор пользователя
     * @return true, если бронирование принадлежит пользователю
     */
    public boolean isOwnedBy(final Long userId) {
        return this.user.id().equals(userId);
    }

    /**
     * @return Идентификатор
     */
    public Long id() {
        return this.id;
    }

    /**
     * @return Номер в Hotel Service
     */
    public Long roomId() {
        return this.roomId;
    }

    /**
     * @return Дата заезда
     */
    public LocalDate arrival() {
        return this.arrival;
    }

    /**
     * @return Дата выезда
     */
    public LocalDate departure() {
        return this.departure;
    }

    /**
     * @return Состояние
     */
    public BookingStatus status() {
        return this.status;
    }

    /**
     * @return Идентификатор запроса клиента
     */
    public String requestId() {
        return this.requestId;
    }

    /**
     * @return Время создания
     */
    public Instant createdAt() {
        return this.createdAt;
    }
}