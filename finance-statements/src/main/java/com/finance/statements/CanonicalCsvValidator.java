package com.finance.statements;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.csv.CSVFormat;

public class CanonicalCsvValidator {
  public void validate(Path path) {
    try {
      if (!Files.isRegularFile(path) || Files.size(path) > 10 * 1024 * 1024)
        throw new IllegalArgumentException("CSV ausente o mayor de 10 MB");
      try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
          var csv =
              CSVFormat.DEFAULT
                  .builder()
                  .setDelimiter(';')
                  .setHeader()
                  .setSkipHeaderRecord(true)
                  .get()
                  .parse(reader)) {
        for (var key :
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
          if (!csv.getHeaderMap().containsKey(key))
            throw new IllegalArgumentException(
                "Exportación nativa pendiente de adaptar al CSV normalizado; falta columna " + key);
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("No se pudo leer el CSV local", e);
    }
  }
}
