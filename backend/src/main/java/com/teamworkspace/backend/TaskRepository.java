package com.teamworkspace.backend;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Datenbankzugriff für Task-Objekte mit einer ID vom Typ Long.
 * Spring Data JPA stellt Methoden wie findAll, findById, save und deleteById bereit;
 * dafür ist hier keine eigene Implementierung und kein SQL nötig.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectId(Long projectId);
}
