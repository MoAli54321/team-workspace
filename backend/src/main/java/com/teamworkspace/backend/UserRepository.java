package com.teamworkspace.backend;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Sucht Benutzer für Anmeldung und Registrierung. */
public interface UserRepository extends JpaRepository<User, Long> {

    // Optional enthält den gefundenen Benutzer oder ist leer, wenn es keinen Treffer gibt.
    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    // Die existsBy-Methoden prüfen auf einen Treffer und liefern nur true oder false.
    // Der AuthController nutzt sie, um doppelte Registrierungen zu erkennen.
    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
