package com.example.hotel.booking.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Доступ к бронированиям по ролям и владельцу. Вызовы Hotel не выполняются: проверки срабатывают раньше.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "app.bootstrap-admin.password=test-admin"})
@AutoConfigureMockMvc
class BookingControllerTest {

    private static final String NEW_BOOKING = "{\"requestId\":\"r-1\",\"roomId\":7,"
            + "\"autoSelect\":false,\"arrival\":\"2026-12-01\",\"departure\":\"2026-12-05\"}";

    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousCannotReadHistory() throws Exception {
        this.mvc.perform(get("/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userReadsEmptyHistory() throws Exception {
        this.mvc.perform(get("/bookings").with(as("USER")))
                .andExpect(content().string("[]"));
    }

    @Test
    void adminCannotBook() throws Exception {
        this.mvc.perform(post("/booking")
                        .with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_BOOKING))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotReadMissingBooking() throws Exception {
        this.mvc.perform(get("/booking/{id}", 999_999L).with(as("USER")))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor as(final String role) {
        return jwt().jwt(token -> token.subject("1"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}