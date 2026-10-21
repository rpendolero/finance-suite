# Financial identity and product UUID migration

## Deployment safety

V18 is an **expand-only** Flyway migration: it adds the identity and mapping tables and the IBAN column. It does **not** rewrite product primary keys, delete duplicate products, or rewrite movements. Back up MySQL before deploying.

The application uses Flyway SQL migrations under `db/migration` (not Liquibase). The existing `product.id` and references are VARCHAR(64), including `movement.product_id`, `product.linked_account_id` and `external_bank_account.product_id`.

## UUID migration procedure (pending validation)

1. Take a consistent MySQL backup and restore it to staging.
2. Inventory all foreign keys and any application tables referring to `product.id`.
3. Populate `product_id_migration(old_id,new_id)` with one immutable UUID per existing product. Validate counts, uniqueness and referential completeness.
4. Stage new UUID keys and all dependent references in shadow columns/tables. Preserve unique movement keys and self-references.
5. Validate every movement, card/account link and external banking link, then perform a controlled cutover and re-create foreign keys.
6. Switch creation and validation paths to UUIDs only **after** all consumers and importers are updated. Keep the mapping for rollback/audit.

Do not run an UPDATE of the primary key while dependent foreign keys are active, and do not disable foreign-key checks as a shortcut.

## Account reconciliation

The Enable Banking account-details payload includes `account_id.iban`. The adapter now preserves the normalized IBAN. Match on a verified IBAN before considering provider account IDs; never merge on account name alone. Existing duplicate products must be reported and manually reviewed before consolidating historical movements.

## Card identifiers

Never store a full PAN. A masked PAN or last four digits is not unique. A provider token is preferred; HMAC-based identity requires explicit key management and a PCI review. Kutxabank card imports without a stable identifier must remain linked to the selected product.
