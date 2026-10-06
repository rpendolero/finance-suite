package com.finance.importer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.finance.importer.infrastructure.adapter.out.playwright.KutxabankPageSynchronizer;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;

class KutxabankSynchronizationTest {
  @Test
  void registersObserverBeforeActionAndCleansUpAfterCompletion() {
    var page = mock(Page.class);
    Runnable action = mock(Runnable.class);
    new KutxabankPageSynchronizer().execute(page, "test-action", action);
    var order = inOrder(page, action);
    order
        .verify(page)
        .waitForFunction(anyString(), isNull(), any(Page.WaitForFunctionOptions.class));
    order.verify(page).evaluate(contains("new MutationObserver"));
    order.verify(action).run();
    order
        .verify(page)
        .waitForFunction(
            contains("state?.completed"), isNull(), any(Page.WaitForFunctionOptions.class));
    order.verify(page).isClosed();
    order.verify(page).evaluate(contains("delete window.__financeKutxaWait"));
  }

  @Test
  void cleanupFailureDoesNotHideActionFailure() {
    var page = mock(Page.class);
    var original = new IllegalStateException("test failure");
    when(page.evaluate(contains("delete window.__financeKutxaWait")))
        .thenThrow(new IllegalStateException("cleanup failure"));
    assertThatThrownBy(
            () ->
                new KutxabankPageSynchronizer()
                    .execute(
                        page,
                        "test-action",
                        () -> {
                          throw original;
                        }))
        .isSameAs(original);
  }

  @Test
  void doesNotEvaluateCleanupInAClosedPage() {
    var page = mock(Page.class);
    when(page.isClosed()).thenReturn(true);
    new KutxabankPageSynchronizer().execute(page, "test-action", () -> {});
    verify(page, never()).evaluate(contains("delete window.__financeKutxaWait"));
  }
}
