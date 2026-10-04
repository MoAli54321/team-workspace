package com.teamworkspace.backend;

import java.time.Instant;
import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * Erstellt signierte JWTs für angemeldete Benutzer und prüft eingehende Tokens für Spring Security.
 * Die Signatur schützt vor Änderungen; der Token-Inhalt selbst ist lesbar und nicht verschlüsselt.
 */
@Service
public class JwtService implements JwtDecoder {

    private static final String ISSUER = "team-workspace";
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long expirationSeconds;

    public JwtService(
            @Value("${jwt.secret}") String encodedSecret,
            @Value("${jwt.expiration-seconds}") long expirationSeconds) {
        // Die Konfiguration liefert den Schlüssel als Base64-Text; zum Signieren brauchen wir die Bytes.
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET muss gültiges Base64 enthalten.");
        }

        // HS256 benötigt mindestens 256 Bit. Es gibt absichtlich keinen Standardschlüssel.
        if (secret.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET muss mindestens 32 zufällige Bytes enthalten (Base64-kodiert).");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("jwt.expiration-seconds muss größer als 0 sein.");
        }

        SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
        this.encoder = NimbusJwtEncoder
                .withSecretKey(key)
                .algorithm(MacAlgorithm.HS256)
                .build();
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Zusätzlich zur Signatur werden die Gültigkeitszeiten und der Aussteller geprüft.
        // Ein vorhandenes nbf-Feld legt fest, ab welchem Zeitpunkt der Token benutzt werden darf.
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        this.decoder = jwtDecoder;
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(User user) {
        Instant now = Instant.now();

        // sub ist die Benutzer-ID. Teamrollen stehen bewusst nicht im Token:
        // Die Controller lesen Mitgliedschaften aktuell aus der Datenbank, damit Änderungen sofort greifen.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    // Spring Security ruft diese Methode für eingehende Bearer-Tokens auf.
    @Override
    public Jwt decode(String token) {
        return decoder.decode(token);
    }
}
