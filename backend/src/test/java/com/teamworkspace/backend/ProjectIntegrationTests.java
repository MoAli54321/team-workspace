package com.teamworkspace.backend;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prüft Projektzugriffe und das Löschen samt Aufgaben, ohne andere Projekte zu verändern.
 * Das Testprofil nutzt H2; die Testtransaktion wird nach jedem Test zurückgerollt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProjectIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JwtService jwtService;

    private User owner;
    private User member;
    private User outsider;

    @BeforeEach
    void createUsers() {
        owner = createUser("project-owner", "project-owner@example.test");
        member = createUser("project-member", "project-member@example.test");
        outsider = createUser("project-outsider", "project-outsider@example.test");
    }

    @Test
    void teamMemberCanCreateAndListProjects() throws Exception {
        long teamId = createTeam("Project Team");
        addMember(teamId, member);

        String created = createProject(teamId, member, "Team Workspace Webapp");
        long projectId = JsonPath.<Number>read(created, "$.id").longValue();

        mvc.perform(get("/api/teams/" + teamId + "/projects")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(projectId))
                .andExpect(jsonPath("$[0].name").value("Team Workspace Webapp"))
                .andExpect(jsonPath("$[0].description").value("Hauptprojekt unseres Teams"))
                .andExpect(jsonPath("$[0].team.id").value(teamId));

        assertThat(projectRepository.findByTeamId(teamId)).hasSize(1);
    }

    @Test
    void nonMemberCannotReadOrCreateProjects() throws Exception {
        long teamId = createTeam("Private Team");

        mvc.perform(get("/api/teams/" + teamId + "/projects")
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Not a team member"));

        mvc.perform(post("/api/teams/" + teamId + "/projects")
                        .header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Forbidden Project","description":"Must not be saved"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Not a team member"));

        assertThat(projectRepository.findByTeamId(teamId)).isEmpty();
    }

    @Test
    void projectRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/teams/1/projects"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/teams/1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Unauthorized Project"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void projectListsAreScopedToTheirTeam() throws Exception {
        long firstTeamId = createTeam("First Team");
        long secondTeamId = createTeam("Second Team");
        createProject(firstTeamId, owner, "First Project");
        createProject(secondTeamId, owner, "Second Project");

        mvc.perform(get("/api/teams/" + firstTeamId + "/projects")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("First Project"));
    }

    @Test
    void ownerCanDeleteProjectAndItsTasksWithoutAffectingOtherProjects() throws Exception {
        long teamId = createTeam("Delete Team");
        long projectId = JsonPath.<Number>read(createProject(teamId, owner, "Delete me"), "$.id").longValue();
        long otherId = JsonPath.<Number>read(createProject(teamId, owner, "Keep me"), "$.id").longValue();
        Task task = createTask(projectId);
        Task otherTask = createTask(otherId);

        mvc.perform(delete("/api/teams/" + teamId + "/projects/" + projectId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        projectRepository.flush();

        assertThat(projectRepository.existsById(projectId)).isFalse();
        assertThat(taskRepository.existsById(task.getId())).isFalse();
        assertThat(projectRepository.existsById(otherId)).isTrue();
        assertThat(taskRepository.existsById(otherTask.getId())).isTrue();
        mvc.perform(get("/api/teams/" + teamId + "/projects").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherId));
    }

    @Test
    void ownerCanDeleteAnEmptyProject() throws Exception {
        long teamId = createTeam("Empty Team");
        long projectId = JsonPath.<Number>read(createProject(teamId, owner, "Empty"), "$.id").longValue();
        mvc.perform(delete("/api/teams/" + teamId + "/projects/" + projectId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        projectRepository.flush();
        assertThat(projectRepository.existsById(projectId)).isFalse();
    }

    @Test
    void memberAndOutsiderCannotDeleteProject() throws Exception {
        long teamId = createTeam("Private Team");
        addMember(teamId, member);
        long projectId = JsonPath.<Number>read(createProject(teamId, member, "Keep"), "$.id").longValue();
        Task task = createTask(projectId);
        for (User user : List.of(member, outsider)) {
            mvc.perform(delete("/api/teams/" + teamId + "/projects/" + projectId)
                            .header("Authorization", bearer(user)))
                    .andExpect(status().isForbidden());
        }
        assertThat(projectRepository.existsById(projectId)).isTrue();
        assertThat(taskRepository.existsById(task.getId())).isTrue();
    }

    @Test
    void deletionIsScopedToTheTeamInTheUrl() throws Exception {
        long firstTeam = createTeam("First");
        long secondTeam = createTeam("Second");
        long projectId = JsonPath.<Number>read(createProject(secondTeam, owner, "Keep"), "$.id").longValue();
        mvc.perform(delete("/api/teams/" + firstTeam + "/projects/" + projectId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        assertThat(projectRepository.existsById(projectId)).isTrue();
    }

    @Test
    void deletingUnknownProjectOrTeamReturnsNotFound() throws Exception {
        long teamId = createTeam("Known Team");
        mvc.perform(delete("/api/teams/" + teamId + "/projects/999999")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/teams/999999/projects/999999")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void projectDeletionRequiresAuthentication() throws Exception {
        mvc.perform(delete("/api/teams/1/projects/1")).andExpect(status().isUnauthorized());
    }

    private Task createTask(long projectId) {
        Task task = new Task();
        task.setTitle("Project task");
        task.setProject(projectRepository.findById(projectId).orElseThrow());
        return taskRepository.saveAndFlush(task);
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("not-used-in-this-test");
        return userRepository.saveAndFlush(user);
    }

    private long createTeam(String name) throws Exception {
        String response = mvc.perform(post("/api/teams")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Test team"}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private void addMember(long teamId, User newMember) throws Exception {
        mvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s"}
                                """.formatted(newMember.getUsername())))
                .andExpect(status().isOk());
    }

    private String createProject(long teamId, User user, String name) throws Exception {
        return mvc.perform(post("/api/teams/" + teamId + "/projects")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Hauptprojekt unseres Teams"}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.team.id").value(teamId))
                .andReturn().getResponse().getContentAsString();
    }

    // Ein echter signierter Testtoken richtet den Test auf die Berechtigungsprüfung aus.
    // Der Login selbst wird getrennt in AuthIntegrationTests geprüft.
    private String bearer(User user) {
        return "Bearer " + jwtService.createToken(user);
    }
}
