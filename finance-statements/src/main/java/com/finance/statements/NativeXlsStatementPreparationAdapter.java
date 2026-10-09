package com.finance.statements;

import com.finance.domain.Product;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Owns workbook lifecycle and delegates format differences to strategies.
 */
@Slf4j
public final class NativeXlsStatementPreparationAdapter {
    private final List<NativeStatementFormat> formats;
    private final CanonicalCsvValidator canonical;

    public NativeXlsStatementPreparationAdapter() {
        this(
                List.of(
                        new IngAccountStatementFormat(),
                        new IngCreditCardStatementFormat(),
                        new KutxabankAccountStatementFormat(),
                        new KutxabankCardStatementFormat()),
                new CanonicalCsvValidator());
    }

    public NativeXlsStatementPreparationAdapter(
            List<NativeStatementFormat> formats, CanonicalCsvValidator canonical) {
        this.formats = List.copyOf(formats);
        this.canonical = canonical;
    }

    public void validate(Path input) {
        canonical.validate(input);
    }

    public Path prepare(Path input, Product.Provider provider) {
        if (!input.toString().toLowerCase(Locale.ROOT).endsWith(".xls")) {
            validate(input);
            return input;
        }
        return prepareNative(input, provider, null);
    }

    public Path prepare(Path input, StatementFormat format) {
        if (format == StatementFormat.CSV) {
            validate(input);
            return input;
        }
        return prepareNative(input, format.provider(), format);
    }

    private Path prepareNative(Path input, Product.Provider provider, StatementFormat expected) {
        validateNativeInput(input, provider);
        Path output = null;
        try (var stream = Files.newInputStream(input);
             var workbook = new HSSFWorkbook(stream)) {
            log.info("Native workbook conversion started: provider={}", provider);
            var format = selectFormat(workbook, provider, expected);
            var sheet = workbook.getSheet(format.sheetName());
            var formatter = new DataFormatter(Locale.forLanguageTag("es-ES"));
            format.validateTitle(sheet, formatter);
            int header = findAndValidateHeader(sheet, format, formatter);
            output = createPrivateOutput(input, format);
            writeMovements(sheet, header, format, formatter, output);
            validate(output);
            log.info("Native workbook conversion completed: provider={}", provider);
            return output;
        } catch (Exception failure) {
            log.error(
                    "Native workbook conversion failed: errorType={}", failure.getClass().getSimpleName());
            removePartialOutput(output, failure);
            throw new IllegalArgumentException("No se pudo normalizar el Excel nativo: " + failure.getMessage(), failure);
        }
    }

    private void validateNativeInput(Path input, Product.Provider provider) {
        if (formats.stream().noneMatch(f -> f.provider() == provider))
            throw new IllegalArgumentException("Proveedor sin formato XLS nativo configurado");
        try {
            if (!Files.isRegularFile(input))
                throw new IllegalArgumentException("Excel ausente");
            else if (Files.size(input) > 10 * 1024 * 1024)
                throw new IllegalArgumentException("Excel mayor de 10 MB");
        } catch (IOException e) {
            throw new IllegalArgumentException("Excel nativo ilegible", e);
        }
    }

    private NativeStatementFormat selectFormat(HSSFWorkbook workbook, Product.Provider provider, StatementFormat expected) {
        var matches =
                formats.stream()
                        .filter(
                                f ->
                                        (expected == null || expected.identifierPrefix().equals(f.identifierPrefix()))
                                        && f.provider() == provider && matchesHeaders(workbook.getSheet(f.sheetName()), f))
                        .toList();
        if (matches.size() != 1)
            throw new IllegalArgumentException("Formato nativo desconocido o ambiguo");
        return matches.getFirst();
    }

