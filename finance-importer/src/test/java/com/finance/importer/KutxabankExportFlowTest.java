package com.finance.importer;

import static org.assertj.core.api.Assertions.*;

import com.finance.domain.Product.Provider;
import com.finance.importer.infrastructure.adapter.out.playwright.KutxabankAccountExportFlow;
import com.finance.importer.infrastructure.config.BrowserProperties;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class KutxabankExportFlowTest {
  @Test
  void requiresExplicitAccountAndOrderedDates() {
    var flow = new KutxabankAccountExportFlow();
    var export = new BrowserProperties.Export();
    export.setFlow("KUTXABANK_ACCOUNT");
    assertThat(flow.supports(Provider.KUTXABANK, export)).isTrue();
    assertThat(flow.supports(Provider.ING, export)).isFalse();
    assertThatThrownBy(() -> flow.validate(export)).isInstanceOf(IllegalArgumentException.class);
    export.setAccountName("Test account");
    export.setFrom(LocalDate.of(2026, 9, 1));
    export.setTo(LocalDate.of(2026, 10, 1));
    assertThatCode(() -> flow.validate(export)).doesNotThrowAnyException();
    export.setTo(LocalDate.of(2026, 8, 1));
    assertThatThrownBy(() -> flow.validate(export)).isInstanceOf(IllegalArgumentException.class);
  }
}
