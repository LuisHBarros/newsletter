package com.assine.content.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Notion webhook is authenticated via HMAC in its own controller/filter — no JWT.
                .requestMatchers(HttpMethod.POST, "/webhooks/notion").permitAll()
                // Admin endpoints require content:admin scope
                .requestMatchers("/api/v1/admin/**").hasAuthority("SCOPE_content:admin")
                // Internal jobs (called by EventBridge Scheduler via API Gateway w/ admin token)
                .requestMatchers("/api/v1/internal/**").hasAuthority("SCOPE_content:admin")
                // Public reads (authenticated subscribers)
                .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAuthority("SCOPE_content:read")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
