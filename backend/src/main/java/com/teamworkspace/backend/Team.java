package com.teamworkspace.backend;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// JPA bildet Team-Objekte auf Datensätze in der Tabelle teams ab.
@Entity
@Table(name = "teams")
public class Team {

    // Die Datenbank vergibt die ID beim ersten Speichern.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ein Teamname darf in der Datenbank nicht null sein.
    @Column(nullable = false)
    private String name;

    private String description;

    // Zeitpunkt der Objekterzeugung; JPA schreibt ihn bei späteren Updates nicht erneut.
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // JPA benötigt einen parameterlosen Konstruktor zum Laden gespeicherter Teams.
    public Team() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
