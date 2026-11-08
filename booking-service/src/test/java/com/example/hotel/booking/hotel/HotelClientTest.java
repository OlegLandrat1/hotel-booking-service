package com.example.hotel.booking.hotel;

import com.example.hotel.booking.security.JwtIssuer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Повторы, тайм-ауты и разбор ошибок Hotel Service. Ответы имитируются, сеть не нужна.
 */
final class HotelClientTest {

    private static final LocalDate ARRIVAL = LocalDate.of(2026, 12, 1);
    private static final LocalDate DEPARTURE = LocalDate.of(2026, 12, 5);

    private MockRestServiceServer server;
    private HotelClient client;

    @BeforeEach
    void setUp() {
        final RestClient.Builder builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(builder).build();
        final HotelProperties props = new HotelProperties(
                "hotel-service",
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                new HotelProperties.Retry(3, Duration.ZERO, 2.0)
        );
        final JwtIssuer tokens = Mockito.mock(JwtIssuer.class);
        Mockito.when(tokens.issueService()).thenReturn("service-token");
        this.client = new HotelClient(builder, props, tokens);
    }

    @Test
    void serverErrorIsRetriedAndThenSucceeds() {
        this.server.expect(times(2), requestTo(Matchers.containsString("/confirm-availability")))
                .andRespond(withServerError());
        this.server.expect(once(), requestTo(Matchers.containsString("/confirm-availability")))
                .andRespond(withSuccess());
        this.client.confirm(7L, ARRIVAL, DEPARTURE, "key-1");
        this.server.verify();
    }

    @Test
    void timeoutIsRetriedAndExhaustedRaisesHotelFailure() {
        this.server.expect(times(3), requestTo(Matchers.containsString("/release")))
                .andRespond(request -> {
                    throw new SocketTimeoutException("read timed out");
                });
        Assertions.assertThrows(
                HotelClient.HotelFailure.class,
                () -> this.client.release(7L, "key-1")
        );
        this.server.verify();
    }

    @Test
    void conflictIsNotRetried() {
        this.server.expect(once(), requestTo(Matchers.containsString("/confirm-availability")))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        Assertions.assertThrows(
                HotelClient.RoomUnavailable.class,
                () -> this.client.confirm(7L, ARRIVAL, DEPARTURE, "key-2")
        );
        this.server.verify();
    }

    @Test
    void persistentServerErrorStopsAfterMaxAttempts() {
        this.server.expect(times(3), requestTo(Matchers.containsString("/confirm-availability")))
                .andRespond(withServerError());
        Assertions.assertThrows(
                HotelClient.HotelFailure.class,
                () -> this.client.confirm(7L, ARRIVAL, DEPARTURE, "key-3")
        );
        this.server.verify();
    }
}