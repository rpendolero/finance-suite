package com.finance.server;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finance.domain.ClassificationRule;
import com.finance.domain.Movement;
import com.finance.server.application.port.*;
import com.finance.server.application.service.*;
import com.finance.server.infrastructure.adapter.in.mcp.ClassificationTools;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ClassificationToolsTest {
  final LedgerPort ledger = mock(LedgerPort.class);
  final SettingsPort settings = mock(SettingsPort.class);
  final CategoryCatalogPort catalog = mock(CategoryCatalogPort.class);
  final CategoryCatalogService categories = new CategoryCatalogService(catalog);
  final MerchantNormalizationService merchants = new MerchantNormalizationService();
  final ClassificationManagementService management = new ClassificationManagementService(
      ledger, settings, new MovementClassificationService(merchants, categories, new MovementKindDetectionService()),
      categories, merchants);
  final ClassificationTools tools = new ClassificationTools(management, settings);

  @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

  void authenticate(String role) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("user", "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
  }

  @Test void readerCannotUseAnyWriteTool() {
    authenticate("READER");
    assertThatThrownBy(() -> tools.classify("id", "OTROS", "OTROS", Movement.Kind.NORMAL, false, false))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> tools.saveRule(null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(tools::reclassify).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(ledger, settings, catalog);
  }

  @Test void missingAuthenticationFailsClosed() {
    assertThatThrownBy(tools::rules).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(tools::reclassify).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(ledger, settings);
  }

  @Test void readerCanReadCatalogRulesAndPendingMovements() {
    authenticate("READER");
    when(catalog.findAllActive()).thenReturn(List.of());
    when(settings.rules()).thenReturn(List.of());
    when(ledger.movements(any(), isNull())).thenReturn(List.of());
    assertThat(tools.categories()).isEqualTo(List.of());
    assertThat(tools.rules()).isEqualTo(List.of());
    assertThat(tools.unclassified("2026-10-01", "2026-10-31", null, 100)).isEqualTo(List.of());
    verify(ledger, never()).updateClassifications(any());
  }

  @Test void adminCanSaveValidatedRuleAndReclassifyHistoricalMovements() {
    authenticate("ADMIN");
    when(catalog.findActiveByCode("OTROS")).thenReturn(java.util.Optional.of(
        new CategoryCatalogPort.Category("OTROS", "Otros", List.of(new CategoryCatalogPort.Subcategory("OTROS", "Otros")))));
    var rule = new ClassificationRule("test", 20, "SHOP", "OTROS", Movement.Kind.NORMAL);
    tools.saveRule(rule);
    verify(settings).saveRule(any(ClassificationRule.class));
    when(settings.rules()).thenReturn(List.of(rule));
    when(ledger.allMovements()).thenReturn(List.of());
    var result = (ClassificationManagementService.ReclassificationResult) tools.reclassify();
    assertThat(result.scanned()).isZero();
  }

  @Test void callbackRegistrationIncludesAllSixToolsAndEnforcesAuthorization() {
    var callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();
    assertThat(callbacks).extracting(c -> c.getToolDefinition().name()).containsExactlyInAnyOrder(
        "bank_get_categories", "bank_get_rules", "bank_get_unclassified", "bank_classify_movement", "bank_save_rule", "bank_reclassify");
    var callback = java.util.Arrays.stream(callbacks).filter(c -> c.getToolDefinition().name().equals("bank_reclassify")).findFirst().orElseThrow();
    authenticate("READER");
    assertThatThrownBy(() -> callback.call("{}")).hasRootCauseInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(ledger, settings);
  }
}
