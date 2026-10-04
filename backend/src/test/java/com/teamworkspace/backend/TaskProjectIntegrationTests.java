package com.teamworkspace.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Prüft, dass Aufgaben einem Projekt gehören und Teamrechte auch bei direktem Zugriff über die ID gelten.
 * Das Testprofil nutzt H2; die Testtransaktion wird nach jedem Test zurückgerollt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskProjectIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JwtService jwtService;

    private User owner;
    private User member;
    private User outsider;

    @BeforeEach
    void createUsers() {
        owner = createUser("task-owner", "task-owner@example.test");
        member = createUser("task-member", "task-member@example.test");
        outsider = createUser("task-outsider", "task-outsider@example.test");
    }

    @Test
    void memberCanCreateAndListOnlyTasksOfRequestedProject() throws Exception {
        long teamId = createTeam();
        addMember(teamId, member);
        long firstProjectId = createProject(teamId, "First Project");
        long secondProjectId = createProject(teamId, "Second Project");

        long firstTaskId = createTask(firstProjectId, member, "Dashboard bauen");
        createTask(secondProjectId, owner, "Other project task");

        Task storedTask = taskRepository.findById(firstTaskId).orElseThrow();
        assertThat(storedTask.getProject().getId()).isEqualTo(firstProjectId);

        mvc.perform(get("/api/projects/" + firstProjectId + "/tasks")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstTaskId))
                .andExpect(jsonPath("$[0].title").value("Dashboard bauen"))
                .andExpect(jsonPath("$[0].project.id").value(firstProjectId));
    }

    @Test
    void nonMemberCannotReadOrCreateProjectTasks() throws Exception {
        long teamId = createTeam();
        long projectId = createProject(teamId, "Private Project");

        mvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("Forbidden task")))
                .andExpect(status().isForbidden());

        assertThat(taskRepository.findByProjectId(projectId)).isEmpty();
    }

    @Test
    void missingProjectReturnsNotFound() throws Exception {
        long missingProjectId = 999999L;

        mvc.perform(get("/api/projects/" + missingProjectId + "/tasks")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/projects/" + missingProjectId + "/tasks")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("Missing project task")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRequiresMembershipAndKeepsProjectAssignment() throws Exception {
        long teamId = createTeam();
        addMember(teamId, member);
        long projectId = createProject(teamId, "Update Project");
        long taskId = createTask(projectId, owner, "Original title");

        mvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("Forbidden update")))
                .andExpect(status().isForbidden());
        assertThat(taskRepository.findById(taskId).orElseThrow().getTitle()).isEqualTo("Original title");

        mvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Updated task",
                                  "description":"Updated description",
                                  "status":"DONE",
                                  "priority":"HIGH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated task"))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.project.id").value(projectId));
    }

    @Test
    void deleteRequiresMembership() throws Exception {
        long teamId = createTeam();
        addMember(teamId, member);
        long projectId = createProject(teamId, "Delete Project");
        long taskId = createTask(projectId, owner, "Delete me");

        mvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
        assertThat(taskRepository.existsById(taskId)).isTrue();

        mvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isNoContent());
        assertThat(taskRepository.existsById(taskId)).isFalse();
    }

    @Test
    void taskRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/projects/1/tasks"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/projects/1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("Unauthorized task")))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("Unauthorized update")))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isUnauthorized());
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("not-used-in-this-test");
        return userRepository.saveAndFlush(user);
    }

    private long createTeam() throws Exception {
        String response = mvc.perform(post("/api/teams")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Task Team","description":"Task integration test"}
                                """))
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

    private long createProject(long teamId, String name) throws Exception {
        String response = mvc.perform(post("/api/teams/" + teamId + "/projects")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Task test project"}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private long createTask(long projectId, User user, String title) throws Exception {
        String response = mvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody(title)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.id").value(projectId))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private String taskBody(String title) {
        return """
                {
                  "title":"%s",
                  "description":"Task integration test",
                  "status":"TODO",
                  "priority":"MEDIUM"
                }
                """.formatted(title);
    }

    // Ein echter signierter Testtoken richtet den Test auf die Berechtigungsprüfung aus.
    // Der Login selbst wird getrennt in AuthIntegrationTests geprüft.
    private String bearer(User user) {
        return "Bearer " + jwtService.createToken(user);
    }
}
