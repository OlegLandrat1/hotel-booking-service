package com.example.hotel.hotel.availability;

import com.example.hotel.hotel.room.Room;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Временная блокировка номера на период проживания, которую создаёт шаг подтверждения.
 * Интервал полуоткрытый: день выезда не мешает заезду в тот же день.
 */
@Entity
@Table(name = "slot_locks")
public final class SlotLock {

    /**
     * Идентификатор, назначает база.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Заблокированный номер.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /**
     * Дата заезда.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate arrival;

    /**
     * Дата выезда.
     */
    @Column(name = "end_date", nullable = false)
    private LocalDate departure;

    /**
     * Идентификатор запроса, по нему подтверждение идемпотентно.
     */
    @Column(name = "request_id", nullable = false, unique = true)
    private String request;

    /**
     * Конструктор для JPA.
     */
    protected SlotLock() {
    }

    /**
     * Создаёт блокировку, если дата заезда раньше даты выезда.
     * @param room Номер
     * @param arrival Дата заезда
     * @param departure Дата выезда
     * @param request Идентификатор запроса
     */
    public SlotLock(
            final Room room,
            final LocalDate arrival,
            final LocalDate departure,
            final String request
    ) {
        if (!arrival.isBefore(departure)) {
            throw new IllegalArgumentException(
                    String.format("Lock must start before it ends, got %s and %s", arrival, departure)
            );
        }
        this.room = room;
        this.arrival = arrival;
        this.departure = departure;
        this.request = request;
    }

    public Long id() {
        return this.id;
    }

    public boolean belongsTo(final Long owner) {
        return Objects.equals(this.room.id(), owner);
    }
}