package com.bookrush.socialservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class SocialTokenValidationTest {
  @Test
  void validatesSignatureIssuerAudienceAndTokenLifetime() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
        .privateKey((RSAPrivateKey) pair.getPrivate()).keyID("disposable-test-key").build();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
    server.createContext("/jwks", exchange -> {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, jwks.length);
      try (var body = exchange.getResponseBody()) { body.write(jwks); }
    });
    server.start();
    try {
      var decoder = new SocialSecurityConfiguration().jwtDecoder(
          "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks", "https://issuer.test", "admin-api");
      var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
      var now = Instant.now();
      for (var scenario : List.of("valid", "expired", "future", "issuer", "audience")) {
        var claims = JwtClaimsSet.builder().subject("operator-1")
            .issuer(scenario.equals("issuer") ? "https://wrong.test" : "https://issuer.test")
            .audience(List.of(scenario.equals("audience") ? "wrong-api" : "admin-api"))
            .issuedAt(now.minusSeconds(900))
            .notBefore(scenario.equals("future") ? now.plusSeconds(300) : now.minusSeconds(900))
            .expiresAt(scenario.equals("expired") ? now.minusSeconds(300) : now.plusSeconds(600))
            .build();
        var token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        if (scenario.equals("valid")) assertEquals("operator-1", decoder.decode(token).getSubject());
        else assertThrows(JwtException.class, () -> decoder.decode(token), scenario);
      }
    } finally {
      server.stop(0);
    }
  }
}
