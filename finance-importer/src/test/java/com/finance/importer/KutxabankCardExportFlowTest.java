package com.finance.importer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finance.domain.Product.Provider;
import com.finance.importer.infrastructure.adapter.out.playwright.KutxabankCardExportFlow;
import com.finance.importer.infrastructure.config.BrowserProperties;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class KutxabankCardExportFlowTest {
  @Test
  void waitsForOneDownloadAndFillsConfiguredDates() {
    var page = mock(Page.class);
    var control = mock(Locator.class);
    var download = mock(Download.class);
    when(page.getByRole(any(AriaRole.class), any(Page.GetByRoleOptions.class))).thenReturn(control);
    when(page.getByText(anyString(), any(Page.GetByTextOptions.class))).thenReturn(control);
    when(page.locator(anyString())).thenReturn(control);
    when(control.filter(any(Locator.FilterOptions.class))).thenReturn(control);
    when(page.waitForDownload(any(Runnable.class)))
        .thenAnswer(
            call -> {
              ((Runnable) call.getArgument(0)).run();
              return download;
            });
    var export = new BrowserProperties.Export();
    export.setFlow("KUTXABANK_CARD");
    export.setFrom(LocalDate.of(2026, 9, 1));
    export.setTo(LocalDate.of(2026, 10, 2));
    var flow = new KutxabankCardExportFlow();
    flow.validate(export);
    assertThat(flow.supports(Provider.KUTXABANK, export)).isTrue();
    assertThat(flow.supports(Provider.ING, export)).isFalse();
    assertThat(flow.download(page, Provider.KUTXABANK, export)).isSameAs(download);
    verify(page, times(1)).waitForDownload(any(Runnable.class));
    verify(control).fill("09");
    verify(control).fill("10");
    verify(control).fill("02");
    verify(control, times(2)).fill("2026");
  }

  @Test
  void rejectsMissingOrInvertedDates() {
    var flow = new KutxabankCardExportFlow();
    var export = new BrowserProperties.Export();
    assertThatThrownBy(() -> flow.validate(export)).isInstanceOf(IllegalArgumentException.class);
    export.setFrom(LocalDate.of(2026, 10, 1));
    export.setTo(LocalDate.of(2026, 9, 1));
    assertThatThrownBy(() -> flow.validate(export)).isInstanceOf(IllegalArgumentException.class);
  }
}
