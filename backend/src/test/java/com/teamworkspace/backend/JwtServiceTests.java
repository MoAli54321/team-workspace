package com.teamworkspace.backend;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Prüft die Tokenprüfung und ihre Konfiguration ohne Datenbank oder gestarteten Webserver. */
class JwtServiceTests {

    // Ausschließlich ein öffentlicher Testschlüssel.
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-only-signing-key-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));

    @Test
    void changingTokenClaimsInvalidatesTheSignature() {
        String token = new JwtService(SECRET, 3600).createToken(testUser());
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String changedPayload = payload.replace("alice", "mallory");
        String tamperedToken = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(changedPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThatThrownBy(() -> decoder(SECRET).decode(tamperedToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void aDifferentSigningKeyCannotVerifyTheToken() {
        String token = new JwtService(SECRET, 3600).createToken(testUser());
        byte[] differentKey = Base64.getDecoder().decode(SECRET);
        differentKey[0] ^= 1;
        String differentSecret = Base64.getEncoder().encodeToString(differentKey);

        assertThatThrownBy(() -> decoder(differentSecret).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenIsRejectedAfterItsExpiry() {
        String token = new JwtService(SECRET, 3600).createToken(testUser());
        NimbusJwtDecoder decoder = decoder(SECRET);
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        // Eine versetzte Uhr prüft den Ablauf sofort, ohne den Test eine Stunde warten zu lassen.
        timestamps.setClock(Clock.offset(Clock.systemUTC(), Duration.ofSeconds(3601)));
        decoder.setJwtValidator(timestamps);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "not base64!", "c2hvcnQ=" })
    void invalidOrShortSecretFailsAtStartup(String secret) {
        assertThatThrownBy(() -> new JwtService(secret, 3600)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, -1 })
    void nonPositiveLifetimeFailsAtStartup(long seconds) {
        assertThatThrownBy(() -> new JwtService(SECRET, seconds)).isInstanceOf(IllegalArgumentException.class);
    }

    private User testUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(user.getUsername()).thenReturn("alice");
        return user;
    }

    private NimbusJwtDecoder decoder(String secret) {
        return NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
