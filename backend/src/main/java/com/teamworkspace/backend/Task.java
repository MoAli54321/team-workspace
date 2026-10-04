package com.teamworkspace.backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Eine Aufgabe gehört zu einem Projekt; dessen Team bestimmt, wer sie lesen und bearbeiten darf. */
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    // Das Frontend verwendet TODO, IN_PROGRESS und DONE; die API prüft diese Werte vor dem Speichern.
    private String status;

    // Das Frontend verwendet LOW, MEDIUM und HIGH; die API akzeptiert nur diese drei Werte.
    private String priority;

    // Initialwert beim Erzeugen des Java-Objekts; beim Laden ersetzt JPA ihn durch den DB-Wert.
    private LocalDateTime createdAt = LocalDateTime.now();

    // Bleibt für bestehende Tasks zunächst nullable; neue Tasks werden über ein Projekt angelegt.
    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    public Task() {
    }

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

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }
}
