package com.bookrush.catalog.asset;

import com.bookrush.catalog.storage.StorageProperties;
import com.bookrush.catalog.storage.StorageConfiguration;
import com.bookrush.catalog.canonical.CanonicalServiceProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
@Import(StorageConfiguration.class)
@EnableConfigurationProperties({CanonicalServiceProperties.class, CatalogOidcProperties.class})
public class AssetSecurity {
  @Bean org.springframework.security.core.userdetails.UserDetailsService noPasswordUsers() {
    return new org.springframework.security.provisioning.InMemoryUserDetailsManager();
  }
  @Bean SecurityFilterChain security(HttpSecurity http,StorageProperties props,CanonicalServiceProperties canonical, CatalogOidcProperties oidc) throws Exception {
    var filter=new OncePerRequestFilter() {
      protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String auth=request.getHeader("Authorization");
        // Internal service calls may use a dedicated header so a gateway cannot
        // accidentally reinterpret the service credential as a user bearer token.
        String serviceToken = request.getHeader("X-Canonical-Service-Token");
        if(oidc.legacyStaticEnabled() && props.enabled() && auth!=null && auth.startsWith("Bearer ")
            && MessageDigest.isEqual(auth.substring(7).getBytes(StandardCharsets.UTF_8),props.adminToken().getBytes(StandardCharsets.UTF_8))) {
          SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
              "asset-admin",null,List.of(new SimpleGrantedAuthority("ROLE_ASSET_ADMIN"))));
        }
        String presentedServiceToken = serviceToken != null ? serviceToken
            : (auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null);
        if (oidc.legacyStaticEnabled() && presentedServiceToken != null
            && MessageDigest.isEqual(presentedServiceToken.getBytes(StandardCharsets.UTF_8), canonical.token().getBytes(StandardCharsets.UTF_8))) {
          SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
              "catalog-service", null, List.of(new SimpleGrantedAuthority("ROLE_CATALOG_SERVICE"))));
        }
        chain.doFilter(request,response);
      }
    };
    // Only explicit bearer headers authenticate; no cookie/session/Basic authentication.
    http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(c->c.disable()).addFilterBefore(filter,AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(a->a
          .requestMatchers("/api/admin/**","/actuator/metrics","/actuator/metrics/**")
            .hasAnyAuthority("ROLE_ASSET_ADMIN", "SCOPE_bookrush.assets.write")
          .requestMatchers("/api/internal/v1/catalog/**")
            .hasAnyAuthority("ROLE_CATALOG_SERVICE", "SCOPE_bookrush.catalog.write")
          .requestMatchers(HttpMethod.GET,"/api","/api/","/api/status","/actuator/health","/actuator/health/**","/api/books/*/assets/*/download-url","/swagger-ui.html","/swagger-ui/**","/v3/api-docs/**").permitAll()
          .anyRequest().denyAll())
        .exceptionHandling(e->e.authenticationEntryPoint((req,res,error)->res.sendError(401))
            .accessDeniedHandler((req,res,error)->res.sendError(403)));
    if (oidc.enabled()) {
      http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(catalogRolesConverter())));
    }
    return http.build();
  }

  private Converter<Jwt, ? extends AbstractAuthenticationToken> catalogRolesConverter() {
    return jwt -> {
      var authorities = new java.util.ArrayList<GrantedAuthority>();
      Object realm = jwt.getClaims().get("realm_access");
      if (realm instanceof java.util.Map<?, ?> map && map.get("roles") instanceof List<?> roles) {
        roles.stream().filter(String.class::isInstance)
            .map(String.class::cast).map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .forEach(authorities::add);
      }
      Object roles = jwt.getClaims().get("roles");
      if (roles instanceof List<?> values) {
        values.stream().filter(String.class::isInstance)
            .map(String.class::cast).map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .forEach(authorities::add);
      }
      Object scopeClaim = jwt.getClaims().get("scope");
      if (scopeClaim instanceof String scope) {
        for (String value : scope.split("\\s+")) {
          if (!value.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));
        }
      } else if (scopeClaim instanceof java.util.Collection<?> values) {
        values.forEach(value -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + value)));
      }
      // The decoder already enforces the configured audience. Map that
      // validated technical audience to the explicit internal authority so
      // client-credentials tokens remain stable across scope claim formats.
      if (jwt.getAudience() != null && jwt.getAudience().contains("bookrush-catalog-admin")) {
        authorities.add(new SimpleGrantedAuthority("ROLE_CATALOG_SERVICE"));
      }
      return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    };
  }
}
