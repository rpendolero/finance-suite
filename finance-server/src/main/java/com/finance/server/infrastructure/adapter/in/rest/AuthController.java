package com.finance.server.infrastructure.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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

  public record LoginRequest(String username, String password) {}
  public record UserSession(String username, List<String> roles) {}

  @PostMapping("/login")
  public UserSession login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
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
    return new UserSession(authentication.getName(),
        authentication.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).toList());
  }
}
