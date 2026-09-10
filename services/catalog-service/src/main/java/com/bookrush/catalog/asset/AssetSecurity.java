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

@Configuration
@Import(StorageConfiguration.class)
@EnableConfigurationProperties(CanonicalServiceProperties.class)
public class AssetSecurity {
  @Bean org.springframework.security.core.userdetails.UserDetailsService noPasswordUsers() {
    return new org.springframework.security.provisioning.InMemoryUserDetailsManager();
  }
  @Bean SecurityFilterChain security(HttpSecurity http,StorageProperties props,CanonicalServiceProperties canonical) throws Exception {
    var filter=new OncePerRequestFilter() {
      protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String auth=request.getHeader("Authorization");
        // Internal service calls may use a dedicated header so a gateway cannot
        // accidentally reinterpret the service credential as a user bearer token.
        String serviceToken = request.getHeader("X-Canonical-Service-Token");
        if(props.enabled() && auth!=null && auth.startsWith("Bearer ")
            && MessageDigest.isEqual(auth.substring(7).getBytes(StandardCharsets.UTF_8),props.adminToken().getBytes(StandardCharsets.UTF_8))) {
          SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
              "asset-admin",null,List.of(new SimpleGrantedAuthority("ROLE_ASSET_ADMIN"))));
        }
        String presentedServiceToken = serviceToken != null ? serviceToken
            : (auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null);
        if (presentedServiceToken != null
            && MessageDigest.isEqual(presentedServiceToken.getBytes(StandardCharsets.UTF_8), canonical.token().getBytes(StandardCharsets.UTF_8))) {
          SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
              "catalog-service", null, List.of(new SimpleGrantedAuthority("ROLE_CATALOG_SERVICE"))));
        }
        chain.doFilter(request,response);
      }
    };
    // Only explicit bearer headers authenticate; no cookie/session/Basic authentication.
    return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(c->c.disable()).addFilterBefore(filter,AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(a->a
          .requestMatchers("/api/admin/**","/actuator/metrics","/actuator/metrics/**").hasRole("ASSET_ADMIN")
          .requestMatchers("/api/internal/v1/catalog/**").hasRole("CATALOG_SERVICE")
          .requestMatchers(HttpMethod.GET,"/api","/api/","/api/status","/actuator/health","/actuator/health/**","/api/books/*/assets/*/download-url","/swagger-ui.html","/swagger-ui/**","/v3/api-docs/**").permitAll()
          .anyRequest().denyAll())
        .exceptionHandling(e->e.authenticationEntryPoint((req,res,error)->res.sendError(401))
            .accessDeniedHandler((req,res,error)->res.sendError(403))).build();
  }
}
