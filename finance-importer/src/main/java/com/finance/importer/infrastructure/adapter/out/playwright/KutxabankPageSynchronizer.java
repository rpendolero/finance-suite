package com.finance.importer.infrastructure.adapter.out.playwright;

import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

/** Observes ICEFaces status before an action, including short or replaced status elements. */
@Slf4j
public final class KutxabankPageSynchronizer {
  private static final double TIMEOUT_MS = 60_000;
  private static final String READY =
      """
      () => {
        const visible = id => {
          const e = document.getElementById('panelCargando:connection-' + id);
          return !!e && getComputedStyle(e).visibility === 'visible';
        };
        if (visible('trouble') || visible('lost'))
          throw new Error('ICEFaces connection unavailable');
        return visible('idle') && !visible('working');
      }
      """;

  private static final String ARM =
      """
      () => {
        window.__financeKutxaWait?.observer.disconnect();
        const prefix = 'panelCargando:connection-';
        const visible = name => {
          const e = document.getElementById(prefix + name);
          return !!e && getComputedStyle(e).visibility === 'visible';
        };
        if (!document.getElementById(prefix + 'working') ||
            !document.getElementById(prefix + 'idle'))
          throw new Error('ICEFaces status indicators not found');
        const state = { started: false, completed: false, failed: false };
        const update = records => {
          // Mutation delivery can group the visible and hidden changes in one callback.
          for (const record of records) {
            if (record.target.id === prefix + 'working' &&
                record.attributeName === 'style' &&
                /(?:^|;)\\s*visibility\\s*:\\s*visible\\s*(?:;|$)/i.test(record.oldValue || ''))
              state.started = true;
          }
          if (visible('working')) state.started = true;
          state.failed = visible('trouble') || visible('lost');
          state.completed = state.started && !state.failed &&
              !visible('working') && visible('idle');
        };
        state.observer = new MutationObserver(update);
        state.observer.observe(document.documentElement, {
          subtree: true, childList: true, attributes: true, attributeOldValue: true,
          attributeFilter: ['style', 'class']
        });
        window.__financeKutxaWait = state;
        update([]);
      }
      """;

  public void awaitReady(Page page) {
    page.waitForFunction(READY, null, new Page.WaitForFunctionOptions().setTimeout(TIMEOUT_MS));
  }

  public void execute(Page page, String operation, Runnable action) {
    awaitReady(page);
    page.evaluate(ARM);
    log.info("ICEFaces action started: operation={}", operation);
    try {
      action.run();
      page.waitForFunction(
          """
          () => {
            const state = window.__financeKutxaWait;
            if (state?.failed) throw new Error('ICEFaces connection unavailable');
            return state?.completed === true;
          }
          """,
          null,
          new Page.WaitForFunctionOptions().setTimeout(TIMEOUT_MS));
      log.info("ICEFaces action completed: operation={}", operation);
    } catch (RuntimeException failure) {
      log.error(
          "ICEFaces action failed: operation={}, errorType={}",
          operation,
          failure.getClass().getSimpleName());
      throw failure;
    } finally {
      cleanup(page);
    }
  }

  private void cleanup(Page page) {
    if (page.isClosed()) return;
    try {
      page.evaluate(
          """
          () => {
            window.__financeKutxaWait?.observer.disconnect();
            delete window.__financeKutxaWait;
          }
          """);
    } catch (RuntimeException failure) {
      // Cleanup must not mask the original browser/connection failure.
      log.warn(
          "ICEFaces observer cleanup failed: errorType={}", failure.getClass().getSimpleName());
    }
  }
}
