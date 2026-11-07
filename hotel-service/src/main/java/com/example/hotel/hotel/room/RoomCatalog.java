package com.example.hotel.hotel.room;

import com.example.hotel.hotel.hotel.HotelRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Каталог номеров: добавление в гостиницу и выборка свободных на даты.
 */
@Service
public final class RoomCatalog {

    private final RoomRepository rooms;
    private final HotelRepository hotels;

    public RoomCatalog(final RoomRepository rooms, final HotelRepository hotels) {
        this.rooms = rooms;
        this.hotels = hotels;
    }

    /**
     * Добавляет номер в гостиницу.
     * @param hotel Идентификатор гостиницы
     * @param number Номер комнаты
     * @return Сохранённый номер
     */
    public Room add(final Long hotel, final String number) {
        return this.rooms.save(
                new Room(
                        this.hotels.findById(hotel).orElseThrow(
                                () -> new NoSuchElementException(
                                        String.format("Hotel %d was not found", hotel)
                                )
                        ),
                        number
                )
        );
    }

    /**
     * Свободные на период номера, от наименее загруженного к наиболее загруженному.
     * @param arrival Дата заезда
     * @param departure Дата выезда
     * @return Номера в порядке times_booked, при равенстве по id
     */
    public List<Room> free(final LocalDate arrival, final LocalDate departure) {
        return this.rooms.findFree(arrival, departure, Sort.by("times", "id"));
    }
}