# Enable Banking integration

Development branch: `feature/enable-banking`.

The importer now contains the foundation for Enable Banking AIS while keeping Playwright unchanged as a fallback.

## Configuration

Register an application in the Enable Banking Control Panel. For personal access to real accounts, a restricted production application can be linked to the owner's accounts.

Configure the importer with:

- `ENABLE_BANKING_ENABLED=true`
- `ENABLE_BANKING_APPLICATION_ID`
- `ENABLE_BANKING_PRIVATE_KEY` (PKCS#8 RSA private key; never commit it)
- `ENABLE_BANKING_REDIRECT_URL`

## Implemented API operations

The client supports ASPSP discovery, authorization start, authorization-code exchange, session lookup, account lookup, balances and transactions. Requests use locally generated short-lived RS256 JWTs.

## Remaining work

The next step is to map Enable Banking account and transaction payloads into Finance Suite products and canonical movements, persist session/account identifiers, implement the callback and incremental synchronization, then validate Kutxabank and ING with real linked accounts before making AIS the default source.
