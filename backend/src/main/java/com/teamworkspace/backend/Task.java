package com.teamworkspace.backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// JPA bildet diese Klasse auf die Datenbanktabelle tasks ab.
@Entity
@Table(name = "tasks")
public class Task {

    // Primärschlüssel: Die Datenbank erzeugt die ID beim Einfügen einer neuen Aufgabe.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    // Das Frontend verwendet TODO, IN_PROGRESS und DONE; hier wird freier Text gespeichert.
    private String status;

    // Das Frontend verwendet LOW, MEDIUM und HIGH; auch dieses Feld ist ein String.
    private String priority;

    // Initialwert beim Erzeugen des Java-Objekts; beim Laden ersetzt JPA ihn durch den DB-Wert.
    private LocalDateTime createdAt = LocalDateTime.now();

    // JPA benötigt einen parameterlosen Konstruktor, um Datensätze als Objekte zu laden.
    public Task() {
    }

    // Getter geben Feldwerte zurück; Setter übernehmen Änderungen, etwa aus dem Controller.
    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}