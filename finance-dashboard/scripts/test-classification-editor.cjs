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
            lib: {entry: {editor: path.resolve('src/ClassificationMovementRow.tsx'), importer: path.resolve('src/StatementImport.tsx'), api: path.resolve('src/api.ts')},
                formats: ['cjs'], fileName: (_format, name) => `${name}.cjs`},
            rollupOptions: {external: ['react', 'react/jsx-runtime']}
        }});
        const Row = require(path.join(temporary, 'editor.cjs')).default;
        const {api} = require(path.join(temporary, 'api.cjs'));
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
