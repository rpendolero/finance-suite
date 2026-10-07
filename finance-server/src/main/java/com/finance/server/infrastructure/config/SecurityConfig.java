package com.finance.server.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  UserDetailsService users(
      @Value("${finance.security.reader-password}") String reader,
      @Value("${finance.security.admin-password}") String admin,
      @Value("${finance.security.importer-password}") String importer,
      PasswordEncoder encoder) {
    if (reader.equals(admin)
        || reader.equals(importer)
        || admin.equals(importer))
      throw new IllegalArgumentException(
          "Contraseñas distintas de al menos 20 caracteres requeridas");
    return new InMemoryUserDetailsManager(
        User.withUsername("reader").password(encoder.encode(reader)).roles("READER").build(),
        User.withUsername("importer").password(encoder.encode(importer)).roles("IMPORTER").build(),
        User.withUsername("admin")
            .password(encoder.encode(admin))
            .roles("ADMIN", "READER", "IMPORTER")
            .build());
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.addFilterBefore(
            new OriginFilter(),
            org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class)
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            s ->
                s.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/v1/banking/enable-banking/callback")
                    .permitAll()
                    .requestMatchers("/api/v1/banking/enable-banking/authorizations", "/api/v1/banking/connections/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/importer/**")
                    .hasRole("IMPORTER")
                    .requestMatchers("/mcp", "/mcp/**")
                    .hasRole("READER")
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/**")
                    .hasRole("READER")
                    .requestMatchers("/api/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .denyAll())
        .httpBasic(b -> {})
        .build();
  }
}
