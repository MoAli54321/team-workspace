package com.teamworkspace.backend;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nimmt Registrierungs- und Login-Anfragen unter /api/auth entgegen.
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

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {

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

        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        User savedUser = userRepository.save(user);

        return ResponseEntity.ok(savedUser);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {

        if (request.getIdentifier() == null || request.getIdentifier().isBlank()
                || request.getPassword() == null || request.getPassword().isEmpty()) {
            return ResponseEntity
                    .status(401)
                    .body("Invalid username/email or password");
        }

        User user = userRepository.findByEmail(request.getIdentifier())
                .or(() -> userRepository.findByUsername(request.getIdentifier()))
                .orElse(null);

        if (user == null) {
            return ResponseEntity
                    .status(401)
                    .body("Invalid username/email or password");
        }

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
