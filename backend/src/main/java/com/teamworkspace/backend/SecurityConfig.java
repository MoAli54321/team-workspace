package com.teamworkspace.backend;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** Prüft die Anmeldung vor dem Controller; die Teamrechte prüfen anschließend die Controller selbst. */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Der Browser sendet den JWT ausdrücklich im Authorization-Header.
                // Da keine Cookie-Anmeldung verwendet wird, benötigt diese API keinen CSRF-Schutz.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // Das Frontend meldet lokal ab, indem es den gespeicherten Token entfernt.
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/tasks", "/api/tasks/**",
                                "/api/projects", "/api/projects/**",
                                "/api/teams", "/api/teams/**"
                        ).authenticated()
                        // Alle übrigen Pfade bleiben öffentlich, darunter Login, Registrierung und Health-Check.
                        .anyRequest().permitAll())
                // Liest Authorization: Bearer <token> und nutzt den JwtDecoder (JwtService).
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .build();
    }
}
