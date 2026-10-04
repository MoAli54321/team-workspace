package com.teamworkspace.backend;

import java.time.LocalDateTime;

/**
 * Antwortobjekt für die Mitgliederliste. Es enthält nur Daten für Anzeige und Mitgliederverwaltung;
 * die vollständige User-Entität und ihre Zugangsdaten werden hier nicht übertragen.
 */
public record TeamMemberSummary(Long userId, String username, String role, LocalDateTime joinedAt) {

    public static TeamMemberSummary from(TeamMember member) {
        return new TeamMemberSummary(member.getUser().getId(), member.getUser().getUsername(),
                member.getRole(), member.getJoinedAt());
    }
}
