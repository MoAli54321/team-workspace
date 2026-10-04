package com.teamworkspace.backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Verbindet einen Benutzer mit einem Team und speichert seine Rolle in genau diesem Team. */
@Entity
@Table(
        name = "team_members",
        // Diese Kombination darf nur einmal vorkommen, auch bei gleichzeitigen Anfragen.
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "team_id"})
        }
)
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ein Benutzer kann mehrere Mitgliedschaften haben, jeweils eine pro Team.
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Ein Team kann über diese Verbindung beliebig viele Mitglieder enthalten.
    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    // OWNER verwaltet das Team; MEMBER arbeitet an Projekten und Aufgaben mit.
    @Column(nullable = false)
    private String role;

    private LocalDateTime joinedAt = LocalDateTime.now();

    public TeamMember() {
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
