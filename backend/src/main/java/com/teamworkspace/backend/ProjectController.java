package com.teamworkspace.backend;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verwaltet Projekte innerhalb eines Teams. Mitglieder dürfen Projekte ansehen und erstellen;
 * das Löschen eines Projekts samt Aufgaben bleibt dem Teambesitzer vorbehalten.
 */
@RestController
@RequestMapping("/api/teams/{teamId}/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskRepository taskRepository;

    public ProjectController(
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TaskRepository taskRepository) {
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.taskRepository = taskRepository;
    }

    @GetMapping
    public ResponseEntity<?> getProjects(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = getAuthenticatedUserId(jwt);

        if (!teamRepository.existsById(teamId)) {
            return ResponseEntity.notFound().build();
        }

        if (!teamMemberRepository.existsByUserIdAndTeamId(userId, teamId)) {
            return ResponseEntity.status(403).body("Not a team member");
        }

        List<Project> projects = projectRepository.findByTeamId(teamId);
        return ResponseEntity.ok(projects);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createProject(
            @PathVariable Long teamId,
            @RequestBody Project request,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = getAuthenticatedUserId(jwt);

        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }

        if (!teamMemberRepository.existsByUserIdAndTeamId(userId, teamId)) {
            return ResponseEntity.status(403).body("Not a team member");
        }

        // ID und Teamzuordnung werden nicht aus dem Request übernommen.
        Project project = new Project();
        project.setName(RequestValidation.requiredText(request.getName(), "Projektname"));
        project.setDescription(RequestValidation.optionalText(request.getDescription(), "Beschreibung"));
        project.setTeam(team);
        return ResponseEntity.ok(projectRepository.save(project));
    }

    @DeleteMapping("/{projectId}")
    @Transactional
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long teamId,
            @PathVariable Long projectId,
            @AuthenticationPrincipal Jwt jwt) {
        if (!teamRepository.existsById(teamId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team nicht gefunden");
        }
        Long userId = getAuthenticatedUserId(jwt);
        TeamMember membership = teamMemberRepository.findByUserIdAndTeamId(userId, teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Kein Zugriff auf dieses Team"));
        if (!"OWNER".equals(membership.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Nur der Team-Owner darf Projekte löschen");
        }

        // Auch ein Besitzer darf keine Projekt-ID aus einem anderen Team unter dieser URL löschen.
        Project project = projectRepository.findById(projectId)
                .filter(item -> item.getTeam().getId().equals(teamId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Projekt nicht gefunden"));

        // Zuerst werden die abhängigen Aufgaben gelöscht. flush führt diesen Schritt vor dem
        // Projektlöschen in der Datenbank aus; bei einem Fehler wird die gesamte Transaktion zurückgerollt.
        taskRepository.deleteByProjectId(projectId);
        taskRepository.flush();
        projectRepository.delete(project);
        return ResponseEntity.noContent().build();
    }

    private Long getAuthenticatedUserId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NullPointerException | NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ungültiger Benutzer im Token");
        }
    }
}
