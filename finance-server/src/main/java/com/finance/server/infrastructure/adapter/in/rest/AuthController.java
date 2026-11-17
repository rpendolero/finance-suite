package com.finance.server.infrastructure.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import java.util.Map;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthenticationManager authenticationManager;
  private final Environment environment;

  public record LoginRequest(String username, String password) {}
  public record LoginConfiguration(boolean keycloakEnabled) {}

  @GetMapping("/config")
  public LoginConfiguration config() {
    return new LoginConfiguration(environment.acceptsProfiles(Profiles.of("keycloak")));
  }
  public record UserSession(String username, List<String> roles) {}

  @PostMapping("/login")
  public UserSession login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    if (environment.acceptsProfiles(Profiles.of("keycloak"))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "La autenticación local está desactivada; utiliza Keycloak.");
    }
    try {
      Authentication authentication = authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      HttpSession session = servletRequest.getSession(true);
      session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
      return session(authentication);
    } catch (org.springframework.security.core.AuthenticationException ex) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
    }
  }

  @GetMapping("/me")
  public UserSession me(Authentication authentication) { return session(authentication); }

  @PostMapping("/logout")
  public void logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
  }

  private UserSession session(Authentication authentication) {
    String username = authentication.getPrincipal() instanceof OidcUser oidc
        ? oidc.getEmail() : authentication.getName();
    return new UserSession(username,
        authentication.getAuthorities().stream().map(a -> a.getAuthority())
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring("ROLE_".length())).toList());
  }
}
