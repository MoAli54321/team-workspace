package com.teamworkspace.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prüft die Teamrollen und ob das Entfernen eines Mitglieds dessen Zugriff sofort beendet.
 * Das Testprofil nutzt H2; die Testtransaktion wird nach jedem Test zurückgerollt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeamMemberIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    private User owner;
    private User member;
    private User thirdUser;

    @BeforeEach
    void createUsers() {
        owner = createUser("team-owner", "owner@example.test");
        member = createUser("team-member", "member@example.test");
        thirdUser = createUser("third-user", "third@example.test");
    }

    @ParameterizedTest
    @ValueSource(strings = {"team-member", "member@example.test"})
    void ownerCanAddMemberByUsernameOrEmail(String identifier) throws Exception {
        long teamId = createTeamAsOwner();

        mvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s"}
                                """.formatted(identifier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(member.getId().intValue()))
                .andExpect(jsonPath("$.user.username").value("team-member"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.team.id").value(teamId))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        TeamMember savedMember = teamMemberRepository
                .findByUserIdAndTeamId(member.getId(), teamId)
                .orElseThrow();
        assertThat(savedMember.getRole()).isEqualTo("MEMBER");
    }

    @Test
    void getTeamsReturnsOnlyTeamsOfTheAuthenticatedMember() throws Exception {
        long sharedTeamId = createTeamAsOwner();
        createTeamAs(thirdUser, "Foreign Team");
        addMemberAsOwner(sharedTeamId, "team-member");

        mvc.perform(get("/api/teams")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(sharedTeamId))
                .andExpect(jsonPath("$[0].name").value("Integration Team"))
                .andExpect(jsonPath("$[0].role").value("MEMBER"));
    }

    @Test
    void ownerCanDeleteTeamWithProjectsTasksAndMemberships() throws Exception {
        long teamId = createTeamAsOwner();
        addMemberAsOwner(teamId, member.getUsername());
        Team team = teamRepository.findById(teamId).orElseThrow();

        Project project = new Project();
        project.setName("Temporary project");
        project.setTeam(team);
        project = projectRepository.saveAndFlush(project);

        Task task = new Task();
        task.setTitle("Temporary task");
        task.setProject(project);
        task = taskRepository.saveAndFlush(task);
        Long projectId = project.getId();
        Long taskId = task.getId();

        mvc.perform(delete("/api/teams/" + teamId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());

        // Benutzerkonten sind unabhängig vom gelöschten Arbeitsbereich und bleiben bestehen.
        assertThat(teamRepository.existsById(teamId)).isFalse();
        assertThat(projectRepository.existsById(projectId)).isFalse();
        assertThat(taskRepository.existsById(taskId)).isFalse();
        assertThat(teamMemberRepository.findByTeamId(teamId)).isEmpty();
        assertThat(userRepository.existsById(owner.getId())).isTrue();
        assertThat(userRepository.existsById(member.getId())).isTrue();
    }

    @Test
    void memberCannotDeleteTeam() throws Exception {
        long teamId = createTeamAsOwner();
        addMemberAsOwner(teamId, member.getUsername());

        mvc.perform(delete("/api/teams/" + teamId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());

        assertThat(teamRepository.existsById(teamId)).isTrue();
        assertThat(teamMemberRepository.findByTeamId(teamId)).hasSize(2);
    }

    @Test
    void memberCannotAddAnotherMember() throws Exception {
        long teamId = createTeamAsOwner();
        addMemberAsOwner(teamId, "team-member");

        mvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"third-user"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(teamMemberRepository.existsByUserIdAndTeamId(thirdUser.getId(), teamId)).isFalse();
    }

    @Test
    void duplicateMembershipReturnsConflict() throws Exception {
        long teamId = createTeamAsOwner();
        addMemberAsOwner(teamId, "team-member");

        mvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"team-member"}
                                """))
                .andExpect(status().isConflict());

        assertThat(teamMemberRepository.findByTeamId(teamId)).hasSize(2);
    }

    @Test
    void missingIdentifierReturnsBadRequest() throws Exception {
        long teamId = createTeamAsOwner();

        mvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownUserReturnsNotFound() throws Exception {
        long teamId = createTeamAsOwner();

        mvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"unknown-user"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestWithoutTokenReturnsUnauthorized() throws Exception {
        mvc.perform(post("/api/teams/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"team-member"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void membersCanReadOnlyTheirTeamsRosterWithoutPrivateUserDetails() throws Exception {
        long teamId = createTeamAsOwner();
        addMemberAsOwner(teamId, member.getUsername());
        createTeamAs(thirdUser, "Other Team");

        mvc.perform(get("/api/teams/" + teamId + "/members").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(owner.getId().intValue()))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].username").value(member.getUsername()))
                .andExpect(jsonPath("$[1].joinedAt").exists())
                .andExpect(jsonPath("$[1].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[1].email").doesNotExist())
                .andExpect(jsonPath("$[1].user").doesNotExist());
    }

    @Test
    void outsiderCannotReadOrChangeMembers() throws Exception {
        long teamId = createTeamAsOwner();
        addMemberAsOwner(teamId, member.getUsername());
        mvc.perform(get("/api/teams/" + teamId + "/members").header("Authorization", bearer(thirdUser)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/teams/" + teamId + "/members/" + member.getId())
                        .header("Authorization", bearer(thirdUser)))
                .andExpect(status().isForbidden());
        assertThat(teamMemberRepository.existsByUserIdAndTeamId(member.getId(), teamId)).isTrue();
    }

    @Test
    void ownerCanRemoveMemberAndExistingTokenImmediatelyLosesTeamAccess() throws Exception {
        long teamId = createTeamAsOwner();
        addMemberAsOwner(teamId, member.getUsername());
        Project project = new Project();
        project.setName("Protected project");
        project.setTeam(teamMemberRepository.findByUserIdAndTeamId(owner.getId(), teamId).orElseThrow().getTeam());
        project = projectRepository.saveAndFlush(project);
        Task task = new Task();
        task.setTitle("Protected task");
        task.setProject(project);
        task = taskRepository.saveAndFlush(task);
        String existingToken = bearer(member);

        mvc.perform(delete("/api/teams/" + teamId + "/members/" + member.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        teamMemberRepository.flush();

        assertThat(teamMemberRepository.existsByUserIdAndTeamId(member.getId(), teamId)).isFalse();
        assertThat(userRepository.existsById(member.getId())).isTrue();
        mvc.perform(get("/api/teams").header("Authorization", existingToken))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/teams/" + teamId + "/projects").header("Authorization", existingToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/teams/" + teamId + "/members").header("Authorization", existingToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/" + project.getId() + "/tasks").header("Authorization", existingToken))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/tasks/" + task.getId()).header("Authorization", existingToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Forbidden\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/tasks/" + task.getId()).header("Authorization", existingToken))
                .andExpect(status().isForbidden());
        assertThat(taskRepository.existsById(task.getId())).isTrue();
    }

    @Test
    void removalDoesNotAffectMembershipInAnotherTeam() throws Exception {
        long firstTeam = createTeamAsOwner();
        long secondTeam = createTeamAsOwner();
        addMemberAsOwner(firstTeam, member.getUsername());
        addMemberAsOwner(secondTeam, member.getUsername());
        mvc.perform(delete("/api/teams/" + firstTeam + "/members/" + member.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        assertThat(teamMemberRepository.existsByUserIdAndTeamId(member.getId(), secondTeam)).isTrue();
    }

    @Test
    void memberCannotRemoveAnyoneIncludingThemselves() throws Exception {
        long teamId = createTeamAsOwner();
        addMemberAsOwner(teamId, member.getUsername());
        mvc.perform(delete("/api/teams/" + teamId + "/members/" + member.getId())
                        .header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/teams/" + teamId + "/members/" + owner.getId())
                        .header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        assertThat(teamMemberRepository.findByTeamId(teamId)).hasSize(2);
    }

    @Test
    void ownerCannotBeRemoved() throws Exception {
        long teamId = createTeamAsOwner();
        mvc.perform(delete("/api/teams/" + teamId + "/members/" + owner.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
        assertThat(teamMemberRepository.existsByUserIdAndTeamId(owner.getId(), teamId)).isTrue();
    }

    @Test
    void unknownTeamOrMembershipReturnsNotFound() throws Exception {
        long teamId = createTeamAsOwner();
        mvc.perform(delete("/api/teams/" + teamId + "/members/" + thirdUser.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/teams/999999/members").header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/teams/999999/members/" + member.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rosterAndRemovalRequireAuthentication() throws Exception {
        mvc.perform(get("/api/teams/1/members")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/teams/1/members/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/teams/1")).andExpect(status().isUnauthorized());
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("not-used-in-this-test");
        return userRepository.saveAndFlush(user);
    }

    private long createTeamAsOwner() throws Exception {
        return createTeamAs(owner, "Integration Team");
    }

    private long createTeamAs(User creator, String name) throws Exception {
        String response = mvc.perform(post("/api/teams")
                        .header("Authorization", bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Test only"}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long teamId = JsonPath.<Number>read(response, "$.id").longValue();

        TeamMember ownerMembership = teamMemberRepository
                .findByUserIdAndTeamId(creator.getId(), teamId)
                .orElseThrow();
        assertThat(ownerMembership.getRole()).isEqualTo("OWNER");
        return teamId;
    }

    private void addMemberAsOwner(long teamId, String identifier) throws Exception {
        mvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s"}
                                """.formatted(identifier)))
                .andExpect(status().isOk());
    }

    // Ein echter signierter Testtoken richtet den Test auf die Berechtigungsprüfung aus.
    // Der Login selbst wird getrennt in AuthIntegrationTests geprüft.
    private String bearer(User user) {
        return "Bearer " + jwtService.createToken(user);
    }
}
