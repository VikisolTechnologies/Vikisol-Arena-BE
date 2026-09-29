package com.vikisol.arena.business;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.business.repository.BusinessVerificationRepository;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Membership;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import com.vikisol.arena.integration.provider.EmailMessage;
import com.vikisol.arena.integration.provider.EmailProvider;
import com.vikisol.arena.integration.provider.ProviderException;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Business verification (G27) and the team role catalogue (G28). */
@AutoConfigureMockMvc
class BusinessVerificationTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired MembershipRepository memberships;
    @Autowired BusinessVerificationRepository verifications;
    @MockBean TokenDenylistService denylist;
    @MockBean EmailProvider email;

    private User admin, recruiter, talent;
    private EnterpriseProfile company;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        admin = user(Role.COMPANY_ADMIN);
        company = enterprises.save(EnterpriseProfile.builder().user(admin).companyName("GreenLeaf Labs").logoEmoji("G")
                .industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
        recruiter = user(Role.RECRUITER);
        memberships.save(Membership.builder().user(recruiter).tenant(company).joinedAt(Instant.now()).build());
        talent = user(Role.TALENT);
    }

    private static final String SUBMIT = "{\"legalName\":\"GreenLeaf Labs Pvt Ltd\",\"website\":\"https://www.greenleaf.example\","
            + "\"workEmail\":\"asha@greenleaf.example\",\"submitterRole\":\"founder\"}";

    @Test
    void aConfirmedDomainCodeEarnsThePublicBadge() throws Exception {
        mvc.perform(get("/companies/" + company.getId() + "/verification"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.verified").value(false));

        call(admin, post("/enterprise/verification"), SUBMIT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.domain").value("greenleaf.example"))
                .andExpect(jsonPath("$.data.workEmail").value("a***@greenleaf.example"));
        String code = sentCode();

        String wrong = code.equals("123456") ? "654321" : "123456";
        call(admin, post("/enterprise/verification/confirm"), "{\"code\":\"" + wrong + "\"}").andExpect(status().isBadRequest());
        call(recruiter, post("/enterprise/verification/confirm"), "{\"code\":\"" + code + "\"}").andExpect(status().isForbidden());
        call(admin, post("/enterprise/verification/confirm"), "{\"code\":\"" + code + "\"}")
                .andExpect(jsonPath("$.data.status").value("verified"));

        call(recruiter, get("/enterprise/verification"), null).andExpect(jsonPath("$.data.status").value("verified"));
        mvc.perform(get("/companies/" + company.getId() + "/verification"))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.domain").value("greenleaf.example"));
        // The code is never stored in the clear.
        assertThat(verifications.findByTenantId(company.getId()).orElseThrow().getCodeHash()).isNull();
    }

    @Test
    void theWorkEmailMustBeAtTheWebsitesDomainAndNotAPersonalMailbox() throws Exception {
        call(admin, post("/enterprise/verification"), SUBMIT.replace("asha@greenleaf.example", "asha@gmail.com"))
                .andExpect(status().isBadRequest());
        call(admin, post("/enterprise/verification"), SUBMIT.replace("asha@greenleaf.example", "asha@other.example"))
                .andExpect(status().isBadRequest());
        call(admin, post("/enterprise/verification"), SUBMIT.replace("\"founder\"", "\"ceo\"")).andExpect(status().isBadRequest());
        // A subdomain mailbox is fine.
        call(admin, post("/enterprise/verification"), SUBMIT.replace("asha@greenleaf.example", "asha@hr.greenleaf.example"))
                .andExpect(status().isOk());
    }

    @Test
    void onlyTheCompanyAdminSubmitsAndWrongCodesRunOut() throws Exception {
        call(recruiter, post("/enterprise/verification"), SUBMIT).andExpect(status().isForbidden());
        call(talent, post("/enterprise/verification"), SUBMIT).andExpect(status().isForbidden());
        call(admin, post("/enterprise/verification"), SUBMIT).andExpect(status().isOk());
        String code = sentCode();
        String wrong = code.equals("123456") ? "654321" : "123456";
        for (int i = 0; i < 5; i++) {
            call(admin, post("/enterprise/verification/confirm"), "{\"code\":\"" + wrong + "\"}").andExpect(status().isBadRequest());
        }
        call(admin, post("/enterprise/verification/confirm"), "{\"code\":\"" + code + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Too many wrong codes. Ask for a new one."));
        // Asking again straight away is throttled.
        call(admin, post("/enterprise/verification"), SUBMIT).andExpect(status().isBadRequest());
    }

    @Test
    void aFailedEmailIsAnHonest503AndNeverVerifies() throws Exception {
        doThrow(new ProviderException(ProviderException.Kind.EMAIL)).when(email).sendEmail(any());
        call(admin, post("/enterprise/verification"), SUBMIT)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("We couldn't send the code right now. Please try again in a minute."));
        mvc.perform(get("/companies/" + company.getId() + "/verification")).andExpect(jsonPath("$.data.verified").value(false));
    }

    @Test
    void teamRolesCatalogueIsForTheTeamOnly() throws Exception {
        call(recruiter, get("/enterprise/team/roles"), null)
                .andExpect(jsonPath("$.data[0].role").value("company_admin"))
                .andExpect(jsonPath("$.data[1].can[0]").value("post_and_manage_jobs"));
        call(talent, get("/enterprise/team/roles"), null).andExpect(status().isForbidden());
    }

    private String sentCode() {
        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(email, atLeastOnce()).sendEmail(sent.capture());
        Matcher m = Pattern.compile(">(\\d{6})<").matcher(sent.getValue().htmlBody());
        assertThat(m.find()).isTrue();
        assertThat(sent.getValue().to()).containsExactly("asha@greenleaf.example");
        return m.group(1);
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private User user(Role role) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Person")
                .role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
}
