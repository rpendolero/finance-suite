const assert = require('node:assert/strict');
const {createServer} = require('vite');
const {chromium} = require('playwright');

const catalog = [
    {code: 'UNCLASSIFIED', label: 'No categorizado', subcategories: []},
    {code: 'ALIMENTACION', label: 'Alimentación', subcategories: ['SUPERMERCADO'], subcategoryLabels: {SUPERMERCADO: 'Supermercado'}},
    {code: 'RESTAURACION', label: 'Restauración', subcategories: ['RESTAURANTE'], subcategoryLabels: {RESTAURANTE: 'Restaurante'}},
    {code: 'NO_COMPUTABLE', label: 'No computable', subcategories: ['LIQUIDACION_PAYPAL'], subcategoryLabels: {LIQUIDACION_PAYPAL: 'Liquidación PayPal'}}
];
const fixture = count => Array.from({length: count}, (_, i) => ({
    id: `m${String(i).padStart(3, '0')}`, productId: 'account', date: '2026-10-15', amount: -(i + 1),
    merchant: `Shop ${String(i).padStart(3, '0')}`, description: `Compra ${i}`, category: 'UNCLASSIFIED', kind: 'NORMAL'
}));
const html = `<!doctype html><html lang="es"><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body><div id="root" style="padding:16px;max-width:1200px;margin:auto"></div><script type="module">
import React from 'react'; import {createRoot} from 'react-dom/client';
import MovementSearch from '/src/MovementSearch.tsx'; import '/src/styles.css';
createRoot(document.getElementById('root')).render(React.createElement(React.StrictMode, null, React.createElement(MovementSearch, {
    products: [{id:'account', name:'Cuenta de prueba'}], initialFrom:'2026-10-01', initialTo:'2026-10-31',
    canEdit: !location.search.includes('reader'), onClassified: () => {}
})));
</script></body></html>`;

