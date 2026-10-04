package com.teamworkspace.backend;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Datenbankzugriff auf Projekte. Spring Data JPA leitet die Abfragen aus den Methodennamen ab. */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Begrenzt die Projektübersicht auf das ausgewählte Team; die Rechte prüft vorher der Controller.
    List<Project> findByTeamId(Long teamId);
}
