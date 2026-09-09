package com.bookrush.catalog.asset;

import com.bookrush.catalog.storage.StorageProperties;
import com.bookrush.catalog.storage.StorageConfiguration;
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

@Configuration
@Import(StorageConfiguration.class)
public class AssetSecurity {
  @Bean org.springframework.security.core.userdetails.UserDetailsService noPasswordUsers() {
    return new org.springframework.security.provisioning.InMemoryUserDetailsManager();
  }
  @Bean SecurityFilterChain security(HttpSecurity http,StorageProperties props) throws Exception {
    var filter=new OncePerRequestFilter() {
      protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String auth=request.getHeader("Authorization");
        if(props.enabled() && auth!=null && auth.startsWith("Bearer ")
            && MessageDigest.isEqual(auth.substring(7).getBytes(StandardCharsets.UTF_8),props.adminToken().getBytes(StandardCharsets.UTF_8))) {
          SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
              "asset-admin",null,List.of(new SimpleGrantedAuthority("ROLE_ASSET_ADMIN"))));
        }
        chain.doFilter(request,response);
      }
    };
    // Only explicit bearer headers authenticate; no cookie/session/Basic authentication.
    return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(c->c.disable()).addFilterBefore(filter,AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(a->a
          .requestMatchers("/api/admin/**","/actuator/metrics","/actuator/metrics/**").hasRole("ASSET_ADMIN")
          .requestMatchers(HttpMethod.GET,"/api/status","/actuator/health","/actuator/health/**","/api/books/*/assets/*/download-url").permitAll()
          .anyRequest().denyAll())
        .exceptionHandling(e->e.authenticationEntryPoint((req,res,error)->res.sendError(401))
            .accessDeniedHandler((req,res,error)->res.sendError(403))).build();
  }
}
