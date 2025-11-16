package com.example.myproject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // ביטול CSRF בזמן פיתוח
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**", "/hello").permitAll() // גישה חופשית ל-H2 ולבדיקה
                        .anyRequest().permitAll()
                )
                .headers(headers -> headers.frameOptions(frame -> frame.disable())); // מאפשר תצוגת קונסולה ב-iFrame

        return http.build();
    }
}
