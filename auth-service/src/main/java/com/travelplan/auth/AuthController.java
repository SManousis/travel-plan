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

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String username;
    private final String passwordHash;

    public AuthController(TokenService tokens,
                          @Value("${security.admin-username:admin}") String username,
                          @Value("${security.admin-password:change-me-admin}") String password) {
        this.tokens = tokens;
        this.username = username;
        this.passwordHash = passwordEncoder.encode(password);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        if (!username.equals(request.username()) || !passwordEncoder.matches(request.password(), passwordHash)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("UNAUTHENTICATED", "Invalid credentials"));
        }
        return ResponseEntity.ok(TokenResponse.from(tokens.issue(username, "ADMIN")));
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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authorization) { tokens.revoke(authorization.substring("Bearer ".length())); }

    @GetMapping("/me")
    public TokenService.Claims me(@RequestAttribute("travelPlanClaims") TokenService.Claims claims) { return claims; }

    private TokenService.Claims bearer(String value) { return value != null && value.startsWith("Bearer ") ? tokens.verify(value.substring(7)) : null; }
    private ResponseCookie refreshCookie(String token) { return ResponseCookie.from("travel_plan_refresh", token).httpOnly(true).secure(true).sameSite("Strict").path("/api/auth").maxAge(900).build(); }
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresAt) { static TokenResponse from(TokenService.IssuedToken t) { return new TokenResponse(t.value(), "Bearer", t.expiresAt()); } }
    public record ErrorResponse(String code, String message) {}
}
