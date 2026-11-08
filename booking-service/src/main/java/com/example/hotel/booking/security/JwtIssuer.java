package com.example.hotel.booking.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Выпускает JWT, подписанные RS256 закрытым ключом Booking Service.
 */
@Component
public class JwtIssuer {

    /**
     * Роль служебного токена для вызовов Hotel Service.
     */
    public static final String SERVICE_ROLE = "SERVICE";

    private final RSAPrivateKey key;
    private final String issuer;
    private final Duration ttl;

    /**
     * @param keyFile Закрытый ключ в PKCS#8 (BEGIN PRIVATE KEY)
     * @param issuer Издатель, его проверяет Hotel Service
     * @param ttl Срок жизни токена
     * @throws IOException Файл ключа не читается
     */
    public JwtIssuer(
            @Value("${app.jwt.private-key-location}") final Resource keyFile,
            @Value("${app.jwt.issuer}") final String issuer,
            @Value("${app.jwt.expiration}") final Duration ttl
    ) throws IOException {
        this.key = readKey(keyFile);
        this.issuer = issuer;
        this.ttl = ttl;
    }

    /**
     * Токен для пользователя или служебный.
     * @param subject Субъект (логин или имя сервиса)
     * @param role Роль, попадает в claim role
     * @return Подписанный токен
     */
    public String issueFor(final String subject, final String role) {
        final Instant now = Instant.now();
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(this.issuer)
                .subject(subject)
                .claim("role", role)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(this.ttl)))
                .build();
        final SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            jwt.sign(new RSASSASigner(this.key));
            return jwt.serialize();
        } catch (final JOSEException ex) {
            throw new IllegalStateException("Token cannot be signed", ex);
        }
    }

    /**
     * Служебный токен для вызовов Hotel Service.
     * @return Подписанный токен с ролью SERVICE
     */
    public String issueService() {
        return this.issueFor("booking-service", SERVICE_ROLE);
    }

    private static RSAPrivateKey readKey(final Resource keyFile) throws IOException {
        final String pem = new String(keyFile.getContentAsByteArray(), StandardCharsets.US_ASCII)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (final GeneralSecurityException ex) {
            throw new IllegalStateException("Private key cannot be parsed", ex);
        }
    }
}