package com.teamworkspace.backend;

import java.time.LocalDateTime;

/**
 * Kompakte Darstellung eines Teams für die persönliche Übersicht.
 * Die Rolle gehört zur Mitgliedschaft und zeigt dem Frontend, welche Aktionen erlaubt sind.
 */
public record TeamSummary(
        Long id,
        String name,
        String description,
        LocalDateTime createdAt,
        String role) {

    public static TeamSummary from(TeamMember membership) {
        Team team = membership.getTeam();
        return new TeamSummary(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getCreatedAt(),
                membership.getRole());
    }
}
