package com.teamworkspace.backend;

import java.nio.charset.StandardCharsets;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verbindet die öffentlichen Anmeldeseiten mit der Benutzerverwaltung.
 * Bei der Registrierung wird das Passwort gehasht; nach erfolgreichem Login wird ein JWT ausgestellt.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // Die Vorabprüfung liefert verständliche Fehlermeldungen bei bereits vergebenen Zugangsdaten.
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {

        request.setUsername(RequestValidation.requiredText(request.getUsername(), "Benutzername"));
        request.setEmail(RequestValidation.requiredText(request.getEmail(), "E-Mail"));
        // BCrypt verarbeitet höchstens 72 Bytes; Passwörter werden weder gekürzt noch getrimmt.
        if (!request.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || request.getPassword() == null || request.getPassword().isBlank()
                || request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.badRequest().body("Invalid registration data");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body("Email already exists");
        }

        User user = new User();

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());

        // Gespeichert wird nur der BCrypt-Hash. Das eingegebene Passwort wird nicht in der Datenbank abgelegt.
        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        User savedUser = userRepository.save(user);

        return ResponseEntity.ok(savedUser);
    }

    // Unbekannte Benutzer und falsche Passwörter erhalten dieselbe Antwort.
    // So verrät der Login nicht, ob ein bestimmter Account existiert.
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {

        if (request.getIdentifier() == null || request.getIdentifier().isBlank()
                || request.getPassword() == null || request.getPassword().isEmpty()
                || request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity
                    .status(401)
                    .body("Invalid username/email or password");
        }

        // Ein einziges Eingabefeld reicht aus: Zuerst wird nach E-Mail, dann nach Benutzername gesucht.
        String identifier = request.getIdentifier().strip();
        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElse(null);

        if (user == null) {
            return ResponseEntity
                    .status(401)
                    .body("Invalid username/email or password");
        }

        // BCrypt vergleicht die Eingabe mit dem Hash; das gespeicherte Passwort wird nicht entschlüsselt.
        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        )) {
            return ResponseEntity
                    .status(401)
                    .body("Invalid username/email or password");
        }

        // Erst nach erfolgreicher Passwortprüfung wird ein signierter Token ausgestellt.
        String token = jwtService.createToken(user);

        // Die Antwort enthält einen Zugangstoken und soll deshalb nicht zwischengespeichert werden.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                java.util.Map.of(
                        "message", "Login successful",
                        "userId", user.getId(),
                        "username", user.getUsername(),
                        "token", token,
                        "tokenType", "Bearer",
                        "expiresIn", jwtService.getExpirationSeconds()
                )
        );
    }
}
