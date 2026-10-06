import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { ListToolsRequestSchema, CallToolRequestSchema } from '@modelcontextprotocol/sdk/types.js';
const secret = process.env.FINANCE_READER_PASSWORD;
if (!secret || secret.length < 20) throw new Error('FINANCE_READER_PASSWORD is required');
const url = new URL(process.env.FINANCE_MCP_URL ?? 'http://127.0.0.1:8081/mcp');
if (url.protocol !== 'https:' && !['127.0.0.1','localhost','[::1]'].includes(url.hostname)) throw new Error('Remote MCP requires HTTPS');
const client = new Client({name:'finance-local-bridge',version:'0.4.0'});
await client.connect(new StreamableHTTPClientTransport(url,{requestInit:{headers:{Authorization:`Basic ${Buffer.from(`reader:${secret}`).toString('base64')}`}}}));
const server = new Server({name:'personal-finance',version:'0.4.0'},{capabilities:{tools:{}}});
server.setRequestHandler(ListToolsRequestSchema, async () => {
 const {tools} = await client.listTools();
 return {tools:tools.filter(t=>t.name.startsWith('bank_')).map(t=>({...t,annotations:{...t.annotations,readOnlyHint:true,destructiveHint:false,idempotentHint:true,openWorldHint:false}}))};
});
server.setRequestHandler(CallToolRequestSchema, async ({params}) => {
 if (!params.name.startsWith('bank_')) throw new Error('Tool not allowed');
 return await client.callTool({name:params.name,arguments:params.arguments});
});
await server.connect(new StdioServerTransport());
let closing=false;
async function close(){if(closing)return;closing=true;await client.close();await server.close();}
process.on('SIGINT',()=>close().finally(()=>process.exit(0)));
process.on('SIGTERM',()=>close().finally(()=>process.exit(0)));
