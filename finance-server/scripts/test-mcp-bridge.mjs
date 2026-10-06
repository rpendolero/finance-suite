import assert from 'node:assert/strict';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
const client=new Client({name:'finance-smoke-test',version:'1'});
const transport=new StdioClientTransport({command:process.execPath,args:['scripts/openclaw-bridge.mjs'],env:{...process.env,FINANCE_READER_PASSWORD:'reader-secret-for-test-12345',FINANCE_MCP_URL:'http://127.0.0.1:18081/mcp'}});
try {
 await client.connect(transport);
 const {tools}=await client.listTools();
 assert.equal(tools.length,18);
 assert.ok(tools.every(t=>t.name.startsWith('bank_')&&t.annotations.readOnlyHint));
 const result=await client.callTool({name:'bank_get_summary',arguments:{from:'2026-09-01',to:'2026-09-30',productId:''}});
 assert.ok(!result.isError);
 const summary=JSON.parse(result.content.find(c=>c.type==='text').text);
 assert.equal(summary.income,3000);assert.equal(summary.expenses,1050);assert.equal(summary.net,1950);
 const report=await client.callTool({name:'bank_get_financial_report',arguments:{from:'2026-07-01',to:'2026-09-30'}});
 assert.ok(!report.isError);
 const data=JSON.parse(report.content.find(c=>c.type==='text').text);
 assert.equal(data.cardExposure[0].utilizationPercent,7.5);
 for(const [name,args] of [
  ['bank_get_products',{}],
 ['bank_get_provider_summary',{from:'2026-09-01',to:'2026-09-30',provider:'KUTXABANK'}],
  ['bank_get_transactions',{from:'2026-09-01',to:'2026-09-30',productId:'',offset:0,limit:100}],
  ['bank_compare_periods',{from:'2026-09-01',to:'2026-09-30',previousFrom:'2026-08-01',previousTo:'2026-08-31',productId:''}],
  ['bank_get_recurring_expenses',{from:'2026-07-01',to:'2026-09-30'}],
  ['bank_get_anomalies',{from:'2026-07-01',to:'2026-09-30'}],
  ['bank_get_card_exposure',{}],
  ['bank_forecast_cash_flow',{from:'2026-07-01',to:'2026-09-30',days:30}],
  ['bank_get_data_quality',{from:'2026-09-01',to:'2026-09-30'}],
  ['bank_get_monthly_summary',{month:'2026-09'}],
  ['bank_get_monthly_trend',{fromMonth:'2026-07',toMonth:'2026-09'}],
  ['bank_get_merchant_spending',{from:'2026-09-01',to:'2026-09-30'}],
  ['bank_get_budget_status',{month:'2026-09'}],
  ['bank_get_reconciliation_candidates',{from:'2026-09-01',to:'2026-09-30'}],
  ['bank_get_recurring_increases',{from:'2026-07-01',to:'2026-09-30'}],
  ['bank_get_account_cash_flow',{from:'2026-09-01',to:'2026-09-30'}]
 ]){const response=await client.callTool({name,arguments:args});assert.ok(!response.isError,`${name} failed`);}
 console.log('PASS: 18 read-only tools, all invoked over stdio → HTTP, summary and card exposure verified');
}finally{await client.close();}
