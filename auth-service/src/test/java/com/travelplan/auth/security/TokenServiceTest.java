package com.travelplan.auth.security;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TokenServiceTest {
    private static final String SECRET = "01234567890123456789012345678901";

    @Test void issuedTokenVerifiesAndTamperingFails() {
        TokenService service = new TokenService(SECRET, 60);
        TokenService.IssuedToken issued = service.issue("admin", "ADMIN");
        assertEquals("ADMIN", service.verify(issued.value()).role());
        String tampered = "A" + issued.value().substring(1);
        assertNull(service.verify(tampered));
    }

    @Test void logoutRevokesExistingToken() {
        TokenService service = new TokenService(SECRET, 60);
        String token = service.issue("admin", "ADMIN").value();
        assertNotNull(service.verify(token));
        service.revoke(token);
        assertNull(service.verify(token));
    }

    @Test void expiredTokenIsRejected() {
        TokenService service = new TokenService(SECRET, 0);
        assertNull(service.verify(service.issue("admin", "ADMIN").value()));
    }

    @Test void disablingSubjectRevokesAllIssuedTokens() {
        TokenService service = new TokenService(SECRET, 60);
        String token = service.issue("user-1", "ADMIN").value();
        service.revokeSubject("user-1");
        assertNull(service.verify(token));
    }
}
