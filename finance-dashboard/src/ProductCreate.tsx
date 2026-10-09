import {useState} from 'react';
import {api, Product, ProductInput} from './api';

export default function ProductCreate({products, initialName = '', initialProvider = '', cashAccountType, onCreated, onCancel}: {
    products: Product[]; initialName?: string; initialProvider?: string; cashAccountType?: string;
    onCreated: (product: Product) => void; onCancel: () => void;
}) {
    const supportedProvider = ['KUTXABANK', 'ING', 'PAYPAL'].find(p => initialProvider.toUpperCase().includes(p));
    const [name, setName] = useState(initialName), [provider, setProvider] = useState(supportedProvider || '');
    const [type, setType] = useState(cashAccountType === 'CARD' ? 'CREDIT_CARD' : supportedProvider === 'PAYPAL' ? 'WALLET' : 'ACCOUNT');
    const [balance, setBalance] = useState('0.00'), [balanceAt, setBalanceAt] = useState(new Date().toISOString().slice(0, 10));
    const [linkedAccountId, setLinkedAccountId] = useState(''), [creditLimit, setCreditLimit] = useState(''), [lastDigits, setLastDigits] = useState('');
    const [busy, setBusy] = useState(false), [error, setError] = useState('');
    const card = type === 'CREDIT_CARD' || type === 'DEBIT_CARD';
    const types = cashAccountType === 'CARD' ? ['CREDIT_CARD', 'DEBIT_CARD'] : ['CACC', 'SVGS'].includes(cashAccountType || '') ? ['ACCOUNT'] : ['ACCOUNT', 'CREDIT_CARD', 'DEBIT_CARD', 'WALLET'];
    const labels: Record<string, string> = {ACCOUNT: 'Cuenta', CREDIT_CARD: 'Tarjeta de crédito', DEBIT_CARD: 'Tarjeta de débito', WALLET: 'Monedero'};
    const submit = async (event: React.FormEvent) => {
        event.preventDefault(); setError('');
        if (!provider || !name.trim()) { setError('Indica el nombre y la entidad.'); return; }
        setBusy(true);
        try {
            const input: ProductInput = {id: crypto.randomUUID(), name: name.trim(), provider, type, currency: 'EUR', balance: Number(balance), balanceAt: new Date(`${balanceAt}T12:00:00`).toISOString(),
                ...(card && linkedAccountId ? {linkedAccountId} : {}),
                ...(card && creditLimit !== '' ? {creditLimit: Number(creditLimit)} : {}),
                ...(card && lastDigits ? {maskedPan: `**** ${lastDigits}`} : {})};
            const product = await api.saveProduct(input);
            onCreated(product);
        } catch (e) { setError(e instanceof Error ? e.message : 'No se pudo crear el producto'); }
        finally { setBusy(false); }
    };
    return <div className="app-modal-backdrop">
        <form className="app-modal product-create-modal" role="dialog" aria-modal="true" aria-labelledby="product-create-title" onSubmit={submit}>
            <div className="app-modal-head"><h3 id="product-create-title">Nuevo producto</h3></div>
            <div className="app-modal-body">
                {error && <p role="alert" className="api-error">{error}</p>}
                <label>Nombre<input autoFocus required maxLength={100} value={name} onChange={e => setName(e.target.value)}/></label>
                <label>Entidad<select required value={provider} onChange={e => {setProvider(e.target.value); setLinkedAccountId('');}}><option value="">Selecciona entidad</option><option value="KUTXABANK">Kutxabank</option><option value="ING">ING</option><option value="PAYPAL">PayPal</option></select></label>
                <label>Tipo<select value={type} onChange={e => setType(e.target.value)}>{types.map(t => <option key={t} value={t}>{labels[t]}</option>)}</select></label>
                <label>Moneda<input value="EUR" readOnly/></label>
                <label>Saldo registrado<input required type="number" step="0.01" value={balance} onChange={e => setBalance(e.target.value)}/></label>
                <label>Fecha del saldo<input required type="date" value={balanceAt} onChange={e => setBalanceAt(e.target.value)}/></label>
                <p>El saldo inicial se registra manualmente. Tras vincularlo, la sincronización consultará el saldo al banco.</p>
                {card && <>
                    <label>Cuenta asociada (opcional)<select value={linkedAccountId} onChange={e => setLinkedAccountId(e.target.value)}><option value="">Sin cuenta asociada</option>{products.filter(p => p.type === 'ACCOUNT' && p.provider === provider).map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
                    <label>Límite (opcional)<input type="number" min="0" step="0.01" value={creditLimit} onChange={e => setCreditLimit(e.target.value)}/></label>
                    <label>Últimos cuatro dígitos (opcional)<input inputMode="numeric" pattern="[0-9]{4}" maxLength={4} value={lastDigits} onChange={e => setLastDigits(e.target.value)}/></label>
                </>}
            </div>
            <div className="app-modal-actions"><button type="button" className="secondary" disabled={busy} onClick={onCancel}>Cancelar</button><button type="submit" disabled={busy}>{busy ? 'Guardando…' : 'Crear producto'}</button></div>
        </form>
    </div>;
}
