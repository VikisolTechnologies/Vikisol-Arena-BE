package com.vikisol.arena.security.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * RFC 6238 TOTP (Google Authenticator-compatible: HMAC-SHA1, 6 digits, 30s step) implemented
 * directly rather than pulling in a third-party TOTP library - the algorithm is ~40 lines and
 * security-critical enough that reviewing our own code beats trusting an unfamiliar dependency
 * for something this size (see DECISIONS.md).
 */
@Service
public class TotpService {

    private static final int SECRET_BYTES = 20; // 160-bit, matches Google Authenticator's default
    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int DRIFT_WINDOW_STEPS = 1; // tolerate +/-30s clock drift
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String otpAuthUri(String base32Secret, String accountEmail, String issuer) {
        String label = URLEncoder.encode(issuer + ":" + accountEmail, StandardCharsets.UTF_8);
        String issuerParam = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + issuerParam
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    public boolean verifyCode(String base32Secret, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        long currentStep = Instant.now().getEpochSecond() / STEP_SECONDS;
        for (long i = -DRIFT_WINDOW_STEPS; i <= DRIFT_WINDOW_STEPS; i++) {
            if (generateCode(base32Secret, currentStep + i).equals(code)) return true;
        }
        return false;
    }

    private String generateCode(String base32Secret, long counter) {
        try {
            byte[] key = base32Decode(base32Secret);
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP generation failed", e);
        }
    }

    private String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int bits = 0, value = 0;
        for (byte b : data) {
            value = (value << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32_ALPHABET.charAt((value >>> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32_ALPHABET.charAt((value << (5 - bits)) & 0x1f));
        }
        return sb.toString();
    }

    private byte[] base32Decode(String encoded) {
        String cleaned = encoded.trim().toUpperCase(Locale.ROOT).replace("=", "");
        byte[] out = new byte[cleaned.length() * 5 / 8];
        int bits = 0, value = 0, index = 0;
        for (char c : cleaned.toCharArray()) {
            int idx = BASE32_ALPHABET.indexOf(c);
            if (idx < 0) continue;
            value = (value << 5) | idx;
            bits += 5;
            if (bits >= 8) {
                out[index++] = (byte) ((value >>> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out;
    }
}
