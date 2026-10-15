const assert = require('node:assert/strict');
const {createServer} = require('vite');
const {chromium} = require('playwright');

const catalog = [
    {code: 'UNCLASSIFIED', label: 'No categorizado', subcategories: []},
    {code: 'ALIMENTACION', label: 'Alimentación', subcategories: ['SUPERMERCADO'], subcategoryLabels: {SUPERMERCADO: 'Supermercado'}},
    {code: 'RESTAURACION', label: 'Restauración', subcategories: ['RESTAURANTE'], subcategoryLabels: {RESTAURANTE: 'Restaurante'}},
    {code: 'INGRESOS', label: 'Ingresos', subcategories: ['NOMINA'], subcategoryLabels: {NOMINA: 'Nómina'}},
    {code: 'FINANCIACION', label: 'Financiación', subcategories: ['VEHICULO'], subcategoryLabels: {VEHICULO: 'Vehículo'}},
    {code: 'NO_COMPUTABLE', label: 'No computable', subcategories: ['LIQUIDACION_PAYPAL'], subcategoryLabels: {LIQUIDACION_PAYPAL: 'Liquidación PayPal'}}
];
const fixture = count => Array.from({length: count}, (_, i) => ({
    id: `m${String(i).padStart(3, '0')}`, productId: 'account', date: '2026-10-15', amount: -(i + 1),
    merchant: `Shop ${String(i).padStart(3, '0')}`, description: `Compra ${i}`, category: 'UNCLASSIFIED', kind: 'NORMAL'
}));
const flowFixture = (count = 230) => [
    ...fixture(count).map(row => ({...row, status:'BOOKED', category:'ALIMENTACION'})),
    ...fixture(3).map((row, i) => ({...row, id:`restaurant${i}`, status:'BOOKED', category:'RESTAURACION'})),
    {...fixture(1)[0], id:'income', status:'BOOKED', amount:2000, category:'INGRESOS', merchant:'Nómina'},
    {...fixture(1)[0], id:'refund', status:'BOOKED', kind:'REFUND', amount:20, category:'ALIMENTACION', merchant:'Devolución'},
    {...fixture(1)[0], id:'vehicle', status:'BOOKED', amount:-203.95, category:'FINANCIACION', merchant:'Cuota vehículo'},
    {...fixture(1)[0], id:'outside-period', status:'BOOKED', date:'2026-11-15', category:'ALIMENTACION'},
    {...fixture(1)[0], id:'pending', status:'PENDING', category:'ALIMENTACION'},
    {...fixture(1)[0], id:'excluded', status:'BOOKED', kind:'NON_COMPUTABLE', category:'ALIMENTACION'}
];
const html = `<!doctype html><html lang="es"><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body><div id="root" style="padding:16px;max-width:1200px;margin:auto"></div><script type="module">
import React from 'react'; import {createRoot} from 'react-dom/client';
import MovementSearch from '/src/MovementSearch.tsx'; import '/src/styles.css';
import MovementSummaryModal from '/src/MovementSummaryModal.tsx';
import FinancialFlowView from '/src/FinancialFlowView.tsx';
function FlowFixture() {
    const [month, setMonth] = React.useState('10');
    const [updates, setUpdates] = React.useState(0);
    return React.createElement(React.Fragment, null,
        React.createElement('button', {onClick: () => setMonth('11')}, 'Cambiar período de prueba'),
        React.createElement('span', {'data-testid':'updates'}, 'Actualizaciones: ' + updates),
        React.createElement(FinancialFlowView, {from:'2026-' + month + '-01', to:'2026-' + month + '-30',
            products:[{id:'account', name:'Cuenta de prueba'}], canEdit:!location.search.includes('reader'),
            onClassified:() => setUpdates(value => value + 1)}));
}
const screen = location.search.includes('flow') ? React.createElement(FlowFixture) : location.search.includes('summary') ? React.createElement(MovementSummaryModal, {
    selection: {date:'2026-10-01', productId:'account'}, from:'2026-10-01', to:'2026-10-31',
    products: [{id:'account', name:'Cuenta de prueba'}], onClose: () => {}
}) : React.createElement(MovementSearch, {
    products: [{id:'account', name:'Cuenta de prueba'}], initialFrom:'2026-10-01', initialTo:'2026-10-31',
    canEdit: !location.search.includes('reader'), onClassified: () => {}
});
createRoot(document.getElementById('root')).render(React.createElement(React.StrictMode, null, screen));
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
        let rows = fixture(230), requests = [], searches = [], flowRequests = [], failWrite = false;
        let holdSelection = false, releaseSelection, selectionStarted;
        await page.route('**/api/**', async route => {
            const req = route.request(), url = new URL(req.url());
            const respond = (data, status = 200) => route.fulfill({status, json: data});
            if (url.pathname === '/api/categories') return respond(catalog);
            if (url.pathname === '/api/admin/categories') return respond(catalog.map(item => ({code:item.code, name:item.label})));
            if (url.pathname === '/api/dashboard/flows') {
                const p = url.searchParams, offset = Number(p.get('offset')), limit = Number(p.get('limit'));
                const direction = p.get('direction'), category = p.get('category');
                flowRequests.push(Object.fromEntries(p));
                assert.ok(limit >= 1 && limit <= 100, 'flow pagination must respect the server maximum');
                const group = row => direction === 'INCOME' && row.kind === 'REFUND' ? 'Devoluciones'
                    : direction === 'EXPENSE' && row.category === 'FINANCIACION' ? 'Financiación' : row.category;
                const matches = rows.filter(row => row.date >= p.get('from') && row.date <= p.get('to')
                    && row.status === 'BOOKED' && ['NORMAL', 'REFUND'].includes(row.kind) && row.category !== 'NO_COMPUTABLE'
                    && (direction === 'INCOME' ? row.amount > 0 : row.amount < 0)
                    && (!category || group(row) === category));
                const amount = row => direction === 'INCOME' ? row.amount : -row.amount;
                const total = matches.reduce((sum, row) => sum + amount(row), 0);
                const groups = new Map();
                for (const row of matches) {
                    const key = group(row), item = groups.get(key) || {category:key, amount:0, operations:0};
                    item.amount += amount(row); item.operations++; groups.set(key, item);
                }
                const categories = [...groups.values()].map(item => ({...item, average:item.amount / item.operations, share:100 * item.amount / total}));
                const data = {direction, total, operations:matches.length, categories, counterparties:[], movements:matches.slice(offset, offset + limit), offset, limit};
                if (holdSelection && category === 'ALIMENTACION' && limit === 100) {
                    holdSelection = false; selectionStarted?.();
                    await new Promise(resolve => {releaseSelection = resolve;});
                }
                return respond(data);
            }
            if (url.pathname === '/api/movements/search') {
                const p = url.searchParams, offset = Number(p.get('offset') || 0), limit = Number(p.get('limit') || 25);
                searches.push(Object.fromEntries(p));
                let matches = rows.filter(row => (!p.get('category') || row.category === p.get('category'))
                    && (!p.get('text') || (row.merchant + row.description).toLowerCase().includes(p.get('text').toLowerCase())));
                return respond({items: matches.slice(offset, offset + limit), total: matches.length, offset, limit});
            }
            if (req.method() === 'PATCH') {
                const body = req.postDataJSON(); requests.push({path: url.pathname, body});
                if (failWrite) return respond({detail: 'No se pudo guardar el lote de prueba'}, 400);
                const ids = body.movementIds || [url.pathname.split('/')[3]];
                rows = rows.map(row => ids.includes(row.id) ? {...row, category: body.category, subcategory: body.subcategory,
                    kind:body.kind === 'NORMAL' && row.kind === 'REFUND' ? 'REFUND' : body.kind} : row);
                return respond(body.movementIds ? {updated: ids.length} : {movement: rows.find(row => row.id === ids[0]), reclassified: 0});
            }
            if (req.method() === 'DELETE') {
                requests.push({path:url.pathname, method:'DELETE'});
                rows = rows.filter(row => row.id !== url.pathname.split('/')[3]);
                return route.fulfill({status:204});
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
        await test('daily summary loads every page, includes financing and excludes pending and non-computable rows', async () => {
            rows = [
                ...fixture(200).map(row => ({...row, status: 'BOOKED', kind: 'NON_COMPUTABLE'})),
                {...fixture(1)[0], id:'charges', status:'BOOKED', amount:-251.16, merchant:'Otros cargos'},
                {...fixture(1)[0], id:'vehicle', status:'BOOKED', amount:-203.95, category:'FINANCIACION', merchant:'Stellantis'},
                {...fixture(1)[0], id:'pending', status:'PENDING', amount:-999, merchant:'Pendiente'},
                {...fixture(1)[0], id:'excluded', status:'BOOKED', category:'NO_COMPUTABLE', amount:-999, merchant:'Excluido'}
            ];
            searches = [];
            await page.goto(origin + '?summary');
            await page.waitForFunction(() => document.querySelectorAll('.movement-summary-item').length === 2);
            const values = await page.locator('.movement-summary-kpis strong').allTextContents();
            assert.deepEqual(values.map(value => value.replace(/\s/g, '')), ['2', '0,00€', '455,11€', '-455,11€']);
            assert.equal(await page.getByText('Stellantis', {exact:true}).count(), 1);
            assert.equal(await page.getByText('Pendiente', {exact:true}).count(), 0);
            assert.equal(await page.getByText('Excluido', {exact:true}).count(), 0);
            assert.ok(searches.some(query => query.offset === '200'));
            assert.ok(searches.every(query => query.status === 'BOOKED' && query.productId === 'account'
                && query.from === '2026-10-01' && query.to === '2026-10-01'));
        });
        const categoryButton = label => page.locator('.flow-category-toggle').filter({hasText:label});
        const openCategory = async (label, count) => {await categoryButton(label).click(); await countRows(count); await ready();};
        await test('flow uses read-only rows and the same persisted page sizes and edit dialog as search', async () => {
            rows = flowFixture(); requests = []; flowRequests = [];
            await page.setViewportSize({width:1280, height:900});
            await page.goto(origin + '?flow'); await openCategory('Alimentación', 25);
            assert.equal(await page.locator('.movement-result-row select').count(), 0);
            for (const size of [50, 100]) {
                await page.getByLabel('Movimientos por página').selectOption(String(size)); await countRows(size); await ready();
            }
            await page.reload(); await openCategory('Alimentación', 100);
            await page.getByLabel('Movimientos por página').selectOption('25'); await countRows(25); await ready();
            await page.locator('.movement-result-description').first().click(); await choose('RESTAURACION', 'RESTAURANTE');
            await dialog().getByRole('button', {name:'Cancelar', exact:true}).click();
            assert.equal(requests.length, 0);
            await page.locator('.movement-edit').first().focus(); await page.keyboard.press('Enter');
            assert.equal(await dialog().getByLabel('Categoría', {exact:true}).inputValue(), 'ALIMENTACION');
            await page.keyboard.press('Escape'); assert.equal(await dialog().count(), 0);
        });
        await test('flow single save retains category and page and selection survives size changes', async () => {
            await page.getByRole('button', {name:'Siguiente', exact:true}).click(); await ready();
            await page.getByText('26–50 de 230', {exact:false}).waitFor();
            await page.locator('.movement-edit').first().click(); await choose('ALIMENTACION', 'SUPERMERCADO');
            await dialog().getByRole('button', {name:'Guardar', exact:true}).click(); await ready();
            await page.getByText('Clasificación manual guardada.', {exact:true}).waitFor();
            await page.getByText('26–50 de 230', {exact:false}).waitFor();
            assert.equal(await categoryButton('Alimentación').getAttribute('aria-expanded'), 'true');
            assert.equal(requests[0].path, '/api/movements/m025/classification');
            await page.getByText('Actualizaciones: 1', {exact:true}).waitFor();
            await page.locator('.movement-result-row input').first().check(); await selected(1);
            assert.equal(await dialog().count(), 0);
            await page.getByLabel('Movimientos por página').selectOption('50'); await countRows(50); await ready(); await selected(1);
            await page.getByLabel('Movimientos por página').selectOption('25'); await countRows(25); await ready(); await selected(1);
            await page.getByRole('button', {name:'Limpiar selección', exact:true}).click(); await selected(0);
        });
        await test('flow bulk selection stays within category, direction and period and handles failures and empty categories', async () => {
            await selectPage().check(); await selected(25);
            await page.getByRole('button', {name:'Siguiente', exact:true}).click(); await ready();
            await selectPage().check(); await selected(50);
            await page.getByRole('button', {name:'Seleccionar los 230 movimientos de esta categoría'}).click(); await selected(230);
            await page.getByRole('button', {name:'Recategorizar seleccionados (230)', exact:true}).click();
            await choose('RESTAURACION', 'RESTAURANTE'); failWrite = true;
            await dialog().getByRole('button', {name:'Aplicar a 230 movimientos'}).click();
            await dialog().getByRole('alert').waitFor(); await selected(230);
            assert.equal(await dialog().count(), 1);
            failWrite = false;
            await dialog().getByRole('button', {name:'Aplicar a 230 movimientos'}).click();
            await page.getByText('230 movimientos recategorizados.', {exact:true}).waitFor();
            await categoryButton('Alimentación').waitFor({state:'detached'});
            assert.equal(requests.at(-1).path, '/api/movements/classification');
            assert.deepEqual(requests.at(-1).body.movementIds, fixture(230).map(row => row.id));
            assert.equal(rows.find(row => row.id === 'refund').category, 'ALIMENTACION');
            assert.equal(rows.find(row => row.id === 'outside-period').category, 'ALIMENTACION');
            assert.equal(rows.find(row => row.id === 'pending').category, 'ALIMENTACION');
            await openCategory('Restauración', 25);
            await page.getByText('1–25 de 233', {exact:false}).waitFor(); await selected(0);
            assert.ok(flowRequests.some(query => query.category === 'ALIMENTACION' && query.offset === '200' && query.limit === '100'));
        });
        await test('flow clears selection on category, direction and period changes and ignores late selection responses', async () => {
            rows = flowFixture(); await page.goto(origin + '?flow'); await openCategory('Alimentación', 25);
            await selectPage().check(); await selected(25);
            await openCategory('Restauración', 3); await selected(0);
            await openCategory('Alimentación', 25); await selected(0); await selectPage().check(); await selected(25);
            holdSelection = true;
            const started = new Promise(resolve => {selectionStarted = resolve;});
            await page.getByRole('button', {name:'Seleccionar los 230 movimientos de esta categoría'}).click(); await started;
            await page.getByRole('button', {name:'Ingresos', exact:true}).click();
            await openCategory('Devoluciones', 1); await selected(0);
            releaseSelection();
            await page.locator('.movement-result-row input').check(); await selected(1);
            await page.getByRole('button', {name:'Gastos', exact:true}).click();
            await openCategory('Alimentación', 25); await selected(0);
            await selectPage().check(); await selected(25);
            await page.getByRole('button', {name:'Cambiar período de prueba'}).click();
            await categoryButton('Alimentación').filter({hasText:'1 operaciones'}).waitFor();
            await openCategory('Alimentación', 1); await selected(0);
            assert.equal(flowRequests.at(-1).from, '2026-11-01');
        });
        await test('flow recategorization corrects an empty last page and deletion refreshes category totals', async () => {
            rows = flowFixture(26); requests = [];
            await page.goto(origin + '?flow'); await openCategory('Alimentación', 25);
            await page.getByRole('button', {name:'Siguiente', exact:true}).click(); await countRows(1); await ready();
            await page.locator('.movement-edit').click(); await choose('RESTAURACION', 'RESTAURANTE');
            await dialog().getByRole('button', {name:'Guardar', exact:true}).click(); await countRows(25); await ready();
            await page.getByText('1–25 de 25', {exact:false}).waitFor();
            await page.locator('.movement-edit').first().click();
            await dialog().getByRole('button', {name:'Eliminar', exact:true}).click();
            await dialog().getByRole('button', {name:'Cancelar', exact:true}).click();
            assert.equal(requests.length, 1);
            await page.locator('.movement-edit').first().click();
            await dialog().getByRole('button', {name:'Eliminar', exact:true}).click();
            await dialog().getByRole('button', {name:'Eliminar definitivamente'}).click();
            await countRows(24); await ready();
            await categoryButton('Alimentación').filter({hasText:'24 operaciones'}).waitFor();
            await page.getByText('Movimiento eliminado.', {exact:true}).waitFor();
            assert.equal(requests.at(-1).method, 'DELETE');
        });
        await test('income editing uses the same modal and removing a computable income refreshes the total', async () => {
            await page.getByRole('button', {name:'Ingresos', exact:true}).click();
            await openCategory('Devoluciones', 1);
            await page.locator('.movement-edit').click();
            assert.equal(await dialog().getByLabel('Tratamiento', {exact:true}).inputValue(), 'REFUND');
            await choose('NO_COMPUTABLE', 'LIQUIDACION_PAYPAL');
            await dialog().getByRole('button', {name:'Guardar', exact:true}).click();
            await categoryButton('Devoluciones').waitFor({state:'detached'});
            assert.equal((await page.locator('.flow-total strong').textContent()).replace(/\s/g, ''), '2000,00€');
            await page.getByText('Clasificación manual guardada.', {exact:true}).waitFor();
        });
        await test('flow mobile layout and reader permissions match movement search', async () => {
            await page.setViewportSize({width:390, height:844});
            await page.goto(origin + '?flow'); await openCategory('Alimentación', 24);
            await page.locator('.movement-edit').first().click();
            const box = await dialog().boundingBox(); assert.ok(box.width <= 390 && box.x >= 0);
            assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
            await page.keyboard.press('Escape');
            await page.goto(origin + '?flow&reader'); await openCategory('Alimentación', 24);
            assert.equal(await page.locator('.movement-result-row input, .movement-result-row button').count(), 0);
            await page.locator('.movement-result-row').first().click(); assert.equal(await dialog().count(), 0);
            await page.getByRole('button', {name:'Ingresos', exact:true}).click();
            await openCategory('Ingresos', 1);
            assert.equal(await page.locator('.movement-result-row input, .movement-result-row button').count(), 0);
        });
        assert.deepEqual(browserErrors, []);
        console.log(`${passed} movement browser checks passed`);
    } finally {await browser?.close(); await server?.close();}
})().catch(error => {console.error(error); process.exitCode = 1;});
