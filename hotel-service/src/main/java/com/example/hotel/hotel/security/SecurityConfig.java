package com.example.hotel.hotel.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Проверка JWT по публичному ключу Booking Service и перевод claim role в роли Spring Security.
 * Класс не final: @Configuration должен допускать CGLIB-прокси.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Все запросы требуют токена. Права по ролям проверяет @PreAuthorize.
     * @param http Конфигуратор безопасности
     * @return Цепочка фильтров
     * @throws Exception Ошибка настройки
     */
    @Bean
    public SecurityFilterChain chain(final HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesFromClaim()))
                )
                .build();
    }

    /**
     * Декодер проверяет подпись RS256 и срок действия, а также издателя из app.jwt.issuer.
     * @param key Файл публичного ключа
     * @param issuer Ожидаемый издатель токена
     * @return Декодер JWT
     * @throws IOException Файл ключа не читается
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.public-key-location}") final Resource key,
            @Value("${app.jwt.issuer}") final String issuer
    ) throws IOException {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey(key)).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    /**
     * Claim role переводится в authority ROLE_*, поэтому hasRole('ADMIN') работает.
     * @return Конвертер токена в Authentication
     */
    private static JwtAuthenticationConverter rolesFromClaim() {
        final JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * Читает публичный ключ RSA в формате X.509 (PEM, BEGIN PUBLIC KEY).
     * @param key Файл ключа
     * @return Публичный ключ
     * @throws IOException Файл не читается
     */
    private static RSAPublicKey publicKey(final Resource key) throws IOException {
        final String pem = new String(key.getContentAsByteArray(), StandardCharsets.US_ASCII);
        final String body = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Public key cannot be parsed", ex);
        }
    }
}