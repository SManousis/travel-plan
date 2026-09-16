package com.travelplan.auth;

import com.travelplan.auth.security.TokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String username;
    private final String passwordHash;
    private final String serviceToken;
    private final Map<String, Instant> failedLogins = new ConcurrentHashMap<>();
    private final Map<String, String> provisioned = new ConcurrentHashMap<>();

    public AuthController(TokenService tokens,
                          @Value("${security.admin-username:admin}") String username,
                          @Value("${security.admin-password:change-me-admin}") String password,
                          @Value("${security.internal-service-token:change-me-internal}") String serviceToken) {
        this.tokens = tokens;
        this.username = username;
        this.passwordHash = passwordEncoder.encode(password);
        this.serviceToken = serviceToken;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Instant blockedUntil = failedLogins.get(request.username());
        if (blockedUntil != null && blockedUntil.isAfter(Instant.now())) return ResponseEntity.status(429).body(new ErrorResponse("RATE_LIMITED", "Try again later"));
        String hash = username.equals(request.username()) ? passwordHash : provisioned.get(request.username());
        if (hash == null || !passwordEncoder.matches(request.password(), hash)) {
            failedLogins.put(request.username(), Instant.now().plusSeconds(10));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("UNAUTHENTICATED", "Invalid credentials"));
        }
        failedLogins.remove(request.username());
        return ResponseEntity.ok(TokenResponse.from(tokens.issue(username, "ADMIN")));
    }

    @PostMapping("/internal/provision")
    public ResponseEntity<Void> provision(@RequestBody ProvisionRequest request, @RequestHeader(value="X-Internal-Service-Token", required=false) String presented) {
        if (!serviceToken.equals(presented)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        provisioned.putIfAbsent(request.username(), passwordEncoder.encode(request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @CookieValue(value = "travel_plan_refresh", required = false) String refreshCookie) {
        if (authorization == null && refreshCookie != null) authorization = "Bearer " + refreshCookie;
        TokenService.Claims claims = bearer(authorization);
        if (claims == null || !"ADMIN".equals(claims.role())) return ResponseEntity.status(401).body(new ErrorResponse("UNAUTHENTICATED", "Invalid refresh token"));
        tokens.revoke(authorization.substring("Bearer ".length()));
        TokenResponse response = TokenResponse.from(tokens.issue(claims.subject(), claims.role()));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(response.accessToken()).toString()).body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) { tokens.revoke(authorization.substring("Bearer ".length())); return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, ResponseCookie.from("travel_plan_refresh", "").httpOnly(true).secure(true).sameSite("Strict").path("/api/auth").maxAge(0).build().toString()).build(); }

    @GetMapping("/me")
    public TokenService.Claims me(@RequestAttribute("travelPlanClaims") TokenService.Claims claims) { return claims; }

    @PostMapping("/internal/revoke/{subject}")
    public ResponseEntity<Void> revokeSubject(@PathVariable String subject, @RequestHeader(value = "X-Internal-Service-Token", required = false) String presented) {
        if (!serviceToken.equals(presented)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        tokens.revokeSubject(subject);
        return ResponseEntity.noContent().build();
    }

    private TokenService.Claims bearer(String value) { return value != null && value.startsWith("Bearer ") ? tokens.verify(value.substring(7)) : null; }
    private ResponseCookie refreshCookie(String token) { return ResponseCookie.from("travel_plan_refresh", token).httpOnly(true).secure(true).sameSite("Strict").path("/api/auth").maxAge(900).build(); }
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record ProvisionRequest(@NotBlank String username, @NotBlank String password) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresAt) { static TokenResponse from(TokenService.IssuedToken t) { return new TokenResponse(t.value(), "Bearer", t.expiresAt()); } }
    public record ErrorResponse(String code, String message) {}
}
