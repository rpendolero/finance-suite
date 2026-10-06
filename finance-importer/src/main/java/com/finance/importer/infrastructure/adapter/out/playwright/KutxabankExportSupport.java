package com.finance.importer.infrastructure.adapter.out.playwright;

import com.finance.importer.infrastructure.config.BrowserProperties;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Shared date controls and download action for Kutxabank exports. */
@Slf4j
@Component
@RequiredArgsConstructor
final class KutxabankExportSupport {
  private final KutxabankPageSynchronizer synchronization = new KutxabankPageSynchronizer();

  void validatePeriod(BrowserProperties.Export export) {
    if (export.getFrom() == null
        || export.getTo() == null
        || export.getFrom().isAfter(export.getTo()))
      throw new IllegalArgumentException("Fechas from/to válidas requeridas");
  }

  void search(Page page, BrowserProperties.Export export) {
    synchronization.awaitReady(page);

    fillPeriod(page, export.getFrom(), export.getTo());

    log.info(
            "Kutxabank statement period ready: from={}, to={}",
            export.getFrom(),
            export.getTo());

    synchronization.execute(
            page,
            "search-statements",
            () ->
                    page.getByRole(
                                    AriaRole.LINK,
                                    new Page.GetByRoleOptions()
                                            .setName("MOSTRAR")
                                            .setExact(true))
                            .click());

    log.info("Kutxabank statement search submitted");
  }

  private void fillPeriod(Page page, LocalDate from, LocalDate to) {
    for (int attempt = 1; attempt <= 5; attempt++) {

      boolean fromValid = isDate(page, "Desde", from);
      boolean toValid = isDate(page, "Hasta", to);

      log.info(
              "Kutxabank period state before repair: "
                      + "fromValid={}, toValid={}, attempt={}",
              fromValid,
              toValid,
              attempt);

      if (fromValid && toValid) {
        log.info(
                "Kutxabank statement period successfully set: from={}, to={}",
                from,
                to);
        return;
      }

      /*
       * Important:
       * Fill only the date that is currently invalid.
       *
       * ICEFaces can re-render the opposite date while processing
       * the current one, so both dates must be checked again on
       * the next iteration.
       */
      if (!fromValid) {
        fillDate(page, "Desde", from);
      }

      // Re-check after filling Desde because ICEFaces may have
      // changed the Hasta component.
      fromValid = isDate(page, "Desde", from);
      toValid = isDate(page, "Hasta", to);

      if (fromValid && toValid) {
        logPeriod(page, "period stable");
        return;
      }

      if (!toValid) {
        fillDate(page, "Hasta", to);
      }

      logPeriod(page, "after repair attempt");
    }

    logPeriod(page, "final invalid period");

    throw new IllegalStateException(
            "Unable to stabilize Kutxabank statement period: from="
                    + from
                    + ", to="
                    + to);
  }

  private void fillAndVerifyDate(
          Page page,
          String direction,
          LocalDate date) {

    for (int attempt = 1; attempt <= 3; attempt++) {

      fillDate(page, direction, date);

      if (isDate(page, direction, date)) {
        log.info(
                "Kutxabank {} date successfully set: {}, attempt={}",
                direction,
                date,
                attempt);
        return;
      }

      log.warn(
              "Kutxabank {} date was not retained: date={}, attempt={}",
              direction,
              date,
              attempt);
    }

    throw new IllegalStateException(
            "Unable to set Kutxabank "
                    + direction
                    + " date to "
                    + date);
  }
  private void logPeriod(Page page, String moment) {
    log.info(
            "Kutxabank period [{}]: Desde={}/{}/{}, Hasta={}/{}/{}",
            moment,
            value(page, "Desde", "dias"),
            value(page, "Desde", "mes"),
            value(page, "Desde", "anyo"),
            value(page, "Hasta", "dias"),
            value(page, "Hasta", "mes"),
            value(page, "Hasta", "anyo"));
  }

  private String value(
          Page page,
          String direction,
          String part) {

    String id =
            "formCriterios:calendario"
                    + direction
                    + "_cmb_"
                    + part;

    return page.locator("[id=\"" + id + "\"]").inputValue();
  }

