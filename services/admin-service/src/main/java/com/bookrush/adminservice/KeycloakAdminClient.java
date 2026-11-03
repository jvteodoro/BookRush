package com.bookrush.adminservice;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Service-account client for the product realm's user lifecycle. */
@Component
public class KeycloakAdminClient {
  private final RestClient http;
  private final RestClient tokenHttp;
  private final String tokenUri;
  private final String clientId;
  private final String clientSecret;
  private final boolean configured;

  public KeycloakAdminClient(
      RestClient.Builder builder,
      @Value("${bookrush.keycloak.token-uri:}") String tokenUri,
      @Value("${bookrush.keycloak.client-id:}") String clientId,
      @Value("${bookrush.keycloak.client-secret:}") String clientSecret,
      @Value("${bookrush.keycloak.admin-base:}") String adminBase) {
    this.http = builder.clone().baseUrl(adminBase).build();
    this.tokenHttp = builder.clone().build();
    this.tokenUri = tokenUri;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.configured = !tokenUri.isBlank() && !clientId.isBlank()
        && !clientSecret.isBlank() && !adminBase.isBlank();
  }

  public boolean configured() {
    return configured;
  }

  @SuppressWarnings("unchecked")
  private String serviceToken() {
    if (!configured) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Keycloak user management is not configured");
    }
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    Map<String, Object> response = tokenHttp.post().uri(tokenUri)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
        .retrieve().body(Map.class);
    Object token = response == null ? null : response.get("access_token");
    if (!(token instanceof String value) || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak service token was not issued");
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> users(String search, int page, int size) {
    List<Map<String, Object>> users = http.get().uri(uri -> {
      var query = uri.path("/users").queryParam("briefRepresentation", true)
          .queryParam("first", page * size).queryParam("max", size);
      if (search != null && !search.isBlank()) query.queryParam("search", search.trim());
      return query.build();
    }).headers(h -> h.setBearerAuth(serviceToken())).retrieve().body(List.class);
    return users == null ? List.of() : users;
  }

  public long count(String search) {
    Long count = http.get().uri(uri -> {
      var query = uri.path("/users/count");
      if (search != null && !search.isBlank()) query.queryParam("search", search.trim());
      return query.build();
    }).headers(h -> h.setBearerAuth(serviceToken())).retrieve().body(Long.class);
    return count == null ? 0 : count;
  }

  public void setEnabled(String userId, boolean enabled) {
    http.put().uri("/users/{id}", userId).headers(h -> h.setBearerAuth(serviceToken()))
        .contentType(MediaType.APPLICATION_JSON).body(Map.of("enabled", enabled))
        .retrieve().toBodilessEntity();
    if (!enabled) {
      http.post().uri("/users/{id}/logout", userId).headers(h -> h.setBearerAuth(serviceToken()))
          .retrieve().toBodilessEntity();
    }
  }
}
