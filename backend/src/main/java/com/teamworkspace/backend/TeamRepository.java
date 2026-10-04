package com.teamworkspace.backend;

import org.springframework.data.jpa.repository.JpaRepository;

/** Nutzt die Standardmethoden von Spring Data JPA zum Speichern und Laden von Teams. */
public interface TeamRepository extends JpaRepository<Team, Long> {
}