  private boolean isDate(Page page, String direction, LocalDate expected) {
    String prefix = "formCriterios:calendario" + direction + "_cmb_";

    String day =
            page.locator("[id=\"" + prefix + "dias\"]").inputValue();

    String month =
            page.locator("[id=\"" + prefix + "mes\"]").inputValue();

    String year =
            page.locator("[id=\"" + prefix + "anyo\"]").inputValue();

    String expectedDay =
            String.format(Locale.ROOT, "%02d", expected.getDayOfMonth());

    String expectedMonth =
            String.format(Locale.ROOT, "%02d", expected.getMonthValue());

    String expectedYear =
            Integer.toString(expected.getYear());

    boolean valid =
            expectedDay.equals(day)
                    && expectedMonth.equals(month)
                    && expectedYear.equals(year);

    if (!valid) {
      log.warn(
              "Kutxabank date mismatch: direction={}, expected={}/{}/{}, actual={}/{}/{}",
              direction,
              expectedDay,
              expectedMonth,
              expectedYear,
              day,
              month,
              year);
    }

    return valid;
  }

  private void fillDate(Page page, String direction, LocalDate date) {
    String prefix = "formCriterios:calendario" + direction + "_cmb_";

    String day = String.format(Locale.ROOT, "%02d", date.getDayOfMonth());
    String month = String.format(Locale.ROOT, "%02d", date.getMonthValue());
    String year = Integer.toString(date.getYear());

    log.info(
            "Filling Kutxabank {} date: {}",
            direction,
            date);

    fillDatePart(page, prefix + "dias", day);
    fillDatePart(page, prefix + "mes", month);
    fillDatePart(page, prefix + "anyo", year);

    log.info(
            "Kutxabank {} immediately after fill: {}/{}/{}",
            direction,
            value(page, direction, "dias"),
            value(page, direction, "mes"),
            value(page, direction, "anyo"));
  }
  private void fillDatePart(
          Page page,
          String id,
          String expectedValue) {

    for (int attempt = 1; attempt <= 3; attempt++) {

      // Important: resolve the locator again because ICEFaces may
      // have replaced the input element.
      Locator input = page.locator("[id=\"" + id + "\"]");

      input.waitFor(
              new Locator.WaitForOptions()
                      .setState(WaitForSelectorState.VISIBLE)
                      .setTimeout(30_000));

      input.fill(expectedValue);

      // Resolve it again after fill. The previous DOM node may
      // already have been replaced by ICEFaces.
      Locator currentInput = page.locator("[id=\"" + id + "\"]");

      String actualValue = currentInput.inputValue();

      log.debug(
              "Kutxabank date part: id={}, expected={}, actual={}, attempt={}",
              id,
              expectedValue,
              actualValue,
              attempt);

      if (expectedValue.equals(actualValue)) {
        return;
      }

      log.warn(
              "Kutxabank date part was reset: id={}, expected={}, actual={}, attempt={}",
              id,
              expectedValue,
              actualValue,
              attempt);
    }

    throw new IllegalStateException(
            "Unable to fill Kutxabank date field "
                    + id
                    + " with value "
                    + expectedValue);
  }
  Download download(Page page) {
    synchronization.awaitReady(page);
    log.info("Kutxabank XLS download requested");
    return page.waitForDownload(
        () ->
            page.getByRole(
                    AriaRole.LINK,
                    new Page.GetByRoleOptions().setName("DESCARGAR XLS").setExact(true))
                .click());
  }

  public void searchPeriod(Page page, BrowserProperties.Export export) {
    synchronization.awaitReady(page);

    var betweenDates = page.getByRole(
            AriaRole.RADIO, new Page.GetByRoleOptions().setName("Entre fechas").setExact(true));
    if (!betweenDates.isChecked()) {
      synchronization.execute(page, "select-between-dates", betweenDates::click);
    }
    search(page, export);
  }

  public void searchMovements(Page page, String elementName) {
    synchronization.execute(page, "open-account-queries", () -> link(page, "Consultas").click());

    var account = page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName(elementName));

    if (!account.isChecked()) {
      synchronization.execute(page, "select-account", account::check);
    }
    var movements = page.locator("span").filter(new Locator.FilterOptions().setHasText(Pattern.compile("^Movimientos$")));
    synchronization.execute(page, "open-account-movements", movements::click);
  }

  public Locator link(Page page, String name) {
    return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
  }
}
