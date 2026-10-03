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

/** Erstellt JWTs beim Login und prüft sie für Spring Security bei API-Anfragen. */
@Service
public class JwtService implements JwtDecoder {

    private static final String ISSUER = "team-workspace";
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long expirationSeconds;

    public JwtService(
            @Value("${jwt.secret}") String encodedSecret,
            @Value("${jwt.expiration-seconds}") long expirationSeconds) {
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
        // Prüft neben der Signatur auch Ablaufzeit, ggf. nbf und den erwarteten Aussteller.
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        this.decoder = jwtDecoder;
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(User user) {
        Instant now = Instant.now();

        // sub identifiziert den Benutzer. Ein JWT enthält weder Passwort noch Passwort-Hash.
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

    @Override
    public Jwt decode(String token) {
        return decoder.decode(token);
    }
}
