package com.vikisol.arena.security.proxy;

import com.google.common.net.InetAddresses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class TrustedProxyFilter extends OncePerRequestFilter {

    public static final String PROXY_SECRET_HEADER = "X-Arena-Proxy-Secret";
    public static final String CLIENT_IP_HEADER = "X-Arena-Client-Ip";
    public static final String CLIENT_IP_ATTRIBUTE = TrustedProxyFilter.class.getName() + ".clientIp";

    private final Environment environment;

    @Value("${app.proxy.secret:}")
    private String proxySecret;

    public TrustedProxyFilter(Environment environment) {
        this.environment = environment;
    }

    public static String trustedClientIp(HttpServletRequest request) {
        Object value = request.getAttribute(CLIENT_IP_ATTRIBUTE);
        return value instanceof String ip ? ip : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!StringUtils.hasText(proxySecret) || environment.acceptsProfiles(Profiles.of("local")) || isHealth(request)) {
            chain.doFilter(request, response);
            return;
        }

        String presentedSecret = request.getHeader(PROXY_SECRET_HEADER);
        if (!matchesProxySecret(presentedSecret)) {
            forbid(response);
            return;
        }

        String clientIp = request.getHeader(CLIENT_IP_HEADER);
        if (!StringUtils.hasText(clientIp) || !InetAddresses.isInetAddress(clientIp.trim())) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid proxy client IP\"}");
            return;
        }

        request.setAttribute(CLIENT_IP_ATTRIBUTE, InetAddresses.forString(clientIp.trim()).getHostAddress());
        chain.doFilter(request, response);
    }

    private boolean isHealth(HttpServletRequest request) {
        String path = StringUtils.hasText(request.getServletPath()) ? request.getServletPath() : request.getRequestURI();
        return path.equals("/actuator/health") || path.startsWith("/actuator/health/");
    }

    private boolean matchesProxySecret(String presentedSecret) {
        if (!StringUtils.hasText(presentedSecret)) {
            return false;
        }
        return MessageDigest.isEqual(sha256(proxySecret), sha256(presentedSecret));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for proxy-secret verification", e);
        }
    }

    private static void forbid(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"message\":\"Access denied\"}");
    }
}
