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
            lib: {entry: {editor: path.resolve('src/ClassificationMovementRow.tsx'), api: path.resolve('src/api.ts')},
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
        console.log(`${passed} checks passed`);
    } finally { fs.rmSync(temporary, {recursive: true, force: true}); }
})().catch(error => { console.error(error); process.exitCode = 1; });
