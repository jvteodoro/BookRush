package com.bookrush.adminservice;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Minimal service-account client for the platform realm's user lifecycle. */
@Component
public class KeycloakAdminClient {
  private final RestClient http;
  private final String tokenUri;
  private final String clientId;
  private final String clientSecret;
  private final String adminBase;

  public KeycloakAdminClient(
      @Value("${bookrush.keycloak.token-uri:}") String tokenUri,
      @Value("${bookrush.keycloak.client-id:}") String clientId,
      @Value("${bookrush.keycloak.client-secret:}") String clientSecret,
      @Value("${bookrush.keycloak.admin-base:}") String adminBase) {
    this.http = RestClient.builder().build();
    this.tokenUri = tokenUri;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.adminBase = adminBase;
  }

  public boolean configured() {
    return !tokenUri.isBlank() && !clientId.isBlank() && !clientSecret.isBlank() && !adminBase.isBlank();
  }

  @SuppressWarnings("unchecked")
  private String serviceToken() {
    if (!configured()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Keycloak user lifecycle is not configured");
    }
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    Map<String, Object> response = http.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form).retrieve().body(Map.class);
    Object token = response == null ? null : response.get("access_token");
    if (!(token instanceof String value) || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak service token was not issued");
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> findByUsername(String username) {
    List<Map<String, Object>> users = http.get()
        .uri(adminBase + "/users?username=" + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8))
        .headers(h -> h.setBearerAuth(serviceToken())).retrieve().body(List.class);
    return users == null || users.isEmpty() ? Map.of() : users.getFirst();
  }

  public Map<String, Object> setEnabled(String subjectKey, boolean enabled) {
    http.put().uri(adminBase + "/users/" + java.net.URLEncoder.encode(subjectKey, java.nio.charset.StandardCharsets.UTF_8))
        .headers(h -> h.setBearerAuth(serviceToken())).contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("enabled", enabled)).retrieve().toBodilessEntity();
    return Map.of("subjectKey", subjectKey, "enabled", enabled);
  }

  @SuppressWarnings("unchecked")
  public boolean isEnabled(String subjectKey) {
    Map<String, Object> user = http.get()
        .uri(adminBase + "/users/" + java.net.URLEncoder.encode(subjectKey, java.nio.charset.StandardCharsets.UTF_8))
        .headers(h -> h.setBearerAuth(serviceToken())).retrieve().body(Map.class);
    return user == null || !Boolean.FALSE.equals(user.get("enabled"));
  }
}
