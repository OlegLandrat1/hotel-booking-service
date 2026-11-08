package com.example.hotel.booking.hotel;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки вызовов Hotel Service из application.yml (раздел app.hotel).
 * @param serviceName Имя сервиса в Eureka
 * @param connectTimeout Тайм-аут соединения
 * @param readTimeout Тайм-аут ответа
 * @param retry Политика повторов
 */
@ConfigurationProperties("app.hotel")
public record HotelProperties(
        String serviceName,
        Duration connectTimeout,
        Duration readTimeout,
        Retry retry
) {

    /**
     * Повторы с экспоненциальной паузой.
     * @param maxAttempts Всего попыток, включая первую
     * @param initialBackoff Пауза перед второй попыткой
     * @param multiplier Во сколько раз растёт пауза
     */
    public record Retry(int maxAttempts, Duration initialBackoff, double multiplier) {
    }
}