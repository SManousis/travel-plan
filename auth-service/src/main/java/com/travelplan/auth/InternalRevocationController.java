package com.travelplan.auth;
import com.travelplan.auth.security.TokenService; import org.springframework.beans.factory.annotation.Value; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestController
public class InternalRevocationController {
 private final TokenService tokens; private final String serviceToken;
 public InternalRevocationController(TokenService t,@Value("${security.internal-service-token:change-me-internal}") String s){tokens=t;serviceToken=s;}
 @PostMapping("/internal/revoke/{subject}") ResponseEntity<Void> revoke(@PathVariable String subject,@RequestHeader(value="X-Internal-Service-Token",required=false)String presented){if(!serviceToken.equals(presented))return ResponseEntity.status(403).build();tokens.revokeSubject(subject);return ResponseEntity.noContent().build();}
}
