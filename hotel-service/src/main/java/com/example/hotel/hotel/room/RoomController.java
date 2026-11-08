package com.example.hotel.hotel.room;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Номера гостиниц: добавление администратором и рекомендации по загруженности.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomCatalog catalog;

    public RoomController(final RoomCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/recommend")
    @PreAuthorize("hasAnyRole('USER','ADMIN','SERVICE')")
    public List<Item> recommend(
            @RequestParam("arrival") final LocalDate arrival,
            @RequestParam("departure") final LocalDate departure
    ) {
        return this.catalog.free(arrival, departure).stream().map(Item::new).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Item add(@RequestBody final Form body) {
        return new Item(this.catalog.add(body.hotel(), body.number()));
    }

    /**
     * Данные номера для ответа.
     * @param id Идентификатор
     * @param number Номер комнаты
     * @param times Число подтверждённых бронирований
     */
    public record Item(Long id, String number, int times) {

        /**
         * Строит ответ из номера.
         * @param room Номер
         */
        public Item(final Room room) {
            this(room.id(), room.number(), room.times());
        }
    }

    /**
     * Запрос на добавление номера.
     * @param hotel Идентификатор гостиницы
     * @param number Номер комнаты
     */
    public record Form(Long hotel, String number) {
    }
}