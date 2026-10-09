package com.finance.statements;

import com.finance.domain.Product.Provider;
import java.util.List;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/** Format strategy: identifies a workbook and defines its native columns/statuses. */
public interface NativeStatementFormat {
  default Provider provider() {
    return Provider.ING;
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
