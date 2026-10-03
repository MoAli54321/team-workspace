package com.teamworkspace.backend;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams/{teamId}/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    public ProjectController(
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository) {
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    @GetMapping
    public ResponseEntity<?> getProjects(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());

        if (!teamMemberRepository.existsByUserIdAndTeamId(userId, teamId)) {
            return ResponseEntity.status(403).body("Not a team member");
        }

        List<Project> projects = projectRepository.findByTeamId(teamId);
        return ResponseEntity.ok(projects);
    }

    @PostMapping
    public ResponseEntity<?> createProject(
            @PathVariable Long teamId,
            @RequestBody Project project,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());

        if (!teamMemberRepository.existsByUserIdAndTeamId(userId, teamId)) {
            return ResponseEntity.status(403).body("Not a team member");
        }

        Team team = teamRepository.findById(teamId).orElse(null);

        if (team == null) {
            return ResponseEntity.notFound().build();
        }

        project.setTeam(team);
        return ResponseEntity.ok(projectRepository.save(project));
    }
}
