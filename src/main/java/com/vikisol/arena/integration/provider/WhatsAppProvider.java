package com.vikisol.arena.integration.provider;

import java.util.Map;

/**
 * WhatsApp Business template-message abstraction - mirrors {@link EmailProvider}'s
 * interface -> Noop -> real shape. WhatsApp Business Platform only allows free-form replies inside
 * a 24h customer-initiated session window; every outbound notification-style message (the only
 * kind Arena would send) has to use a pre-approved template instead, hence {@code templateName} +
 * positional {@code params} rather than a free-text body.
 * <p>
 * Not yet wired at a real call site - see {@code WhatsAppBusinessProvider} for why (no phone-number
 * field exists anywhere in Arena's domain model yet, so there's no real "to" to send to). The
 * provider/Noop/real-stub shape is built now so wiring it in later (once a phone field exists and
 * a BSP is chosen) is a call-site change only, not a new abstraction.
 */
public interface WhatsAppProvider {

    String getProviderName();

    boolean isConfigured();

    /**
     * @param to E.164 phone number, e.g. {@code "+91XXXXXXXXXX"}
     * @param templateName name of the pre-approved template registered with the BSP
     * @param params ordered placeholder values substituted into the template body ({{1}}, {{2}}, ...)
     */
    void sendMessage(String to, String templateName, Map<String, String> params);
}
