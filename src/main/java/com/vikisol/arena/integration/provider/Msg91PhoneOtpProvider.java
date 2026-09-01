package com.vikisol.arena.integration.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MSG91 (https://msg91.com) implementation of {@link PhoneOtpProvider} - chosen over Twilio for
 * being dramatically cheaper per-SMS to Indian numbers, which is all this product's phone
 * sign-in/signup ever targets (see AuthService's E.164 +91 assumption throughout). Uses MSG91's
 * Flow API (POST https://control.msg91.com/api/v5/flow/), the same plain
 * {@code java.net.http.HttpClient} style as {@link ResendEmailProvider}/{@link WhatsAppBusinessProvider}.
 * <p>
 * <b>Real operational requirement, not just an API key</b>: Indian telecom regulation (TRAI's DLT
 * framework) requires transactional SMS templates to be pre-registered and approved before MSG91
 * will actually deliver them - an arbitrary message body sent through this API to an Indian number
 * will be silently rejected without one. {@code templateId} must be the ID of a DLT-approved
 * template containing exactly one variable (named {@code otpVariableName}, default {@code "OTP"})
 * that MSG91's dashboard walks through registering - this code cannot skip that step, only use
 * the result of it. buildOtpMessage()'s WebOTP-formatted text is NOT sent as a literal body here
 * (the DLT template's own approved wording is what actually gets delivered) - the variable is
 * filled with just the raw code, and the template's approved copy is expected to already contain
 * the WebOTP "@arena.vikisol.in #<code>" suffix so Chrome/Android auto-read still works; see the
 * class comment on why the DLT template's text is the one thing this code cannot construct.
 * <p>
 * NOT independently verified against a live MSG91 account - no account exists yet to test against
 * (same "isConfigured() correctly stays false, dormant" status as ResendEmailProvider was before
 * a real key existed). Deliberately NOT a {@code @Component} - see ResendEmailProvider's javadoc
 * for why (constructed by IntegrationProviderConfig from env-var-backed config instead).
 */
@Slf4j
public class Msg91PhoneOtpProvider implements PhoneOtpProvider {

    private final String authKey;
    private final String templateId;
    private final String senderId;
    private final String otpVariableName;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final String MSG91_FLOW_ENDPOINT = "https://control.msg91.com/api/v5/flow/";

    public Msg91PhoneOtpProvider(String authKey, String templateId, String senderId, String otpVariableName) {
        this.authKey = authKey;
        this.templateId = templateId;
        this.senderId = senderId;
        this.otpVariableName = otpVariableName;
    }

    @Override
    public String getProviderName() {
        return "MSG91";
    }

    @Override
    public boolean isConfigured() {
        return authKey != null && !authKey.isBlank() && templateId != null && !templateId.isBlank();
    }

    @Override
    public void sendOtp(String phoneNumber, String code) {
        if (!isConfigured()) {
            // Unreachable in practice - IntegrationProviderConfig only wires this bean in when
            // isConfigured() is true - guarded explicitly, matching WhatsAppBusinessProvider's
            // same never-tested-live caution.
            throw new IllegalStateException("Msg91PhoneOtpProvider invoked without MSG91_AUTH_KEY/MSG91_TEMPLATE_ID configured");
        }
        try {
            // MSG91 wants a bare country-code-prefixed number ("919876543210"), no leading "+" -
            // every phoneNumber this app stores/passes is E.164 ("+919876543210"), see
            // AuthService/PhoneOtpProvider callers.
            String mobile = phoneNumber.startsWith("+") ? phoneNumber.substring(1) : phoneNumber;
            Map<String, Object> recipient = new java.util.HashMap<>(Map.of("mobiles", mobile, otpVariableName, code));
            Map<String, Object> payload = new java.util.HashMap<>(Map.of(
                    "template_id", templateId,
                    "recipients", List.of(recipient)));
            if (senderId != null && !senderId.isBlank()) {
                payload.put("sender", senderId);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MSG91_FLOW_ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("authkey", authKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("MSG91 API returned " + response.statusCode() + ": " + response.body());
            }
            log.info("OTP sent via MSG91 to {}", phoneNumber);
        } catch (Exception e) {
            log.error("MSG91 SMS send failed: {}", e.getMessage());
            throw new RuntimeException("Could not send OTP via MSG91: " + e.getMessage(), e);
        }
    }
}
