const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {build} = require('vite');
const React = require('react');
const {renderToStaticMarkup} = require('react-dom/server');
const temp = fs.mkdtempSync(path.join(os.tmpdir(),'finance-overview-'));
(async()=>{try{
 fs.symlinkSync(path.resolve('node_modules'),path.join(temp,'node_modules'),'dir');
 await build({logLevel:'silent',build:{outDir:temp,emptyOutDir:false,lib:{entry:{overview:path.resolve('src/FinancialOverview.tsx'),forecast:path.resolve('src/ForecastCard.tsx')},formats:['cjs'],fileName:(_f,n)=>n+'.cjs'},rollupOptions:{external:['react','react/jsx-runtime','recharts']}}});
 const overviewModule = require(path.join(temp,'overview.cjs'));
 const Overview = overviewModule.default || overviewModule;
 const forecastModule = require(path.join(temp,'forecast.cjs'));
 const Forecast = forecastModule.default || forecastModule;
 const props={overview:{totalBalance:1900,income:2000,expenses:100,availableAfterAllocations:1900,savings:0,savingsRate:0,financingPayments:0,financingCapital:0,financingInterest:0,financingUnallocated:0},trend:[{date:'2026-11-04',income:2000,expenses:100,savings:0}],categories:Array.from({length:9},(_,i)=>({category:'CATEGORY_'+i,amount:10,operations:1,share:10})),products:[{id:'account',name:'Cuenta sintética',type:'ACCOUNT',provider:'ING',currency:'EUR',balance:1900,balanceAt:'2026-11-09T09:00:00Z'},{id:'card',name:'Tarjeta sintética',type:'CREDIT_CARD',provider:'ING',currency:'EUR',balance:-100}],movements:[{id:'salary',date:'2026-11-02',amount:2000,description:'Nómina',kind:'NORMAL',status:'BOOKED',category:'INGRESOS'},{id:'settlement',date:'2026-11-06',amount:-100,description:'Liquidación',kind:'CARD_SETTLEMENT',status:'BOOKED',category:'NO_COMPUTABLE'}],forecast:null,forecastError:'',from:'2026-11-01',to:'2026-11-30',onIncome:()=>{},onExpenses:()=>{},onProducts:()=>{},onMovements:()=>{}};
 const render = p=>renderToStaticMarkup(React.createElement(Overview,p));
 let html=render(props);
 for(const text of ['Saldo registrado','Resultado del período','1900,00','2000,00','100,00','No depende del período','No depende del período ni descuenta deuda de tarjetas','Ver ingresos del período','Ver gastos del período','Excluido del resultado','CATEGORY_8','Saldos con fecha','9/11/2026']){
  assert.ok(html.includes(text),text);
 }
 assert.ok(html.includes('<caption>Ingresos y gastos computables'));
 assert.ok(!html.includes('Tarjeta sintética'),'card debt must not appear among liquid accounts');
 assert.ok(!html.includes('NaN'));
 console.log('OK overview preserves API values, dates, all categories and exclusion labels');
 html=render({...props,movements:[{id:'usd',date:'2026-11-09',amount:-100,currency:'USD',description:'Traspaso USD',kind:'INTERNAL_TRANSFER',status:'BOOKED'}]});
 assert.match(html,/-100,00[^<]*US\$/);assert.ok(!html.match(/-100,00[^<]*€/));
 html=render({...props,movements:[{id:'unknown',date:'2026-11-09',amount:-100,description:'Moneda desconocida',kind:'NON_COMPUTABLE',status:'BOOKED'}]});
 assert.ok(html.includes('moneda no disponible'));assert.ok(!html.match(/-100,00[^<]*€/));
 console.log('OK activity preserves foreign currencies and never assumes EUR for an unknown currency');
 html=render({...props,trend:[],categories:[],products:[],movements:[]});
 for(const text of ['Sin movimientos en el período','Sin gastos clasificados','No hay cuentas ni monederos','Sin movimientos recientes'])assert.ok(html.includes(text));
 console.log('OK overview empty states remain explicit');
 const f={asOf:'2026-11-09',days:30,accountBalances:1900,estimatedNetFlow:6333.34,estimatedAccountBalance:8233.34,limitations:'9 días observados'};
 html=renderToStaticMarkup(React.createElement(Forecast,{forecast:f,from:'2026-11-01',to:'2026-11-30'}));
 assert.ok(html.includes('9/11/2026'));assert.ok(!html.includes('30/11/2026'));
 assert.ok(html.includes('No es un saldo bancario confirmado'));
 assert.ok(html.includes('Hipótesis y límites'));assert.ok(html.includes('9 días observados'));
 console.log('OK forecast shows effective history, not unobserved future days');
 // WCAG relative luminance for text palette, including the dark balance panel.
 const lum=h=>{const rgb=h.match(/[0-9a-f]{2}/gi).map(x=>parseInt(x,16)/255).map(x=>x<=.04045?x/12.92:((x+.055)/1.055)**2.4);return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722;};
 for(const [fg,bg] of [['18323b','ffffff'],['4d6065','ffffff'],['4d6065','faf8f1'],['087566','faf8f1'],['a8412c','faf8f1'],['a9dfcc','16333d'],['d5e5e6','16333d'],['ffffff','16333d'],['ddebea','16333d'],['b7d6d8','16333d'],['16333d','d6f1e5'],['385467','edf3f7'],['405968','edf3f7'],['18323b','edf3f7'],['076a5c','edf3f7'],['a33226','edf3f7']]){
  const ratio=(Math.max(lum(fg),lum(bg))+.05)/(Math.min(lum(fg),lum(bg))+.05);assert.ok(ratio>=4.5,fg+'/'+bg+': '+ratio.toFixed(2));
 }
 console.log('OK 16 overview text color pairs reach 4.5:1 contrast (palette calculation, no browser audit)');
 console.log('5 overview render checks passed');
}finally{fs.rmSync(temp,{recursive:true,force:true});}})().catch(e=>{console.error(e);process.exit(1)});