(async () => {
    let server, browser, passed = 0;
    try {
        server = await createServer({logLevel: 'error', server: {host: '127.0.0.1', port: 0}, plugins: [{
            name: 'movement-test-fixture', configureServer(vite) {
                vite.middlewares.use(async (req, res, next) => {
                    if (!req.url.startsWith('/__movement_test__')) return next();
                    res.setHeader('Content-Type', 'text/html');
                    res.end(await vite.transformIndexHtml(req.url, html));
                });
            }
        }]});
        await server.listen();
        const origin = `http://127.0.0.1:${server.httpServer.address().port}/__movement_test__`;
        browser = await chromium.launch({headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH || undefined});
        const page = await browser.newPage({viewport: {width: 1280, height: 900}});
        const browserErrors = [];
        page.on('pageerror', error => browserErrors.push(error.message));
        let rows = fixture(230), requests = [], failWrite = false;
        await page.route('**/api/**', async route => {
            const req = route.request(), url = new URL(req.url());
            const respond = (data, status = 200) => route.fulfill({status, json: data});
            if (url.pathname === '/api/categories') return respond(catalog);
            if (url.pathname === '/api/movements/search') {
                const p = url.searchParams, offset = Number(p.get('offset') || 0), limit = Number(p.get('limit') || 25);
                let matches = rows.filter(row => (!p.get('category') || row.category === p.get('category'))
                    && (!p.get('text') || (row.merchant + row.description).toLowerCase().includes(p.get('text').toLowerCase())));
                return respond({items: matches.slice(offset, offset + limit), total: matches.length, offset, limit});
            }
            if (req.method() === 'PATCH') {
                const body = req.postDataJSON(); requests.push({path: url.pathname, body});
                if (failWrite) return respond({detail: 'No se pudo guardar el lote de prueba'}, 400);
                const ids = body.movementIds || [url.pathname.split('/')[3]];
                rows = rows.map(row => ids.includes(row.id) ? {...row, category: body.category, subcategory: body.subcategory, kind: body.kind} : row);
                return respond(body.movementIds ? {updated: ids.length} : {movement: rows.find(row => row.id === ids[0]), reclassified: 0});
            }
            return respond({detail: 'Unexpected fixture request'}, 404);
        });
        const test = async (name, run) => {await run(); passed++; console.log(`OK ${name}`);};
        const countRows = async count => {await page.waitForFunction(n => document.querySelectorAll('.movement-result-row').length === n, count);};
        const ready = async () => page.waitForFunction(() => document.querySelector('.movement-results')?.getAttribute('aria-busy') === 'false');
        const dialog = () => page.getByRole('dialog');
        const selectPage = () => page.getByLabel('Seleccionar esta página', {exact: true});
        const selected = n => page.getByText(`${n} seleccionados`, {exact: true}).waitFor();
        const choose = async (category, subcategory) => {
            await dialog().getByLabel('Categoría', {exact: true}).selectOption(category);
            if (subcategory) await dialog().getByLabel('Subcategoría', {exact: true}).selectOption(subcategory);
        };
        await page.goto(origin); await countRows(25); await ready();
        await test('results are read only and page sizes persist through searches and reloads', async () => {
            assert.equal(await page.locator('.movement-result-row select').count(), 0);
            assert.equal(await page.getByLabel('Movimientos por página').inputValue(), '25');
            for (const size of [50, 100]) {
                await page.getByLabel('Movimientos por página').selectOption(String(size)); await countRows(size); await ready();
            }
            await page.reload(); await countRows(100); await ready();
            await page.getByLabel('Movimientos por página').selectOption('25'); await countRows(25); await ready();
        });
        await test('checkbox does not open modal; selection survives pages and all results includes unseen pages', async () => {
            await page.locator('.movement-result-row input').first().check(); await selected(1);
            assert.equal(await dialog().count(), 0);
            await page.getByRole('button', {name: 'Siguiente', exact: true}).click(); await ready();
            await page.getByText('26–50 de 230', {exact: false}).waitFor();
            await selectPage().check(); await selected(26);
            await page.getByRole('button', {name: 'Seleccionar los 230 resultados de la búsqueda'}).click(); await selected(230);
            await page.getByRole('button', {name: 'Recategorizar seleccionados (230)', exact: true}).click();
            await choose('RESTAURACION', 'RESTAURANTE');
            await dialog().getByRole('button', {name: 'Aplicar a 230 movimientos'}).click();
            await page.getByText('230 movimientos recategorizados.', {exact: true}).waitFor(); await ready();
            assert.equal(requests.length, 1); assert.equal(requests[0].body.movementIds.length, 230);
            assert.equal(new Set(requests[0].body.movementIds).size, 230);
            assert.equal(requests[0].body.createRule, undefined);
            assert.ok(await page.getByText('26–50 de 230', {exact: false}).count());
        });
        await test('single edits require Save; Cancel and Escape discard changes; save keeps page and scroll', async () => {
            const row = page.locator('.movement-result-row').nth(8);
            await row.locator('.movement-result-description').click();
            await choose('ALIMENTACION', 'SUPERMERCADO');
            await dialog().getByRole('button', {name: 'Cancelar', exact: true}).click();
            assert.equal(requests.length, 1);
            await row.getByRole('button', {name: 'Editar', exact: false}).click();
            assert.equal(await dialog().getByLabel('Categoría', {exact: true}).inputValue(), 'RESTAURACION');
            await page.keyboard.press('Escape'); assert.equal(await dialog().count(), 0);
            await row.getByRole('button', {name: 'Editar', exact: false}).focus();
            const before = await page.evaluate(() => window.scrollY);
            await page.keyboard.press('Enter');
            await choose('ALIMENTACION', 'SUPERMERCADO');
            await dialog().getByRole('button', {name: 'Guardar', exact: true}).click(); await ready();
            await page.getByText('Clasificación manual guardada.', {exact: true}).waitFor();
            assert.equal(requests.length, 2); assert.equal(requests[1].path, '/api/movements/m033/classification');
            assert.equal(requests[1].body.createRule, false); assert.equal(requests[1].body.applyToSimilar, false);
            assert.ok(await page.getByText('26–50 de 230', {exact: false}).count());
            assert.ok(Math.abs((await page.evaluate(() => window.scrollY)) - before) < 100);
        });
        await test('filter changes clear selection and all-result action uses the submitted search', async () => {
            await selectPage().check(); await selected(25);
            await page.getByLabel('Buscar concepto o comercio').fill('Shop 00'); await selected(0);
            assert.equal(await selectPage().isDisabled(), true);
            await page.getByRole('button', {name: 'Buscar', exact: true}).click(); await countRows(10); await ready();
            await selectPage().check(); await selected(10);
            await page.getByRole('button', {name: 'Recategorizar seleccionados (10)', exact: true}).click();
            await choose('NO_COMPUTABLE', 'LIQUIDACION_PAYPAL');
            failWrite = true;
            await dialog().getByRole('button', {name: 'Aplicar a 10 movimientos'}).click();
            await dialog().getByRole('alert').filter({hasText: 'No se pudo guardar el lote de prueba'}).waitFor();
            assert.equal(await dialog().count(), 1); await selected(10);
            assert.deepEqual(requests.at(-1).body.movementIds, Array.from({length:10}, (_, i) => `m00${i}`));
            assert.equal(requests.at(-1).body.kind, 'WALLET_SETTLEMENT');
            failWrite = false;
            await dialog().getByRole('button', {name: 'Aplicar a 10 movimientos'}).click();
            await page.getByText('10 movimientos recategorizados.', {exact:true}).waitFor(); await ready();
        });
        await test('saving the last matching row returns to the previous valid page', async () => {
            rows = fixture(26); await page.getByLabel('Buscar concepto o comercio').fill('');
            await page.getByLabel('Filtrar por categoría').selectOption('UNCLASSIFIED');
            await page.getByRole('button', {name:'Buscar', exact:true}).click(); await countRows(25); await ready();
            await page.getByRole('button', {name:'Siguiente', exact:true}).click(); await countRows(1); await ready();
            await page.locator('.movement-result-row').getByRole('button').click(); await choose('ALIMENTACION', 'SUPERMERCADO');
            await dialog().getByRole('button', {name:'Guardar', exact:true}).click(); await countRows(25); await ready();
            await page.getByText('1–25 de 25', {exact:false}).waitFor();
            assert.equal(await page.getByLabel('Filtrar por categoría').inputValue(), 'UNCLASSIFIED');
        });
        await test('mobile list and modal fit viewport, trap focus and close using Escape', async () => {
            await page.setViewportSize({width:390, height:844});
            await page.locator('.movement-result-row').first().getByRole('button').click();
            await page.keyboard.press('Tab');
            assert.equal(await page.evaluate(() => !!document.activeElement.closest('[role=dialog]')), true);
            for (let i=0; i<12; i++) await page.keyboard.press('Tab');
            assert.equal(await page.evaluate(() => !!document.activeElement.closest('[role=dialog]')), true);
            const box = await dialog().boundingBox(); assert.ok(box.width <= 390 && box.x >= 0);
            assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
            if (process.env.MOVEMENT_SCREENSHOT) await page.screenshot({path:process.env.MOVEMENT_SCREENSHOT, fullPage:false});
            await page.keyboard.press('Escape'); assert.equal(await dialog().count(), 0);
        });
        await test('reader cannot edit or select results', async () => {
            await page.goto(origin + '?reader'); await countRows(25); await ready();
            assert.equal(await page.locator('.movement-result-row input, .movement-result-row button').count(), 0);
            await page.locator('.movement-result-row').first().click(); assert.equal(await dialog().count(), 0);
        });
        assert.deepEqual(browserErrors, []);
        console.log(`${passed} movement browser checks passed`);
    } finally {await browser?.close(); await server?.close();}
})().catch(error => {console.error(error); process.exitCode = 1;});
