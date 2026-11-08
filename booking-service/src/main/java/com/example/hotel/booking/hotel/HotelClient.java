package com.example.hotel.booking.hotel;

import com.example.hotel.booking.security.JwtIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * Вызовы Hotel Service: тайм-аут, повторы с нарастающей паузой и requestId для идемпотентности.
 * Повтор с тем же requestId Hotel не обрабатывает заново.
 */
@Component
public class HotelClient {

    private static final Logger LOG = LoggerFactory.getLogger(HotelClient.class);

    private final RestClient rest;
    private final HotelProperties.Retry retry;
    private final JwtIssuer tokens;

    /**
     * @param builder Построитель клиента с тайм-аутами
     * @param props Настройки
     * @param tokens Выпуск служебного токена
     */
    public HotelClient(
            final RestClient.Builder builder,
            final HotelProperties props,
            final JwtIssuer tokens
    ) {
        this.rest = builder.baseUrl("lb://" + props.serviceName()).build();
        this.retry = props.retry();
        this.tokens = tokens;
    }

    /**
     * Свободные номера на период, от наименее загруженного.
     * @param arrival Дата заезда
     * @param departure Дата выезда
     * @return Номера в порядке Hotel Service
     */
    public List<RoomView> recommend(final LocalDate arrival, final LocalDate departure) {
        final List<RoomView> rooms = this.call("recommend", "-", () -> this.rest.get()
                .uri(builder -> builder.path("/api/rooms/recommend")
                        .queryParam("arrival", arrival)
                        .queryParam("departure", departure)
                        .build())
                .headers(this::authorize)
                .retrieve()
                .body(new ParameterizedTypeReference<List<RoomView>>() {
                }));
        return rooms == null ? List.of() : rooms;
    }

    /**
     * Занимает номер на период. Повтор с тем же ключом возвращает прежнюю блокировку.
     * @param room Номер
     * @param arrival Дата заезда
     * @param departure Дата выезда
     * @param key Идентификатор запроса для Hotel Service
     */
    public void confirm(
            final Long room,
            final LocalDate arrival,
            final LocalDate departure,
            final String key
    ) {
        this.call("confirm", key, () -> this.rest.post()
                .uri("/api/rooms/{id}/confirm-availability", room)
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Hold(arrival, departure, key))
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * Снимает блокировку. Для неизвестного ключа ничего не делает.
     * @param room Номер
     * @param key Идентификатор запроса для Hotel Service
     */
    public void release(final Long room, final String key) {
        this.call("release", key, () -> this.rest.post()
                .uri("/api/rooms/{id}/release", room)
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Release(key))
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T call(final String operation, final String key, final Supplier<T> request) {
        final int max = this.retry.maxAttempts();
        Duration pause = this.retry.initialBackoff();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            try {
                LOG.info("hotel {} attempt {}/{} key={}", operation, attempt, max, key);
                return request.get();
            } catch (final HttpClientErrorException ex) {
                throw this.rejected(operation, key, ex);
            } catch (final ResourceAccessException | HttpServerErrorException ex) {
                last = ex;
                LOG.warn("hotel {} attempt {}/{} failed key={}: {}",
                        operation, attempt, max, key, ex.getMessage());
                if (attempt < max) {
                    pause(pause);
                    pause = Duration.ofMillis(Math.round(pause.toMillis() * this.retry.multiplier()));
                }
            }
        }
        throw new HotelFailure(operation, last);
    }

    private RuntimeException rejected(
            final String operation,
            final String key,
            final HttpClientErrorException ex
    ) {
        final int code = ex.getStatusCode().value();
        if (code == HttpStatus.CONFLICT.value() || code == HttpStatus.NOT_FOUND.value()) {
            return new RoomUnavailable(
                    String.format("hotel %s key=%s answered %d", operation, key, code)
            );
        }
        return new HotelFailure(operation, ex);
    }

    private void authorize(final HttpHeaders headers) {
        headers.setBearerAuth(this.tokens.issueService());
    }

    private static void pause(final Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new HotelFailure("pause", ex);
        }
    }

    /**
     * Номер из рекомендаций Hotel Service.
     * @param id Идентификатор
     * @param number Номер комнаты
     * @param times Число подтверждённых бронирований
     */
    public record RoomView(Long id, String number, int times) {
    }

    /**
     * Номер занят (409) или не найден (404). Повтор бессмыслен, решение за вызывающим.
     */
    public static class RoomUnavailable extends RuntimeException {

        /**
         * @param message Описание
         */
        public RoomUnavailable(final String message) {
            super(message);
        }
    }

    /**
     * Hotel Service недоступен после всех попыток или вернул другую ошибку.
     */
    public static class HotelFailure extends RuntimeException {

        /**
         * @param operation Операция
         * @param cause Причина
         */
        public HotelFailure(final String operation, final Throwable cause) {
            super("Hotel " + operation + " failed", cause);
        }
    }

    private record Hold(LocalDate arrival, LocalDate departure, String requestId) {
    }

    private record Release(String requestId) {
    }
}