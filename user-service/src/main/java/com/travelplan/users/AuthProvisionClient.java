package com.travelplan.users;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Component; import org.springframework.web.client.RestClient;
@Component public class AuthProvisionClient {
 private final RestClient client; private final String token;
 public AuthProvisionClient(RestClient.Builder b,@Value("${auth.service-url:http://auth-service:8081}") String url,@Value("${security.internal-service-token:change-me-internal}") String token){this.client=b.baseUrl(url).build();this.token=token;}
 public void provision(String username,String password){client.post().uri("/api/auth/internal/provision").header("X-Internal-Service-Token",token).body(new Request(username,password)).retrieve().toBodilessEntity();}
 record Request(String username,String password){}
}
