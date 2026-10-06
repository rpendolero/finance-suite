package com.finance.server.infrastructure.adapter.out.csv;

import com.finance.domain.Movement;
import com.finance.server.application.port.StatementParserPort;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Component;

@Component
public class CsvStatementAdapter implements StatementParserPort {
  public List<Movement> parse(InputStream input, String productId) {
    try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        var csv =
            CSVFormat.DEFAULT
                .builder()
                .setDelimiter(';')
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .get()
                .parse(reader)) {
      for (String h :
          List.of(
              "external_id",
              "date",
              "amount",
              "currency",
              "description",
              "merchant",
              "category",
              "kind",
              "status"))
        if (!csv.getHeaderMap().containsKey(h))
          throw new IllegalArgumentException("Falta columna CSV: " + h);
      var ms = new ArrayList<Movement>();
      Set<String> ids = new HashSet<>();
      for (var row : csv) {
        if (ms.size() >= 50000)
          throw new IllegalArgumentException("Máximo 50000 filas por importación");
        String external = row.get("external_id");
        if (!ids.add(external)) throw new IllegalArgumentException("external_id duplicado en CSV");
        ms.add(
            new Movement(
                UUID.randomUUID().toString(),
                productId,
                external,
                LocalDate.parse(row.get("date")),
                new BigDecimal(row.get("amount")),
                row.get("currency"),
                row.get("description"),
                row.get("merchant"),
                row.get("category").isBlank() ? "UNCLASSIFIED" : row.get("category"),
                Movement.Kind.valueOf(row.get("kind")),
                Movement.Status.valueOf(row.get("status"))));
      }
      return ms;
    } catch (IOException e) {
      throw new IllegalArgumentException("CSV ilegible", e);
    }
  }
}
