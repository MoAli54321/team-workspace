package com.teamworkspace.backend;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// JPA bildet Benutzerobjekte auf Datensätze in der Tabelle users ab.
@Entity
@Table(name = "users")
public class User {

    // Die Datenbank vergibt den Primärschlüssel beim Anlegen eines Benutzers.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Benutzername und E-Mail müssen jeweils eindeutig sein und dürfen in der Datenbank nicht null sein.
    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    // Der Hash wird in der Datenbank gespeichert, aber aus JSON-Antworten ausgeschlossen.
    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    // Initialwert beim Erzeugen des Java-Objekts; JPA lädt für bestehende Benutzer den DB-Wert.
    private LocalDateTime createdAt = LocalDateTime.now();

    // JPA benötigt diesen parameterlosen Konstruktor zum Laden gespeicherter Benutzer.
    public User() {
    }

    // Getter lesen die Werte; Setter werden unter anderem bei der Registrierung verwendet.
    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}