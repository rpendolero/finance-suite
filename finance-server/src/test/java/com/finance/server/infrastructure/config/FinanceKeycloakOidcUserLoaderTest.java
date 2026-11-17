package com.finance.server.infrastructure.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FinanceKeycloakOidcUserLoaderTest {
    @Test
    void separateClientRolesCannotAccessFinancialInformation() {
        assertTrue(FinanceKeycloakOidcUserLoader.extractRoles(Map.of()).isEmpty());
        assertTrue(FinanceKeycloakOidcUserLoader.extractRoles(
                Map.of("resource_access", Map.of("clima-home", Map.of("roles", List.of("ADMIN")))))
                .isEmpty());
    }

    @Test
    void readerCannotWriteAndFinanceAdminRetainsAllExistingPermissions() {
        assertEquals(Set.of("READER"), FinanceKeycloakOidcUserLoader.extractRoles(
                Map.of("resource_access", Map.of("finance-suite", Map.of("roles", List.of("READER"))))));
        assertEquals(Set.of("ADMIN", "READER", "IMPORTER"),
                FinanceKeycloakOidcUserLoader.extractRoles(
                    Map.of("resource_access", Map.of("finance-suite",
                            Map.of("roles", List.of("ADMIN", "UNKNOWN"))))));
    }
}
