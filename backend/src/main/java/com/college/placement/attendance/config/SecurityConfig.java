package com.college.placement.attendance.config;

import com.college.placement.attendance.security.CustomUserDetailsService;
import com.college.placement.attendance.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(content -> {})
                        .xssProtection(xss -> {})
                )
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**"
                        ).permitAll()

                        // Drives public read or student read
                        .requestMatchers(HttpMethod.GET, "/api/v1/drives/my-eligible").hasAuthority("ROLE_STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/drives/import-template").hasAuthority("ROLE_OFFICER")
                        .requestMatchers("/api/v1/drives/*/imports/**").hasAuthority("ROLE_OFFICER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/drives").hasAuthority("ROLE_OFFICER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/drives/**").hasAuthority("ROLE_OFFICER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/drives/*/schedule").hasAuthority("ROLE_OFFICER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/drives/*/open").hasAuthority("ROLE_OFFICER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/drives/*/close").hasAuthority("ROLE_OFFICER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/drives/*/finalize").hasAuthority("ROLE_OFFICER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/drives/*/attendance/stream").hasAuthority("ROLE_OFFICER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/drives/*/attendance/summary").hasAuthority("ROLE_OFFICER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/drives/*/attendance/records").hasAuthority("ROLE_OFFICER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/drives/*/attendance/corrections").hasAuthority("ROLE_OFFICER")

                        // Reports & Audits
                        .requestMatchers("/api/v1/reports/**").hasAuthority("ROLE_OFFICER")
                        .requestMatchers("/api/v1/audit/**").hasAuthority("ROLE_OFFICER")

                        // Device officer management
                        .requestMatchers("/api/v1/devices/pending").hasAuthority("ROLE_OFFICER")
                        .requestMatchers("/api/v1/devices/*/approve").hasAuthority("ROLE_OFFICER")
                        .requestMatchers("/api/v1/devices/*/revoke").hasAuthority("ROLE_OFFICER")

                        // Authenticated fallback
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:[*]", "http://127.0.0.1:[*]"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Correlation-ID", "Accept"));
        configuration.setExposedHeaders(Arrays.asList("X-Correlation-ID", "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
