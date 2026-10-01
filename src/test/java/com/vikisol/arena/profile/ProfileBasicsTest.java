package com.vikisol.arena.profile;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE-API-GAPS 1-5 (onboarding basics) and the 8-character password minimum. */
@AutoConfigureMockMvc
class ProfileBasicsTest extends EmbeddedPostgresAppTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @MockBean TokenDenylistService denylist;

    private User me;
    private String auth;

    @BeforeEach
    void talent() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        me = user(Role.TALENT);
        profiles.save(CandidateProfile.builder().user(me).name("Asha").avatarEmoji("*").title("Designer")
                .industry(Industry.DESIGN).location("Hyderabad").remote(false).experienceYears(4).rateFloor(900)
                .consent(new ConsentSettings(false, true)).build());
        auth = bearer(me);
    }

    @Test
    void gap4PatchChangesOnlyTheFieldsSent() throws Exception {
        mvc.perform(patch("/profile/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"Weekend runner, weekday designer.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Asha"))
                .andExpect(jsonPath("$.data.title").value("Designer"))
                .andExpect(jsonPath("$.data.bio").value("Weekend runner, weekday designer."));
        mvc.perform(patch("/profile/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha R\",\"title\":\"Product designer\"}"))
                .andExpect(jsonPath("$.data.name").value("Asha R"))
                .andExpect(jsonPath("$.data.bio").value("Weekend runner, weekday designer."));
        // The full profile read keeps working and sees the change.
        mvc.perform(get("/profile/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Asha R"))
                .andExpect(jsonPath("$.data.rateFloor").value(900));
    }

    @Test
    void gap4BioIsCappedAt160() throws Exception {
        mvc.perform(patch("/profile/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"" + "x".repeat(161) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/profile/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gap5AvailabilityIsAClosedList() throws Exception {
        mvc.perform(patch("/profile/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availability\":[\"Weekends\",\"evenings\",\"weekends\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability.length()").value(2))
                .andExpect(jsonPath("$.data.availability[0]").value("weekends"));
        mvc.perform(patch("/profile/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availability\":[\"mornings\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gap1IntentsAreSavedAndStayPrivate() throws Exception {
        mvc.perform(put("/profile/me/intents").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intents\":[\"activities\",\"job\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intents[1]").value("job"));
        mvc.perform(put("/profile/me/intents").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intents\":[\"dating\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/profile/" + me.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intents").doesNotExist());
    }

    @Test
    void gap2InterestsAreTrimmedDedupedCappedAndPublic() throws Exception {
        mvc.perform(put("/profile/me/interests").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interests\":[\" Badminton \",\"badminton\",\"Board games\",\"\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interests.length()").value(2))
                .andExpect(jsonPath("$.data.interests[0]").value("Badminton"));
        mvc.perform(put("/profile/me/interests").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interests\":[\"" + "x".repeat(31) + "\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/profile/" + me.getId()))
                .andExpect(jsonPath("$.data.interests[1]").value("Board games"));
    }

    @Test
    void gap3PhotoUploadIsImageOnlyAndCanBeRemoved() throws Exception {
        mvc.perform(multipart("/profile/me/photo").file(new MockMultipartFile("file", "me.png", "image/png", PNG))
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoUrl").value(containsString("sig=")));
        mvc.perform(get("/profile/" + me.getId()))
                .andExpect(jsonPath("$.data.photoUrl").value(containsString("/profile-photo/")));
        mvc.perform(multipart("/profile/me/photo").file(new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                        .header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/profile/me/photo").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoUrl").doesNotExist());
    }

    @Test
    void onlyTheTalentThemselfCanWrite() throws Exception {
        mvc.perform(patch("/profile/me").contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        User recruiter = user(Role.RECRUITER);
        mvc.perform(put("/profile/me/interests").header("Authorization", bearer(recruiter))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"interests\":[\"x\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void newPasswordsNeedEightCharacters() throws Exception {
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"N\",\"email\":\"n-" + UUID.randomUUID() + "@test.local\",\"password\":\"seven77\",\"role\":\"talent\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.password").value("must be at least 8 characters"));
        mvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@test.local\",\"token\":\"t\",\"newPassword\":\"short12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.newPassword").value("must be at least 8 characters"));
    }

    // ARCHITECT-REVIEW-BE-1 (architect notes on B8/B9): the 18+ rule must be enforced on sign-up
    // itself, not only at activity create/join.
    @Test
    void signUpRequiresBeingEighteenOrOlder() throws Exception {
        String email = "minor-" + UUID.randomUUID() + "@test.local";
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Minor\",\"email\":\"" + email + "\",\"password\":\"password1\",\"role\":\"talent\","
                                + "\"dateOfBirth\":\"" + java.time.LocalDate.now().minusYears(17) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You must be 18 or older to join Arena"));
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No DOB\",\"email\":\"" + email + "\",\"password\":\"password1\",\"role\":\"talent\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.dateOfBirth").value("is required"));
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Future\",\"email\":\"" + email + "\",\"password\":\"password1\",\"role\":\"talent\","
                                + "\"dateOfBirth\":\"" + java.time.LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("dateOfBirth can't be in the future"));
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Adult\",\"email\":\"" + email + "\",\"password\":\"password1\",\"role\":\"talent\","
                                + "\"dateOfBirth\":\"" + java.time.LocalDate.now().minusYears(19) + "\"}"))
                .andExpect(status().isOk());
    }

    // ARCHITECT-REVIEW-BE-1 (architect notes on B8/B9): the onboarding path a Google/phone
    // signup uses to set its date of birth (no password-form SignUpRequest to carry it) must
    // enforce 18+ too, not just "not in the future".
    @Test
    void verificationDateOfBirthAlsoRequiresBeingEighteenOrOlder() throws Exception {
        User googleSignup = user(Role.TALENT);
        mvc.perform(put("/verification/date-of-birth").header("Authorization", bearer(googleSignup))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateOfBirth\":\"" + java.time.LocalDate.now().minusYears(16) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You must be 18 or older to join Arena"));
        mvc.perform(put("/verification/date-of-birth").header("Authorization", bearer(googleSignup))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateOfBirth\":\"" + java.time.LocalDate.now().minusYears(20) + "\"}"))
                .andExpect(status().isOk());
    }

    private User user(Role role) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Asha")
                .role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    private String bearer(User u) {
        return "Bearer " + tokens.generateToken(u.getId(), u.getEmail(), u.getName(), u.getRole());
    }
}
