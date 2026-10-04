package com.teamworkspace.backend;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Lädt und löscht Aufgaben innerhalb eines Projekts. */
public interface TaskRepository extends JpaRepository<Task, Long> {

    // Die Liste enthält nur Aufgaben des angefragten Projekts.
    List<Task> findByProjectId(Long projectId);

    // Wird beim Löschen eines Projekts oder Teams innerhalb derselben Transaktion verwendet.
    void deleteByProjectId(Long projectId);
}
