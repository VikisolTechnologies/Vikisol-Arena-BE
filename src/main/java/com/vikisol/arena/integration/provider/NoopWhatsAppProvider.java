package com.vikisol.arena.integration.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

// Default WhatsAppProvider until a BSP is chosen and WHATSAPP_ACCESS_TOKEN/WHATSAPP_PHONE_NUMBER_ID
// are configured - logs what would have been sent and no-ops otherwise, same reasoning as
// NoopEmailProvider/NoopMeetingLinkProvider.
@Slf4j
@Component
public class NoopWhatsAppProvider implements WhatsAppProvider {

    @Override
    public String getProviderName() {
        return "None (log only)";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void sendMessage(String to, String templateName, Map<String, String> params) {
        log.info("[whatsapp:noop] would send template \"{}\" to {} with params {}", templateName, to, params);
    }
}
