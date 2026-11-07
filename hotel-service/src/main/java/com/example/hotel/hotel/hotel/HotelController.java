package com.example.hotel.hotel.hotel;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Гостиницы: просмотр пользователями и добавление администратором.
 */
@RestController
@RequestMapping("/api/hotels")
public final class HotelController {

    private final HotelRepository hotels;

    public HotelController(final HotelRepository hotels) {
        this.hotels = hotels;
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<Item> list() {
        return this.hotels.findAll().stream().map(Item::new).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Item add(@RequestBody final Form body) {
        return new Item(this.hotels.save(new Hotel(body.name(), body.address())));
    }

    /**
     * Данные гостиницы для ответа.
     * @param id Идентификатор
     * @param name Название
     * @param address Адрес
     */
    public record Item(Long id, String name, String address) {

        /**
         * Строит ответ из гостиницы.
         * @param hotel Гостиница
         */
        public Item(final Hotel hotel) {
            this(hotel.id(), hotel.name(), hotel.address());
        }
    }

    /**
     * Запрос на добавление гостиницы.
     * @param name Название
     * @param address Адрес
     */
    public record Form(String name, String address) {
    }
}