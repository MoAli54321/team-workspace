package com.teamworkspace.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private JwtService jwtService;

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
                .andExpect(jsonPath("$[0].name").value("Integration Team"));
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

    private String bearer(User user) {
        return "Bearer " + jwtService.createToken(user);
    }
}
