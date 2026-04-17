package com.assine.billing.config;

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
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health").permitAll()
                // Stripe webhooks authenticate via HMAC signature in the request body,
                // not via OAuth2 JWT — skip the resource-server check for this path.
                .requestMatchers(HttpMethod.POST, "/webhooks/stripe").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/**").hasAuthority("SCOPE_billing:write")
                .requestMatchers(HttpMethod.PUT, "/api/v1/**").hasAuthority("SCOPE_billing:write")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasAuthority("SCOPE_billing:write")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
