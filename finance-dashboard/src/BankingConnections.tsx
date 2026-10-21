import {useEffect, useState} from 'react';
import ProductCreate from './ProductCreate';
import {api, BankAccount, BankConnection, BankSyncResult, Product} from './api';

const domains: Record<string, string> = {
  kutxabank: 'kutxabank.es', ing: 'ing.es', santander: 'bancosantander.es',
  bbva: 'bbva.es', caixabank: 'caixabank.es', sabadell: 'bancsabadell.com',
  bankinter: 'bankinter.com', unicaja: 'unicajabanco.es', abanca: 'abanca.com',
  openbank: 'openbank.es', revolut: 'revolut.com', n26: 'n26.com',
  paypal: 'paypal.com', ibercaja: 'ibercaja.es', cajamar: 'grupocooperativocajamar.es'
};
const normalize = (name: string) => name.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]/g, '');
function BankLogo({name}: {name: string}) {
  const [failed, setFailed] = useState(false);
  const key = normalize(name);
  const domain = Object.entries(domains).find(([alias]) => key.includes(alias))?.[1];
  useEffect(() => setFailed(false), [name]);
  return <span className="bank-logo" aria-hidden="true" style={{width: 48, height: 48, flexShrink: 0, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', borderRadius: 12, background: 'var(--surface-secondary, #f1f5f9)', overflow: 'hidden'}}>
    {domain && !failed ? <img src={`https://www.google.com/s2/favicons?domain=${encodeURIComponent(domain)}&sz=128`} alt="" width="38" height="38" onError={() => setFailed(true)}/> : <span style={{fontSize: 23}}>🏦</span>}
  </span>;
}
const active = (c: BankConnection) => c.status === 'ACTIVE' && !!c.validUntil && new Date(c.validUntil).getTime() > Date.now();
const timestamp = (value?: string) => value ? new Date(value).toLocaleString('es-ES') : '—';
const compatible = (a: BankAccount, p: Product) => (a.currency === 'XXX' || a.currency === p.currency) &&
  (a.cashAccountType === 'CARD' ? ['CREDIT_CARD','DEBIT_CARD'].includes(p.type) : ['CACC','SVGS'].includes(a.cashAccountType || '') ? p.type === 'ACCOUNT' : true);
const statusLabel = (c: BankConnection) => active(c) ? 'Conectado' : c.status === 'AUTHORIZING' ? 'Autorización pendiente' : 'Requiere autorización';

export default function BankingConnections({products, onProductCreated}: {products: Product[]; onProductCreated: () => void}) {
  const [banks, setBanks] = useState<string[]>([]);
  const [bank, setBank] = useState('');
  const [adding, setAdding] = useState(false);
  const closeDetails = () => { setSelected(''); setResult(null); setError(''); };
  const [connections, setConnections] = useState<BankConnection[]>([]);
  const [selected, setSelected] = useState('');
  const [accounts, setAccounts] = useState<BankAccount[]>([]);
  const [links, setLinks] = useState<Record<string,string>>({});
  const [availableProducts, setAvailableProducts] = useState<Product[]>(products);
  const [creatingFor, setCreatingFor] = useState<BankAccount | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<BankSyncResult | null>(null);
  const connection = connections.find(c => c.id === selected);
  const authorized = !!connection && active(connection);
  const failure = (e: unknown) => setError(e instanceof Error ? e.message : 'No se pudo completar la operación.');
  const run = async (action: () => Promise<void>) => {
    setBusy(true); setError('');
    try { await action(); } catch (e) { failure(e); } finally { setBusy(false); }
  };
  const refresh = async () => setConnections(await api.bankConnections());
  useEffect(() => { void run(async () => {
    const [names, productsLoaded, connectionsLoaded] = await Promise.all([api.banks(), api.products(), api.bankConnections()]);
    setBanks(names); setBank(names[0] || ''); setAvailableProducts(productsLoaded); setConnections(connectionsLoaded);
  }); }, []);
  useEffect(() => {
    if (!selected || !authorized) { setAccounts([]); setLinks({}); return; }
    let mounted = true;
    api.bankAccounts(selected).then(items => {
      if (mounted) { setAccounts(items); setLinks(Object.fromEntries(items.map(a => [a.externalAccountId, a.productId || '']))); }
    }).catch(e => { if (mounted) failure(e); });
    return () => { mounted = false; };
  }, [selected, authorized]);
  const redirect = (url: string) => {
    const destination = new URL(url);
    if (destination.protocol !== 'https:') throw new Error('La autorización requiere una dirección HTTPS.');
    window.location.assign(destination.href);
  };
  const connect = () => run(async () => redirect((await api.authorizeBank(bank)).authorizationUrl));
  const reauthorize = (id: string) => run(async () => redirect((await api.reauthorizeBank(id)).authorizationUrl));
  const discover = () => run(async () => {
    const items = await api.discoverBankAccounts(selected);
    setAccounts(items); setLinks(Object.fromEntries(items.map(a => [a.externalAccountId, a.productId || ''])));
  });
  const link = (a: BankAccount) => run(async () => {
    const updated = await api.linkBankAccount(selected, a.externalAccountId, links[a.externalAccountId]);
    setAccounts(items => items.map(item => item.id === updated.id ? updated : item));
  });
  const sync = () => run(async () => {
    setResult(null);
    try {
      setResult(await api.syncBank(selected));
      await refresh(); setAvailableProducts(await api.products()); onProductCreated();
    } catch (e) {
      await refresh();
      throw e;
    }
  });
  const linkedCount = accounts.filter(a => !!a.productId).length;
  return <section className="banking-page">
    {creatingFor && <ProductCreate products={availableProducts} initialName={creatingFor.name} initialProvider={connection?.bankName}
      cashAccountType={creatingFor.cashAccountType} onCancel={() => setCreatingFor(null)} onCreated={p => {
        setAvailableProducts(items => [...items, p]); setLinks(items => ({...items, [creatingFor.externalAccountId]: p.id}));
        setCreatingFor(null); onProductCreated();
      }}/>}
    <div className="finance-page-hero"><div><span className="products-eyebrow">CONEXIONES SEGURAS</span><h2>Bancos y cuentas</h2><p>Consulta el estado de tus bancos y sincroniza tus movimientos.</p></div></div>
    {new URLSearchParams(window.location.search).get('banking') === 'connected' && <p role="status">Autorización completada. Abre el banco y actualiza sus cuentas.</p>}
    {new URLSearchParams(window.location.search).get('banking') === 'cancelled' && <p role="alert">La autorización fue cancelada. Puedes intentarlo de nuevo.</p>}
    {error && <div className="api-error" role="alert">{error}</div>}
    <>
      <div className="card banking-actions" style={{display:'flex',gap:16,flexWrap:'wrap',alignItems:'center'}}>
        <div><strong>{connections.length}</strong><small style={{display:'block'}}>Conexiones</small></div>
        <div><strong>{connections.filter(active).length}</strong><small style={{display:'block'}}>Activas</small></div>
        <div><strong>{connections.filter(c => !active(c)).length}</strong><small style={{display:'block'}}>Requieren atención</small></div>
        <button disabled={busy} onClick={() => setAdding(true)}>+ Añadir banco</button>
        <button disabled={busy} onClick={() => void run(refresh)}>Actualizar</button>
      </div>
      <div style={{display:'grid',gap:12}}>
        {connections.map(c => <div className="card" key={c.id} style={{display:'flex',alignItems:'center',gap:16,cursor:'pointer'}} onClick={() => setSelected(c.id)}>
          <BankLogo name={c.bankName}/><div style={{flex:1}}><strong>{c.bankName}</strong><small style={{display:'block'}}>{statusLabel(c)} · Última sincronización: {timestamp(c.lastSyncAt)}</small></div>
          <span aria-hidden="true">›</span></div>)}
        {!connections.length && <div className="card">Todavía no hay bancos conectados. Pulsa «Añadir banco» para empezar.</div>}
      </div>
    </>
    {adding && <div className="app-modal-backdrop" onMouseDown={e => {if (e.target === e.currentTarget && !busy) setAdding(false);}}>
      <div className="app-modal" role="dialog" aria-modal="true" aria-labelledby="add-bank-title" style={{maxWidth:520,width:'100%'}}>
        <div className="app-modal-head"><h3 id="add-bank-title">Añadir banco</h3><button type="button" className="secondary" disabled={busy} onClick={() => setAdding(false)} aria-label="Cerrar">✕</button></div>
        <div className="app-modal-body"><p>Selecciona la entidad que quieres conectar mediante Enable Banking.</p><label>Entidad bancaria<select value={bank} onChange={e => setBank(e.target.value)}><option value="">Selecciona un banco</option>{banks.map(name => <option key={name} value={name}>{name}</option>)}</select></label>{error && <p className="api-error" role="alert">{error}</p>}</div>
        <div className="app-modal-actions"><button type="button" className="secondary" disabled={busy} onClick={() => setAdding(false)}>Cancelar</button><button type="button" disabled={busy || !bank} onClick={() => void connect()}>{busy ? 'Conectando…' : 'Conectar banco'}</button></div>
      </div>
    </div>}
    {connection && <div className="app-modal-backdrop" onMouseDown={e => {if (e.target === e.currentTarget && !busy && !creatingFor) closeDetails();}}>
      <div className="app-modal banking-detail-modal" role="dialog" aria-modal="true" aria-labelledby="bank-detail-title" style={{width:'min(960px, 96vw)',maxWidth:'960px',maxHeight:'90vh',overflowY:'auto'}}>
      <div className="banking-actions" style={{display:'flex',gap:12,alignItems:'center',marginBottom:16}}>
        <button disabled={busy} onClick={closeDetails} aria-label="Cerrar detalles del banco">✕ Cerrar</button>
        <BankLogo name={connection.bankName}/><h3 id="bank-detail-title">{connection.bankName}</h3>
      </div>
      <div className="card">
        <h3>Estado de conexión</h3><p className="bank-connection-status"><span aria-hidden="true">{authorized ? "●" : connection.status === "AUTHORIZING" ? "◷" : "⚠"}</span> {statusLabel(connection)}</p>
        <p>Autorización hasta: {timestamp(connection.validUntil)} · Última sincronización: {timestamp(connection.lastSyncAt)}</p>
        {!authorized && <p>Necesitas renovar el acceso para recuperar la sincronización.</p>}
        <div className="banking-actions">
          <button disabled={busy} onClick={() => void reauthorize(connection.id)}><><span aria-hidden="true">↻</span> {busy ? 'Procesando…' : 'Renovar acceso bancario'}</></button>
          {authorized && <button disabled={busy} onClick={() => void discover()}><span aria-hidden="true">⟳</span> Actualizar cuentas</button>}
          {authorized && <button disabled={busy || linkedCount === 0} onClick={() => void sync()}><span aria-hidden="true">⇄</span> Sincronizar ahora</button>}
        </div>
      </div>
      {authorized && <div className="card">
        <h3>Cuentas y productos</h3>
        <p>{accounts.length} cuentas · {linkedCount} vinculadas</p>
        {!accounts.length && <p>Selecciona «Actualizar cuentas» para descubrir las cuentas autorizadas.</p>}
        {accounts.map(a => <div className="banking-account" key={a.id}>
          <div><b>{a.name}</b><small>{a.currency} · {a.productId ? 'Vinculada' : 'Pendiente de vincular'}</small></div>
          <label className="banking-product-field">Producto<select disabled={busy} value={links[a.externalAccountId] || ''} onChange={e => setLinks(v => ({...v,[a.externalAccountId]:e.target.value}))}>
            <option value="">Selecciona producto</option>{availableProducts.filter(p => compatible(a,p)).map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select></label>
          <button disabled={busy} onClick={() => setCreatingFor(a)}>Nuevo producto</button>
          <button disabled={busy || !links[a.externalAccountId] || links[a.externalAccountId] === a.productId} onClick={() => void link(a)}>Guardar vínculo</button>
        </div>)}
        {result && <p role="status">Leídos: {result.read} · Nuevos: {result.inserted} · Duplicados: {result.duplicates} · Saldos actualizados: {result.balancesUpdated}</p>}
      </div>}
      </div>
    </div>}
  </section>;
}
