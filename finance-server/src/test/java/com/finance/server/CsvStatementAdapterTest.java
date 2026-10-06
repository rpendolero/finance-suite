package com.finance.server;

import static org.assertj.core.api.Assertions.*;

import com.finance.server.infrastructure.adapter.out.csv.CsvStatementAdapter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CsvStatementAdapterTest {
  String header = "external_id;date;amount;currency;description;merchant;category;kind;status\n";

  InputStream input(String s) {
    return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void parsesQuotedDescriptionsAndUnclassified() {
    var ms =
        new CsvStatementAdapter()
            .parse(
                input(header + "id;2026-09-01;-10.50;EUR;\"Compra; pan\";Shop;;NORMAL;BOOKED\n"),
                "account");
    assertThat(ms).hasSize(1);
    assertThat(ms.get(0).description()).isEqualTo("Compra; pan");
    assertThat(ms.get(0).category()).isEqualTo("UNCLASSIFIED");
  }

  @Test
  void duplicateIdentifiersAbortImport() {
    var row = "id;2026-09-01;-10.50;EUR;Compra;Shop;;NORMAL;BOOKED\n";
    assertThatThrownBy(() -> new CsvStatementAdapter().parse(input(header + row + row), "account"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAmbiguousEuropeanAmountInsteadOfGuessing() {
    assertThatThrownBy(
            () ->
                new CsvStatementAdapter()
                    .parse(
                        input(header + "id;2026-09-01;-10,50;EUR;Compra;Shop;;NORMAL;BOOKED\n"),
                        "account"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
