package com.travelplan.users;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
@Component
public class RevocationClient {
 private final RestClient client; private final String token;
 public RevocationClient(@Value("${services.auth.url:http://auth-service:8081}") String url,@Value("${security.internal-service-token:change-me-internal}") String token){client=RestClient.builder().baseUrl(url).build();this.token=token;}
 public void revoke(UUID userId){try{client.post().uri("/internal/revoke/{id}",userId).header("X-Internal-Service-Token",token).retrieve().toBodilessEntity();}catch(RuntimeException e){throw new UserStore.Conflict("REVOCATION_UNAVAILABLE");}}
}
