package com.teamworkspace.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prüft Registrierung, Login und JWT-Schutz über die echte Spring-Security-Filterkette.
 * Das Testprofil nutzt H2; die Testtransaktion wird nach jedem Test zurückgerollt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TaskRepository taskRepository;

    @Value("${jwt.secret}")
    private String secret;

    private User user;

    @BeforeEach
    void createTestUser() {
        user = new User();
        user.setUsername("jwt-user");
        user.setEmail("jwt-user@example.test");
        user.setPasswordHash(passwordEncoder.encode("Test123!"));
        user = userRepository.saveAndFlush(user);
    }

    @ParameterizedTest
    @ValueSource(strings = { "jwt-user", "jwt-user@example.test" })
    void successfulLoginReturnsVerifiableToken(String identifier) throws Exception {
        Instant beforeLogin = Instant.now().minusSeconds(1);

        String response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"Test123!"}
                                """.formatted(identifier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.userId").value(user.getId().intValue()))
                .andExpect(jsonPath("$.username").value("jwt-user"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(response, "$.token");
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("username")).isEqualTo("jwt-user");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("team-workspace");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
        assertThat(jwt.getIssuedAt()).isBetween(beforeLogin, Instant.now());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "passwordHash", "email");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"identifier\":\"jwt-user\",\"password\":\"wrong\"}",
            "{\"identifier\":\"unknown\",\"password\":\"Test123!\"}",
            "{\"identifier\":\"jwt-user\"}",
            "{\"password\":\"Test123!\"}",
            "{\"identifier\":\" \",\"password\":\"Test123!\"}",
            "{\"identifier\":\"jwt-user\",\"password\":\"\"}",
            "{}"
    })
    void invalidCredentialsReturn401WithoutToken(String request) throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid username/email or password"));
    }

    @Test
    void registrationStillWorksWithoutAuthenticationOrCsrfToken() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new-user","email":"new@example.test","password":"Test123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("new-user"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User registeredUser = userRepository.findByUsername("new-user").orElseThrow();
        assertThat(passwordEncoder.matches("Test123!", registeredUser.getPasswordHash())).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/tasks", "POST, /api/tasks", "GET, /api/tasks/1",
            "PUT, /api/tasks/1", "DELETE, /api/tasks/1",
            "GET, /api/projects/1/tasks", "POST, /api/projects/1/tasks"
    })
    void taskRequestsWithoutTokenReturn401(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Unauthorized task","status":"TODO","priority":"MEDIUM"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")));
        assertThat(taskRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = { "malformed", "tampered", "expired", "future", "issuer", "wrong-key", "unsigned" })
    void invalidTokensCannotReadTasks(String kind) throws Exception {
        String token;
        if (kind.equals("malformed")) {
            token = "not-a-jwt";
        } else {
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(kind.equals("issuer") ? "another-issuer" : "team-workspace")
                    .subject(user.getId().toString())
                    .claim("username", "jwt-user")
                    .issuedAt(now.minusSeconds(600))
                    .notBefore(kind.equals("future") ? now.plusSeconds(300) : now.minusSeconds(600))
                    .expiresAt(kind.equals("expired") ? now.minusSeconds(300) : now.plusSeconds(3600))
                    .build();
            byte[] key = Base64.getDecoder().decode(secret);
            if (kind.equals("wrong-key")) {
                key[0] ^= 1;
            }
            token = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
                    .algorithm(MacAlgorithm.HS256).build()
                    .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims))
                    .getTokenValue();
            if (kind.equals("tampered")) {
                int signatureStart = token.lastIndexOf('.') + 1;
                char changed = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
                token = token.substring(0, signatureStart) + changed + token.substring(signatureStart + 1);
            } else if (kind.equals("unsigned")) {
                String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                        "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                token = header + "." + token.split("\\.")[1] + ".";
            }
        }

        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")));
    }

    @Test
    void loginTokenAllowsAccessToProtectedApi() throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"jwt-user","password":"Test123!"}
                                """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.<String>read(response, "$.token");

        mvc.perform(get("/api/teams").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void healthCheckRemainsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
