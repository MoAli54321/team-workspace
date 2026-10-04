package com.teamworkspace.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Prüft ungültige Eingaben und verhindert, dass POST-Anfragen bestehende Datensätze überschreiben. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RequestValidationIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private TeamRepository teams;
    @Autowired private TeamMemberRepository members;
    @Autowired private ProjectRepository projects;
    @Autowired private TaskRepository tasks;
    @Autowired private JwtService jwtService;

    private Team team;
    private Project project;
    private String token;

    @BeforeEach
    void createWorkspace() {
        User user = new User();
        user.setUsername("validation-user");
        user.setEmail("validation@example.test");
        user.setPasswordHash("unused");
        users.saveAndFlush(user);
        token = "Bearer " + jwtService.createToken(user);
        team = new Team();
        team.setName("Original team");
        teams.saveAndFlush(team);
        TeamMember membership = new TeamMember();
        membership.setTeam(team);
        membership.setUser(user);
        membership.setRole("OWNER");
        members.saveAndFlush(membership);
        project = new Project();
        project.setName("Original project");
        project.setTeam(team);
        projects.saveAndFlush(project);
    }

    @Test
    void invalidRegistrationDoesNotCreateAccounts() throws Exception {
        for (String body : new String[] {
                "{}",
                "{\"username\":\" \",\"email\":\"new@example.test\",\"password\":\"Test123!\"}",
                "{\"username\":\"new\",\"email\":\"invalid\",\"password\":\"Test123!\"}",
                "{\"username\":\"new\",\"email\":\"new@example.test\"}",
                "{\"username\":\"new\",\"email\":\"new@example.test\",\"password\":\" \"}",
                "{\"username\":\"new\",\"email\":\"new@example.test\",\"password\":\"" + "ä".repeat(37) + "\"}"
        }) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void whitespaceIsTrimmedWhenRegisteringAndLoggingIn() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\" new-user \",\"email\":\" new@example.test \",\"password\":\" Test123! \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("new-user"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\" new-user \",\"password\":\" Test123! \"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"new-user\",\"password\":\"" + "a".repeat(73) + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyAndOversizedWorkspaceNamesAreRejected() throws Exception {
        for (String path : new String[] { "/api/teams", "/api/teams/" + team.getId() + "/projects" }) {
            for (String body : new String[] { "{}", "{\"name\":\" \"}",
                    "{\"name\":\"" + "a".repeat(256) + "\"}",
                    "{\"name\":\"Valid\",\"description\":\"" + "a".repeat(256) + "\"}" }) {
                mvc.perform(post(path).header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest());
            }
        }
        assertThat(teams.count()).isEqualTo(1);
        assertThat(projects.count()).isEqualTo(1);
    }

    @Test
    void invalidTaskUpdatesLeaveTheStoredTaskUnchanged() throws Exception {
        Task task = new Task();
        task.setTitle("Original task");
        task.setStatus("TODO");
        task.setPriority("MEDIUM");
        task.setProject(project);
        tasks.saveAndFlush(task);
        for (String body : new String[] {
                "{\"title\":\" \",\"status\":\"TODO\",\"priority\":\"MEDIUM\"}",
                "{\"title\":\"Changed\",\"status\":\"UNKNOWN\",\"priority\":\"MEDIUM\"}",
                "{\"title\":\"Changed\",\"status\":\"TODO\",\"priority\":\"UNKNOWN\"}",
                "{\"title\":\"Changed\"}"
        }) {
            mvc.perform(put("/api/tasks/" + task.getId()).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/projects/" + project.getId() + "/tasks").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(tasks.count()).isEqualTo(1);
        assertThat(tasks.findById(task.getId()).orElseThrow().getTitle()).isEqualTo("Original task");
    }

    @Test
    void suppliedIdsAndRelationshipsCannotOverwriteExistingData() throws Exception {
        Task task = new Task();
        task.setTitle("Original task");
        task.setProject(project);
        tasks.saveAndFlush(task);

        mvc.perform(post("/api/teams").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + team.getId() + ",\"name\":\"New team\",\"createdAt\":\"2000-01-01T00:00:00\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/teams/" + team.getId() + "/projects").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + project.getId()
                                + ",\"name\":\"New project\",\"team\":{\"id\":999999}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.team.id").value(team.getId()));
        mvc.perform(post("/api/projects/" + project.getId() + "/tasks").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + task.getId()
                                + ",\"title\":\"New task\",\"status\":\"TODO\",\"priority\":\"HIGH\",\"project\":{\"id\":999999}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.project.id").value(project.getId()));

        assertThat(teams.count()).isEqualTo(2);
        assertThat(projects.count()).isEqualTo(2);
        assertThat(tasks.count()).isEqualTo(2);
        assertThat(teams.findById(team.getId()).orElseThrow().getName()).isEqualTo("Original team");
        assertThat(projects.findById(project.getId()).orElseThrow().getName()).isEqualTo("Original project");
        assertThat(tasks.findById(task.getId()).orElseThrow().getTitle()).isEqualTo("Original task");
    }

    @Test
    void unknownTeamReturns404ForProjectReadAndCreate() throws Exception {
        mvc.perform(get("/api/teams/999999/projects").header("Authorization", token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/teams/999999/projects").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Project\"}"))
                .andExpect(status().isNotFound());
    }
}
