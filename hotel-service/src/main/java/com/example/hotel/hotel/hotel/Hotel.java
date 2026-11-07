package com.example.hotel.hotel.hotel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Гостиница, в которой находятся номера.
 */
@Entity
@Table(name = "hotels")
public final class Hotel {

    /**
     * Идентификатор, назначает база.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Название гостиницы.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Адрес гостиницы.
     */
    @Column(nullable = false)
    private String address;

    /**
     * Конструктор для JPA.
     */
    protected Hotel() {
    }

    /**
     * Создаёт гостиницу.
     * @param title Название
     * @param place Адрес
     */
    public Hotel(final String title, final String place) {
        this.name = title;
        this.address = place;
    }

    public Long id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public String address() {
        return this.address;
    }
}