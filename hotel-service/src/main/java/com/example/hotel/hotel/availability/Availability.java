package com.example.hotel.hotel.availability;

import java.time.LocalDate;

/**
 * Занятость номера на период, которую подтверждает и снимает Booking Service.
 */
public interface Availability {

    /**
     * Занимает номер на период. Повтор с тем же запросом возвращает прежнюю блокировку.
     * @param room Идентификатор номера
     * @param arrival Дата заезда
     * @param departure Дата выезда
     * @param request Идентификатор запроса
     * @return Идентификатор блокировки
     */
    Long confirm(Long room, LocalDate arrival, LocalDate departure, String request);

    /**
     * Снимает блокировку запроса. Повтор безопасен: отсутствующая блокировка ничего не меняет.
     * @param room Идентификатор номера
     * @param request Идентификатор запроса
     */
    void release(Long room, String request);
}