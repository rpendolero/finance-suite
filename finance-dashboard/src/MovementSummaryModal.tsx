import {useEffect, useState} from 'react';
import {api, type Movement, type Product} from './api';

const excludedKinds = new Set(['INTERNAL_TRANSFER', 'CARD_SETTLEMENT', 'WALLET_SETTLEMENT', 'NON_COMPUTABLE', 'DUPLICATE']);
const eur = (value: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(value);

export default function MovementSummaryModal({selection, from, to, products, onClose}: {
    selection: {productId?: string; date?: string};
    from: string; to: string; products: Product[]; onClose: () => void;
}) {
    const [rows, setRows] = useState<Movement[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    useEffect(() => {
        let active = true;
        setLoading(true); setError(''); setRows([]);
        const load = async () => {
            const result: Movement[] = [];
            let offset = 0;
            while (true) {
                const page = await api.searchMovements({
                    from: selection.date || from, to: selection.date || to,
                    productId: selection.productId, offset, limit: 200,
                    sortBy: 'DATE', sortDirection: 'DESC'
                });
                result.push(...page.items.filter(m => !excludedKinds.has(m.kind) && m.category !== 'NO_COMPUTABLE'));
                offset += page.items.length;
                if (!page.items.length || offset >= page.total) break;
            }
            if (active) setRows(result);
        };
        load().catch(e => {if (active) setError(e instanceof Error ? e.message : 'No se pudieron cargar los movimientos');})
            .finally(() => {if (active) setLoading(false);});
        return () => {active = false;};
    }, [selection, from, to]);
    useEffect(() => {
        const onKey = (event: KeyboardEvent) => {if (event.key === 'Escape') onClose();};
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [onClose]);
    const productName = products.find(p => p.id === selection.productId)?.name;
    const expenses = rows.filter(m => m.amount < 0).reduce((sum, m) => sum - Number(m.amount), 0);
    const income = rows.filter(m => m.amount > 0).reduce((sum, m) => sum + Number(m.amount), 0);
    return <div className="movement-summary-backdrop" onMouseDown={e => {if (e.target === e.currentTarget) onClose();}}>
        <section className="movement-summary-modal" role="dialog" aria-modal="true" aria-labelledby="movement-summary-title">
            <header className="movement-summary-header">
                <div><span className="products-eyebrow">MOVIMIENTOS COMPUTABLES</span>
                    <h2 id="movement-summary-title">{productName || (selection.date ? new Date(selection.date + 'T00:00:00').toLocaleDateString('es-ES', {dateStyle: 'long'}) : 'Resumen de movimientos')}</h2>
                    <p>{selection.date || (from + ' — ' + to)}</p></div>
                <button type="button" className="movement-summary-close" aria-label="Cerrar resumen" onClick={onClose}>×</button>
            </header>
            {loading ? <p className="loading">Cargando movimientos…</p> : error ? <p role="alert" className="api-error">{error}</p> : <>
                <div className="movement-summary-kpis">
                    <div><small>Movimientos</small><strong>{rows.length}</strong></div>
                    <div><small>Ingresos</small><strong className="pos">{eur(income)}</strong></div>
                    <div><small>Gastos</small><strong className="neg">{eur(expenses)}</strong></div>
                    <div><small>Balance</small><strong>{eur(income - expenses)}</strong></div>
                </div>
                <div className="movement-summary-list">
                    {rows.length ? rows.map(m => <div className="movement-summary-item" key={m.id}>
                        <time dateTime={m.date}>{m.date}</time>
                        <div><strong>{m.merchant || m.description}</strong><small>{m.category || 'Sin categoría'}{m.subcategory ? ' · ' + m.subcategory : ''}</small></div>
                        <b className={m.amount >= 0 ? 'pos' : 'neg'}>{eur(Number(m.amount))}</b>
                    </div>) : <p className="empty">No hay movimientos computables en esta selección.</p>}
                </div>
            </>}
        </section>
    </div>;
}
