package com.example.hotel.booking.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Регистрация, вход и администрирование через HTTP. Логины случайные, поэтому тесты не мешают друг другу.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "app.bootstrap-admin.password=test-admin"})
@AutoConfigureMockMvc
@Transactional
class AccountControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void registerIsPublic() throws Exception {
        this.mvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(login(), "secret")))
                .andExpect(status().isCreated());
    }

    @Test
    void registerIssuesToken() throws Exception {
        this.mvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(login(), "secret")))
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void unknownLoginGets401() throws Exception {
        this.mvc.perform(post("/user/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(login(), "secret")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateLoginGets409() throws Exception {
        final String name = login();
        this.mvc.perform(post("/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(name, "secret")));
        this.mvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(name, "secret")))
                .andExpect(status().isConflict());
    }

    @Test
    void userCannotCreateAccount() throws Exception {
        this.mvc.perform(post("/user")
                        .with(as("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(login(), "secret")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateAccount() throws Exception {
        this.mvc.perform(post("/user")
                        .with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(login(), "secret")))
                .andExpect(status().isCreated());
    }

    private static String login() {
        return "user-" + UUID.randomUUID();
    }

    private static String credentials(final String login, final String password) {
        return String.format("{\"login\":\"%s\",\"password\":\"%s\"}", login, password);
    }

    private static RequestPostProcessor as(final String role) {
        return jwt().jwt(token -> token.subject("1"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}