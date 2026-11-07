package com.example.hotel.hotel.room;

import com.example.hotel.hotel.hotel.Hotel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Номер гостиницы. Занятость по датам определяется блокировками слотов, а не полем available.
 */
@Entity
@Table(name = "rooms")

public final class Room {

    /**
     * Идентификатор, назначает база.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Гостиница, которой принадлежит номер.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    /**
     * Номер комнаты, например 101.
     */
    @Column(nullable = false)
    private String number;

    /**
     * Операционная доступность: ремонт или вывод из эксплуатации.
     */
    @Column(nullable = false)
    private boolean available;

    /**
     * Число подтверждённых бронирований, по нему сортируются рекомендации.
     */
    @Column(name = "times_booked", nullable = false)
    private int times;

    /**
     * Конструктор для JPA.
     */
    protected Room() {
    }

    /**
     * Создаёт доступный номер без бронирований.
     * @param owner Гостиница
     * @param label Номер комнаты
     */
    public Room(final Hotel owner, final String label) {
        this.hotel = owner;
        this.number = label;
        this.available = true;
    }

    /**
     * Учитывает подтверждённое бронирование.
     */
    public void book() {
        this.times += 1;
    }

    /**
     * Снимает учёт бронирования при отмене.
     */
    public void unbook() {
        if (this.times == 0) {
            throw new IllegalStateException(
                    String.format("Room %s has no bookings to cancel", this.number)
            );
        }
        this.times -= 1;
    }

    /**
     * Число подтверждённых бронирований.
     * @return Счётчик
     */
    public int times() {
        return this.times;
    }

    public Long id() {
        return this.id;
    }

    public String number() {
        return this.number;
    }
}