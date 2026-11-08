package com.example.hotel.hotel.security;

import com.example.hotel.hotel.hotel.Hotel;
import com.example.hotel.hotel.hotel.HotelRepository;
import com.example.hotel.hotel.room.Room;
import com.example.hotel.hotel.room.RoomRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Настоящая проверка подписи: токен подписывается реальным ключом и проверяется декодером Hotel.
 * Свойство ключа переопределено на тестовый, потому что основной ключ лежит в classpath основного кода.
 * Если SecurityConfig читает ключ по другому свойству, замените имя здесь.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:test-keys/jwt-public.pem",
        "app.jwt.issuer=hotel-booking-service"
})
@AutoConfigureMockMvc
class SignedTokenTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private HotelRepository hotels;

    @Autowired
    private RoomRepository rooms;

    @Test
    void tokenSignedByTrustedKeyIsAccepted() throws Exception {
        this.mvc.perform(confirm(this.freshRoom(), signed(trusted(), "SERVICE")))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void tokenSignedByForeignKeyIsRejected() throws Exception {
        this.mvc.perform(confirm(this.freshRoom(), signed(foreign(), "SERVICE")))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void userTokenIsForbiddenOnInternalEndpoint() throws Exception {
        this.mvc.perform(confirm(this.freshRoom(), signed(trusted(), "USER")))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    private Long freshRoom() {
        final Hotel hotel = this.hotels.save(new Hotel("Dune", "Coast 1"));
        return this.rooms.save(new Room(hotel, "S-" + UUID.randomUUID())).id();
    }

    private MockHttpServletRequestBuilder confirm(final Long room, final String token) {
        return MockMvcRequestBuilders.post("/api/rooms/{id}/confirm-availability", room)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        String.format(
                                "{\"arrival\":\"2026-12-01\",\"departure\":\"2026-12-05\",\"requestId\":\"%s\"}",
                                UUID.randomUUID()
                        )
                );
    }

    private static RSAPrivateKey trusted() throws Exception {
        final String pem = new ClassPathResource("test-keys/jwt-private.pem")
                .getContentAsString(StandardCharsets.US_ASCII);
        final String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
    }

    private static RSAPrivateKey foreign() throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return (RSAPrivateKey) generator.generateKeyPair().getPrivate();
    }

    private static String signed(final RSAPrivateKey key, final String role) throws Exception {
        final Instant now = Instant.now();
        final SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.RS256),
                new JWTClaimsSet.Builder()
                        .issuer("hotel-booking-service")
                        .subject("booking-service")
                        .claim("role", role)
                        .expirationTime(Date.from(now.plusSeconds(60)))
                        .build()
        );
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}