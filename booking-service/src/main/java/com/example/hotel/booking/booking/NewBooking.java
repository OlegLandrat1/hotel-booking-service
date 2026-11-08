package com.example.hotel.booking.booking;

import java.time.LocalDate;

/**
 * Запрос на бронирование. При autoSelect номер выбирает сервис, roomId тогда игнорируется.
 * @param requestId Идентификатор запроса клиента
 * @param roomId Номер, если выбирает клиент
 * @param autoSelect Подобрать номер автоматически
 * @param arrival Дата заезда
 * @param departure Дата выезда
 */
public record NewBooking(
        String requestId,
        Long roomId,
        boolean autoSelect,
        LocalDate arrival,
        LocalDate departure
) {

    /**
     * Проверяет поля запроса.
     */
    public NewBooking {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (arrival == null || departure == null || !arrival.isBefore(departure)) {
            throw new IllegalArgumentException("arrival must be before departure");
        }
        if (!autoSelect && roomId == null) {
            throw new IllegalArgumentException("roomId is required when autoSelect is false");
        }
    }
}