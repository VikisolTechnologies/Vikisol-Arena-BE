package com.vikisol.arena.integration.config;

import com.vikisol.arena.integration.provider.EmailProvider;
import com.vikisol.arena.integration.provider.MeetingLinkProvider;
import com.vikisol.arena.integration.provider.NoopEmailProvider;
import com.vikisol.arena.integration.provider.NoopMeetingLinkProvider;
import com.vikisol.arena.integration.provider.NoopWhatsAppProvider;
import com.vikisol.arena.integration.provider.ResendEmailProvider;
import com.vikisol.arena.integration.provider.TeamsMeetingLinkProvider;
import com.vikisol.arena.integration.provider.WhatsAppBusinessProvider;
import com.vikisol.arena.integration.provider.WhatsAppProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Single place that resolves which concrete {@link EmailProvider}/{@link WhatsAppProvider}/
 * {@link MeetingLinkProvider} the rest of the app gets - AuthService, ApplicationService and
 * InterviewService only ever depend on the interfaces, never construct
 * ResendEmailProvider/WhatsAppBusinessProvider/TeamsMeetingLinkProvider directly.
 * <p>
 * Mirrors the intent of HRLMS-BE's {@code IntegrationService#getMailProvider()}/
 * {@code #getMeetingProvider()} (build the real provider from config, fall back to the Noop bean
 * when {@code isConfigured()} is false), simplified to static env-var-backed {@code @Value} config
 * resolved once at application startup instead of HRLMS-BE's per-tenant, DB-stored, runtime-
 * editable settings - Arena has no "Company Integrations" settings table/UI yet (out of scope for
 * this phase), so these follow the exact same override style as the rest of
 * {@code application.yml} (DB credentials, JWT secret): env var with a blank/local-dev default.
 * A real deployment sets the env vars and restarts; there's no live-reconfigure path yet, which is
 * fine for a single-tenant product at this stage.
 */
@Configuration
public class IntegrationProviderConfig {

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from:Vikisol Arena <no-reply@arena.vikisol.dev>}")
    private String resendFrom;

    @Value("${whatsapp.access-token:}")
    private String whatsappAccessToken;

    @Value("${whatsapp.phone-number-id:}")
    private String whatsappPhoneNumberId;

    @Value("${teams.tenant-id:}")
    private String teamsTenantId;

    @Value("${teams.client-id:}")
    private String teamsClientId;

    @Value("${teams.client-secret:}")
    private String teamsClientSecret;

    @Value("${teams.organizer-email:}")
    private String teamsOrganizerEmail;

    @Bean
    @Primary
    public EmailProvider emailProvider(NoopEmailProvider noopEmailProvider) {
        ResendEmailProvider resend = new ResendEmailProvider(resendApiKey, resendFrom);
        return resend.isConfigured() ? resend : noopEmailProvider;
    }

    @Bean
    @Primary
    public WhatsAppProvider whatsAppProvider(NoopWhatsAppProvider noopWhatsAppProvider) {
        WhatsAppBusinessProvider real = new WhatsAppBusinessProvider(whatsappAccessToken, whatsappPhoneNumberId);
        return real.isConfigured() ? real : noopWhatsAppProvider;
    }

    @Bean
    @Primary
    public MeetingLinkProvider meetingLinkProvider(NoopMeetingLinkProvider noopMeetingLinkProvider) {
        TeamsMeetingLinkProvider real = new TeamsMeetingLinkProvider(teamsTenantId, teamsClientId, teamsClientSecret, teamsOrganizerEmail);
        return real.isConfigured() ? real : noopMeetingLinkProvider;
    }
}
