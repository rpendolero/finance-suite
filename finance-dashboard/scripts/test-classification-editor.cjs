const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {build} = require('vite');
const React = require('react');
const {renderToStaticMarkup} = require('react-dom/server');

const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'finance-editor-test-'));
let passed = 0;
const test = async (name, run) => { await run(); passed++; console.log(`OK ${name}`); };
(async () => {
    try {
        fs.symlinkSync(path.resolve('node_modules'), path.join(temporary, 'node_modules'), 'dir');
        await build({logLevel: 'silent', build: {
            outDir: temporary, emptyOutDir: false,
            lib: {entry: {editor: path.resolve('src/ClassificationMovementRow.tsx'), importer: path.resolve('src/StatementImport.tsx'), api: path.resolve('src/api.ts'), periods: path.resolve('src/periods.ts'), periodSelector: path.resolve('src/PeriodSelector.tsx'), calendar: path.resolve('src/FinancialCalendar.tsx')},
                formats: ['cjs'], fileName: (_format, name) => `${name}.cjs`},
            rollupOptions: {external: ['react', 'react/jsx-runtime']}
        }});
        const Row = require(path.join(temporary, 'editor.cjs')).default;
        const {api} = require(path.join(temporary, 'api.cjs'));
        const {periodRange} = require(path.join(temporary, 'periods.cjs'));
        const {default: FinancialCalendar, calendarCells} = require(path.join(temporary, 'calendar.cjs'));
        const calendarDay = date => ({date, income: 0, expenses: 0, net: 0, operations: 0});
        const positions = dates => calendarCells(dates.map(calendarDay)).map(({day, column, row}) => [day.date, column, row]);
        await test('calendar starts on the actual weekday and wraps Sunday to Monday', () => {
            assert.deepEqual(positions(['2026-10-01', '2026-10-04', '2026-10-05']), [
                ['2026-10-01', 4, 2], ['2026-10-04', 7, 2], ['2026-10-05', 1, 3]
            ]);
        });
        await test('calendar sorts dates and preserves gaps rather than shifting weekdays', () => {
            assert.deepEqual(positions(['2026-10-08', '2026-10-01', '2026-10-03']), [
                ['2026-10-01', 4, 2], ['2026-10-03', 6, 2], ['2026-10-08', 4, 3]
            ]);
        });
        await test('calendar retains weekday alignment across leap days and year boundaries', () => {
            assert.deepEqual(positions(['2024-02-29', '2024-03-01']), [['2024-02-29', 4, 2], ['2024-03-01', 5, 2]]);
            assert.deepEqual(positions(['2023-12-31', '2024-01-01']), [['2023-12-31', 7, 2], ['2024-01-01', 1, 3]]);
        });
        await test('calendar stays aligned through the Spanish daylight saving changes', () => {
            for (const [sunday, monday] of [['2026-03-29', '2026-03-30'], ['2026-10-25', '2026-10-26']])
                assert.deepEqual(positions([sunday, monday]), [[sunday, 7, 2], [monday, 1, 3]]);
        });
        await test('calendar renders seven Spanish headers, month labels and daily financial data', () => {
            const html = renderToStaticMarkup(React.createElement(FinancialCalendar, {
                days: [{...calendarDay('2026-10-01'), income: 100, expenses: 25, net: 75, operations: 2}]
            }));
            for (const weekday of ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']) assert.ok(html.includes(weekday));
            assert.match(html, /grid-column:4;grid-row:2/);
            assert.match(html, /<time dateTime="2026-10-01">01 oct<\/time>/);
            assert.ok(html.includes('2 op.'));
            assert.ok(html.includes('-25,00'));
            assert.ok(html.includes('+100,00'));
        });
        await test('empty calendar shows a Spanish empty-state message', () => {
            assert.deepEqual(calendarCells([]), []);
            const html = renderToStaticMarkup(React.createElement(FinancialCalendar, {days: []}));
            assert.ok(html.includes('Sin datos en el período seleccionado.'));
        });
        const selectorModule = require(path.join(temporary, 'periodSelector.cjs'));
        const PeriodSelector = selectorModule.default || selectorModule;
        const renderPeriod = (overrides = {}) => renderToStaticMarkup(React.createElement(PeriodSelector, {
            preset: 'THIS_MONTH', year: 2026, currentYear: 2026, from: '2026-10-01', to: '2026-10-10',
            onPresetChange: () => {}, onYearChange: () => {}, onFromChange: () => {}, onToChange: () => {}, ...overrides
        }));
        await test('top selector retains all relative periods and adds the twelve named months', () => {
            const html = renderPeriod();
            for (const label of ['Este mes', 'Mes anterior', 'Últimos 3 meses', 'Últimos 6 meses', 'Este año', 'Personalizado',
                'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'])
                assert.ok(html.includes(label), label);
            assert.equal((html.match(/value="MONTH_/g) || []).length, 12);
            assert.doesNotMatch(html, /aria-label="Año"/);
        });
        await test('named month exposes the selected year; custom period keeps both date inputs', () => {
            const html = renderPeriod({preset: 'MONTH_02', year: 2024, from: '2024-02-01', to: '2024-02-29'});
            assert.match(html, /aria-label="Año"/);
            assert.match(html, /value="2024" selected=""/);
            assert.match(html, /2024-02-01 — 2024-02-29/);
            const custom = renderPeriod({preset: 'CUSTOM'});
            assert.match(custom, /aria-label="Desde"/);
            assert.match(custom, /aria-label="Hasta"/);
            assert.doesNotMatch(custom, /aria-label="Año"/);
        });
        await test('calendar months cover their complete dates including leap February', () => {
            const now = new Date(2026, 9, 10);
            const lastDays = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
            lastDays.forEach((day, index) => {
                const month = String(index + 1).padStart(2, '0');
                assert.deepEqual(periodRange(`MONTH_${month}`, now), {from: `2026-${month}-01`, to: `2026-${month}-${day}`});
            });
            assert.deepEqual(periodRange('MONTH_02', now, 2024), {from: '2024-02-01', to: '2024-02-29'});
            assert.deepEqual(periodRange('MONTH_12', now, 2025), {from: '2025-12-01', to: '2025-12-31'});
        });
        await test('relative periods retain their dates independently of the chosen month year', () => {
            const now = new Date(2026, 0, 10);
            const expected = {
                THIS_MONTH: {from: '2026-01-01', to: '2026-01-10'},
                LAST_MONTH: {from: '2025-12-01', to: '2025-12-31'},
                '3M': {from: '2025-11-01', to: '2026-01-10'},
                '6M': {from: '2025-08-01', to: '2026-01-10'},
                YEAR: {from: '2026-01-01', to: '2026-01-10'}
            };
            for (const [preset, range] of Object.entries(expected)) assert.deepEqual(periodRange(preset, now, 2024), range);
        });
        const movement = {id: 'one', productId: 'account', date: '2026-10-09', amount: -20,
            description: 'Compra', merchant: 'Mercadona 1234', normalizedMerchant: 'MERCADONA',
            category: 'ALIMENTACION', subcategory: 'SUPERMERCADO', kind: 'NORMAL'};
        const catalog = [
            {code: 'ALIMENTACION', label: 'Alimentación', subcategories: ['SUPERMERCADO']},
            {code: 'UNCLASSIFIED', label: 'No categorizado', subcategories: []},
            {code: 'NO_COMPUTABLE', label: 'No computable', subcategories: ['OTROS_NO_COMPUTABLES']}
        ];
        const render = (overrides = {}) => renderToStaticMarkup(React.createElement(Row, {
            movement, catalog, canEdit: true, onSaved: () => {}, ...overrides
        }));
        await test('admin has both classification actions and normalized merchant', () => {
            const html = render({onDelete: () => {}});
            for (const text of ['MERCADONA', 'Tratamiento', 'Categoría', 'Subcategoría', 'Solo este', 'Aplicar al comercio', 'Eliminar'])
                assert.ok(html.includes(text), text);
            assert.match(html, /value="ALIMENTACION" selected=""/);
            assert.match(html, /value="SUPERMERCADO" selected=""/);
        });
        await test('reader has no write actions and fields are disabled', () => {
            const html = render({canEdit: false, onDelete: () => {}});
            assert.doesNotMatch(html, /<button/);
            assert.equal((html.match(/<select[^>]*disabled=""/g) || []).length, 3);
        });
        await test('negative charge cannot be selected as a refund', () => {
            assert.match(render(), /<option value="REFUND" disabled=""/);
        });
        await test('existing refund keeps its selected treatment', () => {
            assert.match(render({movement: {...movement, amount: 20, kind: 'REFUND'}}), /value="REFUND" selected=""/);
        });
        await test('unclassified movement cannot create a merchant rule', () => {
            const html = render({movement: {...movement, category: 'UNCLASSIFIED', subcategory: undefined}});
            assert.match(html, /<button[^>]*disabled=""[^>]*>Aplicar al comercio/);
        });
        await test('single and merchant writes include correct scope and CSRF', async () => {
            global.document = {cookie: 'XSRF-TOKEN=test%20token'};
            const calls = [];
            global.fetch = async (url, init) => {
                calls.push({url, init});
                return {ok: true, status: 200, json: async () => ({movement, reclassified: 1})};
            };
            for (const apply of [false, true]) {
                const result = await api.classifyMovement(movement.id, {
                    category: 'ALIMENTACION', subcategory: 'SUPERMERCADO', kind: 'NORMAL',
                    createRule: apply, applyToSimilar: apply
                });
                assert.equal(result.movement.id, movement.id);
                const {url, init} = calls.at(-1);
                assert.equal(url, '/api/movements/one/classification');
                assert.equal(init.method, 'PATCH');
                assert.equal(init.credentials, 'include');
                assert.equal(init.headers['X-XSRF-TOKEN'], 'test token');
                assert.equal(JSON.parse(init.body).createRule, apply);
                assert.equal(JSON.parse(init.body).applyToSimilar, apply);
            }
        });
        await test('validation failures show the API detail', async () => {
            global.fetch = async () => ({ok: false, status: 400, statusText: 'Bad Request', json: async () => ({detail: 'Selecciona subcategoría'})});
            await assert.rejects(api.classifyMovement('one', {category: 'NO_COMPUTABLE', kind: 'NON_COMPUTABLE', createRule: false, applyToSimilar: false}), /Selecciona subcategoría/);
        });
        await test('statement upload uses multipart, session and CSRF without a fixed content type', async () => {
            global.document = {cookie: 'XSRF-TOKEN=upload%20token'};
            let captured;
            global.fetch = async (url, init) => {
                captured = {url, init};
                return {ok: true, status: 200, json: async () => ({read: 3, inserted: 2, duplicates: 1})};
            };
            const file = new File(['bank workbook'], 'statement.xls');
            assert.deepEqual(await api.importStatement('product-one', 'KUTXABANK_ACCOUNT_XLS', file), {read: 3, inserted: 2, duplicates: 1});
            assert.equal(captured.url, '/api/products/product-one/imports');
            assert.equal(captured.init.method, 'POST');
            assert.equal(captured.init.credentials, 'include');
            assert.equal(captured.init.headers['X-XSRF-TOKEN'], 'upload token');
            assert.equal(captured.init.headers['Content-Type'], undefined);
            assert.ok(captured.init.body instanceof FormData);
            assert.equal(captured.init.body.get('format'), 'KUTXABANK_ACCOUNT_XLS');
            assert.equal(captured.init.body.get('file').name, 'statement.xls');
        });
        await test('statement screen offers product, format, file and new-product controls', () => {
            const importerModule = require(path.join(temporary, 'importer.cjs'));
            const Importer = importerModule.default || importerModule;
            const html = renderToStaticMarkup(React.createElement(Importer, {
                products: [{id: 'account', name: 'Mi cuenta', provider: 'ING', type: 'ACCOUNT'}],
                onImported: () => {}, onProductCreated: () => {}
            }));
            for (const label of ['Mi cuenta', 'Formato del fichero', 'Extracto bancario', 'Nuevo producto', 'type="file"'])
                assert.ok(html.includes(label), label);
            assert.match(html, /<button[^>]*type="submit"[^>]*disabled=""/);
        });
        await test('statement upload exposes backend format validation details', async () => {
            global.fetch = async () => ({ok: false, status: 400, json: async () => ({detail: 'El formato no corresponde al producto'})});
            await assert.rejects(api.importStatement('account', 'ING_ACCOUNT_XLS', new File(['file'], 'statement.xls')), /El formato no corresponde al producto/);
        });
        console.log(`${passed} checks passed`);
    } finally { fs.rmSync(temporary, {recursive: true, force: true}); }
})().catch(error => { console.error(error); process.exitCode = 1; });
