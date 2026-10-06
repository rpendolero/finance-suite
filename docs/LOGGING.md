# Logging and Lombok

Application logs use English messages and UTC timestamps. INFO tracks imports, browser authentication, downloads, HTTP uploads, statement processing and server requests. DEBUG adds query operation names and cleanup details. WARN reports rejected requests and cleanup issues; ERROR reports technical failures.

Set `FINANCE_LOG_LEVEL=DEBUG` for additional application detail, or leave the default `INFO`. Logs go to the console and can be redirected with `> finance.log 2>&1`. Keep log files private.

Each import generates a correlation UUID, shown in square brackets. The HTTP adapter sends it as `X-Correlation-ID`; the server validates and returns it. Server-generated requests receive a new UUID. Search the same identifier across importer and server logs to follow a batch. An import's final job summary runs outside the batch context; startup and standalone query logs may show `none`.

Example (illustrative counts):

```text
INFO [correlation-uuid] ImporterService - Statement preparation completed; upload started
INFO [correlation-uuid] ImportService - Statement processed: read=30, inserted=25, duplicates=5
INFO [correlation-uuid] HttpIngestionAdapter - Batch upload response received: status=200
```

Application trace messages omit credentials, product/account/card identifiers, URLs, filenames, financial amounts, descriptions, response bodies and exception messages. Errors record only the exception type. `Statement processed` describes persistence work within the request transaction; the importer reports confirmation only after a successful response. Framework and third-party diagnostic logging remains independently configured; avoid enabling HTTP wire logging or browser debug dumps with private data.

Lombok `@RequiredArgsConstructor` supplies dependency constructors, `@Slf4j` supplies loggers, configuration uses `@Data`, and the validated import job offers `@Builder`. The importer password and job map are excluded from configuration `toString()`. Domain records remain immutable. Explicit constructors remain where validation or defensive copying is necessary.
