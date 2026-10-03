package com.teamworkspace.backend;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public TeamController(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Team> getTeams(@AuthenticationPrincipal Jwt jwt) {
        User user = getAuthenticatedUser(jwt);
        return teamMemberRepository.findByUserId(user.getId()).stream()
                .map(TeamMember::getTeam)
                .toList();
    }

    @PostMapping
    @Transactional
    public Team createTeam(@RequestBody Team team, @AuthenticationPrincipal Jwt jwt) {
        User user = getAuthenticatedUser(jwt);

        Team savedTeam = teamRepository.save(team);

        TeamMember owner = new TeamMember();
        owner.setUser(user);
        owner.setTeam(savedTeam);
        owner.setRole("OWNER");
        teamMemberRepository.save(owner);

        return savedTeam;
    }

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
        member.setRole("MEMBER");
        return teamMemberRepository.save(member);
    }

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
