import {useEffect, useState} from 'react';
import ProductCreate from './ProductCreate';
import {api, BankAccount, BankConnection, BankSyncResult, Product} from './api';

const compatibleProduct = (account: BankAccount, product: Product) => {
    if (product.currency !== account.currency) return false;
    if (account.cashAccountType === 'CARD') return ['CREDIT_CARD', 'DEBIT_CARD'].includes(product.type);
    if (['CACC', 'SVGS'].includes(account.cashAccountType || '')) return product.type === 'ACCOUNT';
    return true;
};
const accountTypeLabel = (type?: string) => type === 'CARD' ? 'Tarjeta' : type === 'CACC' ? 'Cuenta corriente' : type === 'SVGS' ? 'Cuenta de ahorro' : type ? `Tipo: ${type}` : 'Tipo no informado';
const active = (c: BankConnection) => c.status === 'ACTIVE' && !!c.validUntil && new Date(c.validUntil).getTime() > Date.now();
const timestamp = (value?: string) => value ? new Date(value).toLocaleString('es-ES') : '—';

export default function BankingConnections({products, onProductCreated}: {products: Product[]; onProductCreated: () => void}) {
    const [creatingFor, setCreatingFor] = useState<BankAccount | null>(null);
    const [availableProducts, setAvailableProducts] = useState<Product[]>(products);
    const [banks, setBanks] = useState<string[]>([]), [bank, setBank] = useState('');
    const [connections, setConnections] = useState<BankConnection[]>([]), [selected, setSelected] = useState('');
    const [accounts, setAccounts] = useState<BankAccount[]>([]), [links, setLinks] = useState<Record<string, string>>({});
    const [busy, setBusy] = useState(false), [error, setError] = useState('');
    const [result, setResult] = useState<BankSyncResult | null>(null);
    const connection = connections.find(c => c.id === selected);
    const authorized = connection && active(connection);

    const failure = (e: unknown) => setError(e instanceof Error && e.message.includes('404')
        ? 'Enable Banking no está habilitado en el servidor. Activa la integración y configura sus credenciales.'
        : e instanceof Error ? e.message : 'No se pudo completar la operación.');
    const run = async (action: () => Promise<void>) => {
        setBusy(true); setError('');
        try { await action(); } catch (e) { failure(e); } finally { setBusy(false); }
    };
    const refresh = async () => {
        const values = await api.bankConnections();
        setConnections(values);
        setSelected(current => values.some(c => c.id === current) ? current : values.find(active)?.id || values[0]?.id || '');
    };
    useEffect(() => { void run(async () => {
        const [names, productList] = await Promise.all([api.banks(), api.products(), refresh()]);
        setAvailableProducts(productList);
        setBanks(names); setBank(names[0] || '');
    }); }, []);
    useEffect(() => {
        let current = true;
        setAccounts([]); setLinks({}); setResult(null);
        if (authorized) api.bankAccounts(selected).then(values => {
            if (current) { setAccounts(values); setLinks(Object.fromEntries(values.map(a => [a.externalAccountId, a.productId || '']))); }
        }).catch(e => { if (current) failure(e); });
        return () => { current = false; };
    }, [selected, authorized]);
    const connect = (name: string) => run(async () => {
        const authorization = await api.authorizeBank(name);
        const url = new URL(authorization.authorizationUrl);
        if (url.protocol !== 'https:') throw new Error('La autorización bancaria requiere una URL HTTPS.');
        window.location.assign(url.href);
    });
    const discover = () => run(async () => {
        const values = await api.discoverBankAccounts(selected);
        setAccounts(values); setLinks(Object.fromEntries(values.map(a => [a.externalAccountId, a.productId || ''])));
    });
    const link = (account: BankAccount) => run(async () => {
        const updated = await api.linkBankAccount(selected, account.externalAccountId, links[account.externalAccountId]);
        setAccounts(current => current.map(a => a.id === updated.id ? updated : a));
    });
    const sync = () => run(async () => {
        setResult(null);
        setResult(await api.syncBank(selected));
        await refresh();
    });
    return <section className="banking-page">
        {creatingFor && <ProductCreate products={availableProducts} initialName={creatingFor.name} initialProvider={connection?.bankName} cashAccountType={creatingFor.cashAccountType}
            onCancel={() => setCreatingFor(null)} onCreated={product => {
                setAvailableProducts(current => [...current, product]);
                setLinks(current => ({...current, [creatingFor.externalAccountId]: product.id}));
                setCreatingFor(null); onProductCreated();
            }}/>}
        <h2>Bancos · Enable Banking</h2>
        <p>Conecta tu banco, vincula cada cuenta con un producto y sincroniza sus movimientos.</p>
        {new URLSearchParams(window.location.search).get('banking') === 'connected' && <p role="status">Autorización completada. Descubre las cuentas para vincularlas.</p>}
        {new URLSearchParams(window.location.search).get('banking') === 'cancelled' && <p role="alert">La autorización se ha cancelado o el banco la ha rechazado. Puedes volver a conectar.</p>}
        {error && <div className="api-error" role="alert">{error}</div>}
        <div className="card banking-actions">
            <label>Banco<select value={bank} disabled={busy} onChange={e => setBank(e.target.value)}>
                <option value="">Selecciona banco</option>{banks.map(name => <option key={name}>{name}</option>)}
            </select></label>
            <button disabled={busy || !bank} onClick={() => void connect(bank)}>Conectar banco</button>
            <button disabled={busy} onClick={() => void run(refresh)}>Actualizar conexiones</button>
        </div>
        <div className="card banking-actions">
            <label>Conexión<select value={selected} disabled={busy} onChange={e => setSelected(e.target.value)}>
                <option value="">Selecciona conexión</option>{connections.map(c => <option value={c.id} key={c.id}>{c.bankName} · {active(c) ? 'Activa' : 'Pendiente / requiere autorización'}</option>)}
            </select></label>
            {connection && <span>Consentimiento hasta {timestamp(connection.validUntil)} · Última sincronización {timestamp(connection.lastSyncAt)}</span>}
            {connection && !authorized && <button disabled={busy} onClick={() => void connect(connection.bankName)}>Volver a autorizar</button>}
            <button disabled={busy || !authorized} onClick={() => void discover()}>Descubrir cuentas</button>
        </div>
        {authorized && <div className="card">
            <h3>Cuentas y productos</h3>
            {!accounts.length && <p>Pulsa «Descubrir cuentas» para obtener las cuentas autorizadas.</p>}
            {accounts.map(a => <div className="banking-account" key={a.id}>
                <div><b>{a.name}</b><small>{accountTypeLabel(a.cashAccountType)} · {a.currency} · {a.productId ? 'Vinculada' : 'Pendiente de vincular'}</small></div>
                <label>Producto<select disabled={busy} value={links[a.externalAccountId] || ''} onChange={e => setLinks({...links, [a.externalAccountId]: e.target.value})}>
                    <option value="">Selecciona producto</option>{availableProducts.filter(p => compatibleProduct(a, p)).map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select></label>
                {!availableProducts.some(p => compatibleProduct(a, p)) && <small>No hay productos compatibles. Crea un producto para vincularlo.</small>}
                <button disabled={busy} onClick={() => setCreatingFor(a)}>Nuevo producto</button>
                <button disabled={busy || !links[a.externalAccountId] || links[a.externalAccountId] === a.productId || !availableProducts.some(p => p.id === links[a.externalAccountId] && compatibleProduct(a, p))} onClick={() => void link(a)}>Guardar vínculo</button>
            </div>)}
            <p>Solo se sincronizan las cuentas con un vínculo guardado. Puedes seguir importando ficheros.</p>
            <button disabled={busy || !accounts.some(a => a.productId)} onClick={() => void sync()}>{busy ? 'Procesando…' : 'Sincronizar movimientos'}</button>
            {result && <p role="status">Leídos: {result.read} · Insertados: {result.inserted} · Duplicados: {result.duplicates}</p>}
        </div>}
    </section>;
}
