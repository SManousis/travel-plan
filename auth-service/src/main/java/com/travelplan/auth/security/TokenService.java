package com.travelplan.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final byte[] secret;
    private final long ttlSeconds;
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    public TokenService(@Value("${security.token-secret:replace-with-at-least-32-random-characters}") String secret,
                        @Value("${security.access-token-ttl-seconds:900}") long ttlSeconds) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("security.token-secret must contain at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public IssuedToken issue(String subject, String role) {
        long expiresAt = Instant.now().plusSeconds(ttlSeconds).getEpochSecond();
        String payload = subject + "|" + role + "|travel-plan-auth|travel-plan-admin|" + expiresAt + "|" + UUID.randomUUID();
        String encodedPayload = encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return new IssuedToken(encodedPayload + "." + encoder.encodeToString(sign(encodedPayload)), expiresAt);
    }

    public Claims verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), decoder.decode(parts[1]))) {
                return null;
            }
            String[] values = new String(decoder.decode(parts[0]), StandardCharsets.UTF_8).split("\\|", -1);
            if (values.length != 6 || !"travel-plan-auth".equals(values[2]) || !"travel-plan-admin".equals(values[3])) return null;
            long expiresAt = Long.parseLong(values[4]);
            if (expiresAt <= Instant.now().getEpochSecond() || revoked.containsKey(values[5])) return null;
            return new Claims(values[0], values[1], values[5], expiresAt);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public void revoke(String token) {
        Claims claims = verifyIgnoringRevocation(token);
        if (claims != null) revoked.put(claims.jti(), Instant.ofEpochSecond(claims.expiresAt()));
    }

    private Claims verifyIgnoringRevocation(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), decoder.decode(parts[1]))) return null;
            String[] values = new String(decoder.decode(parts[0]), StandardCharsets.UTF_8).split("\\|", -1);
            if (values.length != 6 || !"travel-plan-auth".equals(values[2]) || !"travel-plan-admin".equals(values[3])) return null;
            long expiresAt = Long.parseLong(values[4]);
            return expiresAt > Instant.now().getEpochSecond()
                    ? new Claims(values[0], values[1], values[5], expiresAt) : null;
        } catch (RuntimeException exception) { return null; }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) { throw new IllegalStateException("Cannot sign access token", exception); }
    }

    public record Claims(String subject, String role, String jti, long expiresAt) {}
    public record IssuedToken(String value, long expiresAt) {}
}