    private boolean matchesHeaders(Sheet sheet, NativeStatementFormat format) {
        if (sheet == null) return false;
        var formatter = new DataFormatter(Locale.forLanguageTag("es-ES"));
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) {
            var row = sheet.getRow(i);
            if (row == null) continue;
            boolean matches = true;
            for (int c = 0; c < format.headers().size(); c++) {
                if (!format.headers().get(c).equals(formatter.formatCellValue(row.getCell(c)).trim())) {
                    matches = false;
                    break;
                }
            }
            if (matches) return true;
        }
        return false;
    }

    private int findAndValidateHeader(
            Sheet sheet, NativeStatementFormat format, DataFormatter formatter) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) {
            var row = sheet.getRow(i);
            if (row == null
                    || !format.headers().getFirst().equals(formatter.formatCellValue(row.getCell(0)).trim()))
                continue;
            for (int c = 0; c < format.headers().size(); c++)
                if (!format.headers().get(c).equals(formatter.formatCellValue(row.getCell(c)).trim()))
                    throw new IllegalArgumentException(
                            "Estructura nativa incompatible en columna " + (c + 1));
            return i;
        }
        throw new IllegalArgumentException("Cabecera nativa no encontrada");
    }

    private Path createPrivateOutput(Path input, NativeStatementFormat format) throws IOException {
        Path output =
                Files.createTempFile(input.toAbsolutePath().getParent(), "statement-normalized-", ".csv");
        try {
            Files.setPosixFilePermissions(
                    output, PosixFilePermissions.fromString("rw-------"));
            return output;
        } catch (IOException | RuntimeException failure) {
            removePartialOutput(output, failure);
            throw failure;
        }
    }

    private void writeMovements(
            Sheet sheet, int header, NativeStatementFormat format, DataFormatter formatter, Path output)
            throws Exception {
        var occurrences = new HashMap<String, Integer>();
        int count = 0;
        try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
             var csv =
                     new CSVPrinter(
                             writer,
                             CSVFormat.DEFAULT
                                     .builder()
                                     .setDelimiter(';')
                                     .setHeader(
                                             "external_id",
                                             "date",
                                             "amount",
                                             "currency",
                                             "description",
                                             "merchant",
                                             "category",
                                             "kind",
                                             "status")
                                     .get())) {
            for (int i = header + 1; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                if (isBlank(row, format.headers().size(), formatter)) continue;
                if (++count > 50000) throw new IllegalArgumentException("Máximo 50000 movimientos");
                try {
                    var movement = readMovement(row, format, formatter);
                    csv.printRecord(
                            identifier(movement, format, occurrences),
                            movement.date(),
                            movement.amount(),
                            "EUR",
                            movement.description(),
                            "",
                            movement.category(),
                            "NORMAL",
                            movement.status());
                } catch (Exception failure) {
                    throw new IllegalArgumentException(
                            "Movimiento nativo inválido en fila " + (i + 1), failure);
                }
            }
        }
        log.info("Native statement rows converted: count={}", count);
        if (count == 0) throw new IllegalArgumentException("Exportación nativa sin movimientos");
    }

    private boolean isBlank(Row row, int columns, DataFormatter formatter) {
        if (row == null) return true;
        for (int c = 0; c < columns; c++)
            if (!formatter.formatCellValue(row.getCell(c)).isBlank()) return false;
        return true;
    }

    private NativeMovement readMovement(
            Row row, NativeStatementFormat format, DataFormatter formatter) {
        var date = readDate(row.getCell(format.dateColumn()), format);
        var amount = readMoney(row.getCell(format.amountColumn()));
        var balance =
                format.balanceColumn() == null ? null : readMoney(row.getCell(format.balanceColumn()));
        String description =
                format.normalizeDescription(
                        formatter.formatCellValue(row.getCell(format.descriptionColumn())));
        String category =
                format.categoryColumn() == null
                        ? ""
                        : formatter.formatCellValue(row.getCell(format.categoryColumn())).trim();
        if (description.isBlank() || description.length() > 1000 || category.length() > 64)
            throw new IllegalArgumentException("Descripción o categoría inválida");
        return new NativeMovement(
                date,
                amount,
                balance,
                description,
                category.isBlank() ? "UNCLASSIFIED" : category,
                format.movementStatus(row, formatter));
    }

    private LocalDate readDate(Cell cell, NativeStatementFormat format) {
        if (format.textDates() && cell != null && cell.getCellType() == CellType.STRING)
            return LocalDate.parse(
                    cell.getStringCellValue().trim(),
                    DateTimeFormatter.ofPattern("dd/MM/uuuu")
                            .withResolverStyle(ResolverStyle.STRICT));
        if (cell == null
                || cell.getCellType() != CellType.NUMERIC
                || !DateUtil.isCellDateFormatted(cell))
            throw new IllegalArgumentException("Fecha Excel requerida");
        return cell.getLocalDateTimeCellValue().toLocalDate();
    }

    private BigDecimal readMoney(Cell cell) {
        if (cell == null || cell.getCellType() != CellType.NUMERIC)
            throw new IllegalArgumentException("Importe numérico requerido");
        return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.UNNECESSARY);
    }

    private String identifier(
            NativeMovement movement, NativeStatementFormat format, Map<String, Integer> occurrences)
            throws Exception {
        String identity =
                movement.date()
                        + "\u001f"
                        + movement.amount()
                        + "\u001f"
                        + (movement.balance() == null ? "" : movement.balance() + "\u001f")
                        + movement.description();
        String hash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(identity.getBytes(StandardCharsets.UTF_8)));
        return format.identifierPrefix() + hash + "-" + occurrences.merge(hash, 1, Integer::sum);
    }

    private void removePartialOutput(Path output, Exception failure) {
        if (output != null)
            try {
                Files.deleteIfExists(output);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
    }

    private record NativeMovement(
            LocalDate date,
            BigDecimal amount,
            BigDecimal balance,
            String description,
            String category,
            String status) {
    }
}
