const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {createServer} = require('vite');
const {chromium} = require('playwright');
const evidence = path.resolve('../docs/evidence/financial-overview');
const before = process.argv.includes('--before');
const rows = [
 {id:'salary', productId:'account',date:'2026-11-02',amount:2000,currency:'EUR',description:'Nómina de ejemplo',merchant:'Nómina',category:'INGRESOS',kind:'NORMAL',status:'BOOKED'},
 {id:'purchase',productId:'card',date:'2026-11-04',amount:-100,currency:'EUR',description:'Compra de ejemplo',merchant:'Mercado local',category:'ALIMENTACION',kind:'NORMAL',status:'BOOKED'},
 {id:'transfer',productId:'account',date:'2026-11-05',amount:-300,currency:'EUR',description:'Traspaso propio',category:'NO_COMPUTABLE',kind:'INTERNAL_TRANSFER',status:'BOOKED'},
 {id:'transfer-in',productId:'savings',date:'2026-11-05',amount:300,currency:'EUR',description:'Traspaso propio',category:'NO_COMPUTABLE',kind:'INTERNAL_TRANSFER',status:'BOOKED'},
 {id:'settlement',productId:'account',date:'2026-11-06',amount:-100,currency:'EUR',description:'Liquidación de tarjeta',category:'NO_COMPUTABLE',kind:'CARD_SETTLEMENT',status:'BOOKED'}
];
const products = [
 {id:'account',name:'Cuenta diaria',type:'ACCOUNT',provider:'KUTXABANK',currency:'EUR',balance:1600,balanceAt:'2026-11-09T09:00:00Z'},
 {id:'savings',name:'Cuenta reserva',type:'ACCOUNT',provider:'ING',currency:'EUR',balance:300,balanceAt:'2026-11-08T09:00:00Z'},
 {id:'card',name:'Tarjeta diaria',type:'CREDIT_CARD',provider:'KUTXABANK',currency:'EUR',balance:0,balanceAt:'2026-11-09T09:00:00Z',creditLimit:2000}
];
(async()=>{
 let server,browser; let state='success', passed=0; const requests=[];
 const check=async(name,fn)=>{await fn();console.log('OK '+name);passed++;};
 try {
 server=await createServer({root:process.env.FINANCE_OVERVIEW_ROOT || process.cwd(),logLevel:'error',server:{host:'127.0.0.1',port:0}}); await server.listen();
 browser=await chromium.launch({headless:true,executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH || undefined});
 const page=await browser.newPage({reducedMotion:'reduce'});
 await page.clock.install({time:new Date('2026-11-09T12:00:00Z')});
 const errors=[]; page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/api/**',async route=>{
  const url=new URL(route.request().url()); requests.push(url);
  const send=(data,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)});
  if(url.pathname==='/api/auth/me')return send({username:'demo',roles:['ADMIN']});
  if(state==='loading')await new Promise(r=>setTimeout(r,700));
  if(state==='error')return send({detail:'No se pueden agregar movimientos en monedas distintas de EUR sin conversión.'},400);
  const empty=state==='empty';
  const cats=empty?[]:[{category:'ALIMENTACION',amount:100,operations:1,average:100,share:100}];
  if(url.pathname==='/api/dashboard/overview')return send({totalBalance:empty?0:1900,income:empty?0:2000,expenses:empty?0:100,availableAfterAllocations:empty?0:1900,averageDailyExpense:3.33,savings:0,savingsRate:0,financingPayments:0,financingCapital:0,financingInterest:0,financingUnallocated:0,categories:cats,merchants:[]});
  if(url.pathname==='/api/products')return send(empty?[]:products);
  if(url.pathname==='/api/movements')return send(empty?[]:rows);
  if(url.pathname==='/api/movements/search')return send({items:empty?[]:rows,total:empty?0:rows.length,offset:0,limit:25});
  if(url.pathname==='/api/categories')return send([{code:'ALIMENTACION',label:'Alimentación',subcategories:[]},{code:'INGRESOS',label:'Ingresos',subcategories:[]}]);
  if(url.pathname==='/api/admin/categories')return send([{code:'ALIMENTACION',name:'Alimentación'},{code:'INGRESOS',name:'Ingresos'}]);
  if(url.pathname==='/api/dashboard/categories')return send(cats);
  if(url.pathname==='/api/dashboard/trend')return send(empty?[]:[{date:'2026-11-02',income:2000,expenses:0,savings:0},{date:'2026-11-04',income:0,expenses:100,savings:0}]);
  if(url.pathname==='/api/analysis/forecast')return send(empty?null:{asOf:'2026-11-09',days:30,accountBalances:1900,estimatedNetFlow:6333.34,estimatedAccountBalance:8233.34,estimatedIncome:6666.67,estimatedExpenses:333.33,recurringIncome:0,recurringExpenses:0,recurringPatterns:0,limitations:'Ejemplo sintético. Proyección lineal de 9 días observados; no garantiza liquidez.'});
  if(url.pathname==='/api/dashboard/flows'){
    const income=url.searchParams.get('direction')==='INCOME';
    const matches=rows.filter(r=>r.kind==='NORMAL'&&(income?r.amount>0:r.amount<0));
    return send({direction:income?'INCOME':'EXPENSE',total:income?2000:100,operations:1,categories:income?[{category:'INGRESOS',amount:2000,operations:1,share:100,average:2000}]:cats,counterparties:[],movements:matches,offset:0,limit:25});
  }
  return send([]);
 });
 const open=async()=>{await page.goto(server.resolvedUrls.local[0]);await page.getByRole('heading',{name:'Inicio',exact:true}).waitFor();};
 fs.mkdirSync(evidence,{recursive:true});
 for(const width of [1440,390]){
  state='success'; await page.setViewportSize({width,height:1000});await open();
  await page.getByText('Saldo total',{exact:true}).or(page.getByText('Saldo registrado',{exact:true})).first().waitFor();
  await page.screenshot({path:path.join(evidence,`${before?'before':'after'}-${width}.png`),fullPage:true});
  if(before)continue;
  await check(`layout ${width}px fits viewport`,async()=>assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)));
  await check(`period result ${width}px`,async()=>{await page.getByRole('heading',{name:'Resultado del período'}).waitFor();assert.match(await page.locator('.overview-result').innerText(),/1\.?900,00/);});
  await check(`income detail ${width}px`,async()=>{await page.getByRole('button',{name:'Ver ingresos del período'}).click();await page.getByRole('button',{name:'Ingresos',exact:true}).waitFor();await page.locator('.flow-total').waitFor();assert.match(await page.locator('.flow-total').innerText(),/2000,00|2\.000,00/);assert.ok(requests.some(u=>u.pathname==='/api/dashboard/flows'&&u.searchParams.get('direction')==='INCOME'&&u.searchParams.get('from')==='2026-11-01'));});
  await open(); await page.getByRole('button',{name:'Ver gastos del período'}).click();await page.locator('.flow-total').waitFor();assert.match(await page.locator('.flow-total').innerText(),/100,00/);
  await open();
  state='loading';await page.getByLabel('Período',{exact:true}).selectOption('LAST_MONTH');await page.getByRole('status').filter({hasText:'Cargando información'}).waitFor();
  await page.screenshot({path:path.join(evidence,`loading-${width}.png`),fullPage:true});
  state='error';await page.getByLabel('Período',{exact:true}).selectOption('THIS_MONTH');await page.getByRole('alert').filter({hasText:'No se han podido'}).waitFor();
  await check(`failed period hides stale figures ${width}px`,async()=>assert.equal(await page.locator('.financial-overview').count(),0));
  await page.screenshot({path:path.join(evidence,`error-${width}.png`),fullPage:true});
  state='empty';await page.getByRole('button',{name:'Reintentar carga'}).click();await page.getByText('Sin movimientos en el período.',{exact:true}).waitFor();
  await page.screenshot({path:path.join(evidence,`empty-${width}.png`),fullPage:true});
 }
 assert.deepEqual(errors,[]);console.log(`${passed} overview checks passed; API fixtures, no bank connection.`);
 }finally{await browser?.close();await server?.close();}
})().catch(e=>{console.error(e);process.exit(1)});
