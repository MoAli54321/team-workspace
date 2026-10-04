package com.teamworkspace.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Zeigt die Teams des angemeldeten Benutzers und verwaltet ihre Mitgliedschaften.
 * Die Benutzer-ID stammt aus dem geprüften JWT; die Rolle wird aus der Datenbank gelesen.
 */
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    public TeamController(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            TaskRepository taskRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
    }

    // Die Übersicht beginnt bei den Mitgliedschaften, damit fremde Teams nicht in der Antwort erscheinen.
    @GetMapping
    @Transactional(readOnly = true)
    public List<TeamSummary> getTeams(@AuthenticationPrincipal Jwt jwt) {
        User user = getAuthenticatedUser(jwt);
        return teamMemberRepository.findByUserId(user.getId()).stream()
                .map(TeamSummary::from)
                .toList();
    }

    // Team und OWNER-Mitgliedschaft bilden eine Einheit: Bei einem Fehler wird beides zurückgerollt.
    @PostMapping
    @Transactional
    public Team createTeam(@RequestBody Team request, @AuthenticationPrincipal Jwt jwt) {
        User user = getAuthenticatedUser(jwt);

        // Nur bearbeitbare Felder übernehmen. ID und Erstellzeit vergibt der Server.
        Team team = new Team();
        team.setName(RequestValidation.requiredText(request.getName(), "Teamname"));
        team.setDescription(RequestValidation.optionalText(request.getDescription(), "Beschreibung"));
        Team savedTeam = teamRepository.save(team);

        TeamMember owner = new TeamMember();
        owner.setUser(user);
        owner.setTeam(savedTeam);
        owner.setRole("OWNER");
        teamMemberRepository.save(owner);

        return savedTeam;
    }

    // Nur der Besitzer darf einen bereits registrierten Benutzer zum Team hinzufügen.
    @PostMapping("/{teamId}/members")
    @Transactional
    public TeamMember addMember(
            @PathVariable Long teamId,
            @RequestBody AddTeamMemberRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        User requestingUser = getAuthenticatedUser(jwt);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Team nicht gefunden"));

        TeamMember requestingMembership = teamMemberRepository
                .findByUserIdAndTeamId(requestingUser.getId(), teamId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Nur der Team-Owner darf Mitglieder hinzufügen"));

        if (!"OWNER".equals(requestingMembership.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Nur der Team-Owner darf Mitglieder hinzufügen");
        }

        String identifier = request == null ? null : request.getIdentifier();
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Benutzername oder E-Mail fehlt");
        }
        // Leerzeichen aus der Eingabe entfernen; E-Mail und Benutzername werden wie beim Login unterstützt.
        String normalizedIdentifier = identifier.trim();

        User newMemberUser = userRepository.findByEmail(normalizedIdentifier)
                .or(() -> userRepository.findByUsername(normalizedIdentifier))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Benutzer nicht gefunden"));

        if (teamMemberRepository.existsByUserIdAndTeamId(newMemberUser.getId(), teamId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Benutzer ist bereits Mitglied dieses Teams");
        }

        TeamMember member = new TeamMember();
        member.setUser(newMemberUser);
        member.setTeam(team);
        // Die Rolle legt das Backend fest. Der aufrufende Client kann sich keine OWNER-Rechte geben.
        member.setRole("MEMBER");
        return teamMemberRepository.save(member);
    }

    // Die Liste enthält nur die benötigten Anzeigedaten: Besitzer zuerst, danach alphabetisch.
    @GetMapping("/{teamId}/members")
    @Transactional(readOnly = true)
    public List<TeamMemberSummary> getMembers(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Jwt jwt) {
        requireMembership(teamId, jwt);
        return teamMemberRepository.findByTeamId(teamId).stream()
                .map(TeamMemberSummary::from)
                .sorted(java.util.Comparator
                        .comparing((TeamMemberSummary member) -> !"OWNER".equals(member.role()))
                        .thenComparing(TeamMemberSummary::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    @Transactional
    public ResponseEntity<Void> removeMember(
            @PathVariable Long teamId,
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {
        TeamMember requester = requireMembership(teamId, jwt);
        if (!"OWNER".equals(requester.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Nur der Team-Owner darf Mitglieder entfernen");
        }

        TeamMember member = teamMemberRepository.findByUserIdAndTeamId(userId, teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mitglied nicht gefunden"));
        // Der Besitzer bleibt erhalten, damit das Team weiterhin verwaltet werden kann.
        if ("OWNER".equals(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Der Team-Owner kann nicht entfernt werden");
        }

        // Entfernt nur diese Mitgliedschaft. Account und Mitgliedschaften in anderen Teams bleiben bestehen.
        teamMemberRepository.delete(member);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{teamId}")
    @Transactional
    public ResponseEntity<Void> deleteTeam(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Jwt jwt) {
        TeamMember requester = requireMembership(teamId, jwt);
        if (!"OWNER".equals(requester.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Nur der Team-Owner darf das Team löschen");
        }

        Team team = requester.getTeam();
        List<Project> projects = projectRepository.findByTeamId(teamId);

        // Fremdschlüssel verlangen diese Reihenfolge: Aufgaben, Projekte, Mitgliedschaften, Team.
        // Die gemeinsame Transaktion verhindert einen teilweise gelöschten Arbeitsbereich.
        for (Project project : projects) {
            taskRepository.deleteByProjectId(project.getId());
        }
        taskRepository.flush();
        projectRepository.deleteAll(projects);
        projectRepository.flush();
        teamMemberRepository.deleteAll(teamMemberRepository.findByTeamId(teamId));
        teamMemberRepository.flush();
        teamRepository.delete(team);

        return ResponseEntity.noContent().build();
    }

    // Mitgliedschaft und Rolle werden für jede Verwaltungsaktion erneut aus der Datenbank gelesen.
    private TeamMember requireMembership(Long teamId, Jwt jwt) {
        User user = getAuthenticatedUser(jwt);
        if (!teamRepository.existsById(teamId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team nicht gefunden");
        }
        return teamMemberRepository.findByUserIdAndTeamId(user.getId(), teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Kein Zugriff auf dieses Team"));
    }

    // Ein gültiger Token muss zu einem weiterhin vorhandenen Benutzer gehören.
    private User getAuthenticatedUser(Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NullPointerException | NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ungültiger Benutzer im Token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Benutzer nicht gefunden"));
        return user;
    }
}
