package com.teamworkspace.backend;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Liefert die Verbindungen zwischen Benutzern und Teams für Übersichten und Berechtigungsprüfungen. */
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    // Alle Einträge für die Mitgliederliste eines Teams.
    List<TeamMember> findByTeamId(Long teamId);

    // Grundlage für „Meine Teams“: Aus diesen Mitgliedschaften werden die Teams gelesen.
    List<TeamMember> findByUserId(Long userId);

    // Reicht aus, wenn nur der Teamzugriff oder eine doppelte Mitgliedschaft geprüft werden soll.
    boolean existsByUserIdAndTeamId(Long userId, Long teamId);

    // Gibt auch die Rolle zurück. Ohne Mitgliedschaft ist das Optional leer.
    Optional<TeamMember> findByUserIdAndTeamId(Long userId, Long teamId);
}
