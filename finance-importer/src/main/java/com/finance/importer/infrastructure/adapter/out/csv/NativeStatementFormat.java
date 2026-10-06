package com.finance.importer.infrastructure.adapter.out.csv;

import java.util.List;
import org.apache.poi.ss.usermodel.*;

/** Format strategy: identifies a workbook and defines its native columns/statuses. */
public interface NativeStatementFormat {
  default com.finance.domain.Product.Provider provider() {
    return com.finance.domain.Product.Provider.ING;
  }

  default int dateColumn() {
    return 0;
  }

  default int descriptionColumn() {
    return 3;
  }

  default Integer categoryColumn() {
    return 1;
  }

  default boolean textDates() {
    return false;
  }

  String sheetName();

  List<String> headers();

  int amountColumn();

  Integer balanceColumn();

  String identifierPrefix();

  default String normalizeDescription(String text) {
    return text.trim();
  }

  default void validateTitle(Sheet sheet, DataFormatter formatter) {}

  default String movementStatus(Row row, DataFormatter formatter) {
    return "BOOKED";
  }
}
