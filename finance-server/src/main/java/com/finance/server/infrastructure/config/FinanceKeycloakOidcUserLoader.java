package com.finance.server.infrastructure.config;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * SSO does not grant access to financial data by default.
 * Finance client roles are isolated from Atlas/Clima roles.
 */
@Slf4j
@Component
public class FinanceKeycloakOidcUserLoader extends OidcUserService {
    private static final String CLIENT = "finance-suite";
    private static final Set<String> ALLOWED = Set.of("READER", "IMPORTER", "ADMIN");

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser user = super.loadUser(request);
        Set<String> roles = extractRoles(user.getIdToken().getClaims());
        if (!Boolean.TRUE.equals(user.getEmailVerified()) || roles.isEmpty()) {
            log.warn("Finance SSO rejected: unverified email or missing Finance client role");
            throw new OAuth2AuthenticationException(new OAuth2Error("finance_role_required"),
                    "Verified Keycloak identity with Finance Suite roles is required");
        }
        Set<GrantedAuthority> authorities = new HashSet<>(user.getAuthorities());
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        log.info("Finance SSO authorized with roles={}", roles);
        return user.getUserInfo() != null
                ? new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), "sub")
                : new DefaultOidcUser(authorities, user.getIdToken(), "sub");
    }

    static Set<String> extractRoles(Map<String, Object> claims) {
        Object access = claims.get("resource_access");
        if (!(access instanceof Map<?, ?> resources)) return Set.of();
        Object entry = resources.get(CLIENT);
        if (!(entry instanceof Map<?, ?> values)) return Set.of();
        Object raw = values.get("roles");
        if (!(raw instanceof Iterable<?> entries)) return Set.of();
        Set<String> roles = new HashSet<>();
        entries.forEach(value -> {
            if (value instanceof String role && ALLOWED.contains(role)) roles.add(role);
        });
        // Preserve the existing Finance Suite role semantics for administrators.
        if (roles.contains("ADMIN")) roles.addAll(Set.of("READER", "IMPORTER"));
        return Set.copyOf(roles);
    }
}
