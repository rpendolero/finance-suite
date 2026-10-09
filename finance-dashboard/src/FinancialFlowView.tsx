import {useEffect, useState} from 'react';
import {api, FinancialFlow, FlowDirection, Product} from './api';

const eur = (amount: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(amount);
const dateLabel = (date: string) => new Intl.DateTimeFormat('es-ES').format(new Date(date + 'T00:00:00'));
const PAGE_SIZE = 25;

export default function FinancialFlowView({from, to, products}: {from: string; to: string; products: Product[]}) {
    const [direction, setDirection] = useState<FlowDirection>('EXPENSE');
    const period = `${from}:${to}`;
    const [page, setPage] = useState({period, offset: 0});
    const offset = page.period === period ? page.offset : 0;
    const [data, setData] = useState<FinancialFlow | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => { setPage({period, offset: 0}); }, [period]);

    useEffect(() => {
        let active = true;
        setLoading(true);
        setError('');
        setData(null);
        api.financialFlow(from, to, direction, offset, PAGE_SIZE)
            .then(result => { if (active) setData(result); })
            .catch(reason => { if (active) setError(reason instanceof Error ? reason.message : 'No se pudieron cargar los datos'); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [from, to, direction, offset]);

    const selectDirection = (value: FlowDirection) => {
        setDirection(value);
        setPage({period, offset: 0});
    };
    const income = direction === 'INCOME';
    const title = income ? 'Ingresos' : 'Gastos';
    const origins = income ? 'Origen del ingreso' : 'Comercios';
    const productNames = new Map(products.map(product => [product.id, product.name]));

    return <section className="page financial-flow">
        <div className="section-head"><h2>Análisis de ingresos y gastos</h2></div>
        <div className="flow-switch" role="group" aria-label="Tipo de análisis">
            <button type="button" aria-pressed={!income} onClick={() => selectDirection('EXPENSE')}>Gastos</button>
            <button type="button" aria-pressed={income} onClick={() => selectDirection('INCOME')}>Ingresos</button>
        </div>
        <p className="flow-note">{income
            ? 'Abonos contabilizados. Las devoluciones reducen los gastos y no se cuentan como ingresos.'
            : 'Cargos contabilizados menos devoluciones. Los movimientos mantienen su signo original.'}
            {' '}Los movimientos no computables y pendientes quedan excluidos.</p>
        {error && <div className="api-error" role="alert">{error}</div>}
        {loading ? <div className="loading" role="status">Cargando {title.toLowerCase()}…</div> : data && <>
            <div className="card flow-total">
                <span>Total de {title.toLowerCase()} del período</span>
                <strong>{eur(data.total)}</strong>
                <small>{data.operations} operaciones · {from} — {to}</small>
            </div>
            <div className="two-cols">
                <FlowRank title="Categorías" rows={data.categories.map(item => ({name: item.category, ...item}))}/>
                <FlowRank title={origins} rows={data.counterparties.map(item => ({name: item.merchant, ...item}))}/>
            </div>
            <div className="card">
                <div className="title"><h2>Movimientos de {title.toLowerCase()}</h2></div>
                {data.movements.length ? <div className="table"><table className="flow-table">
                    <thead><tr><th>Fecha</th><th>{income ? 'Origen / concepto' : 'Comercio / concepto'}</th>
                        <th>Producto</th><th>Categoría</th><th>Tratamiento</th><th>Importe</th></tr></thead>
                    <tbody>{data.movements.map(movement => <tr key={movement.id}>
                        <td>{dateLabel(movement.date)}</td>
                        <td><b>{movement.merchant || movement.description}</b>
                            {movement.merchant && <small>{movement.description}</small>}</td>
                        <td>{productNames.get(movement.productId) || movement.productId}</td>
                        <td>{movement.category || 'Sin categoría'}{movement.subcategory && <small>{movement.subcategory}</small>}</td>
                        <td>{movement.kind === 'REFUND' ? 'Devolución' : income ? 'Ingreso' : 'Gasto'}</td>
                        <td className={movement.amount > 0 ? 'pos' : 'neg'}>{eur(movement.amount)}</td>
                    </tr>)}</tbody>
                </table></div> : <div className="empty">No hay {title.toLowerCase()} en este período.</div>}
                {data.operations > 0 && <div className="pagination">
                    <span>{offset + 1}–{Math.min(offset + PAGE_SIZE, data.operations)} de {data.operations}</span>
                    <div className="flow-page-actions">
                        <button type="button" disabled={offset === 0} onClick={() => setPage({period, offset: Math.max(0, offset - PAGE_SIZE)})}>Anterior</button>
                        <button type="button" disabled={offset + PAGE_SIZE >= data.operations} onClick={() => setPage({period, offset: offset + PAGE_SIZE})}>Siguiente</button>
                    </div>
                </div>}
            </div>
        </>}
    </section>;
}

function FlowRank({title, rows}: {title: string; rows: {name: string; amount: number; operations: number; share: number}[]}) {
    return <div className="card"><div className="title"><h2>{title}</h2></div>
        {rows.length ? rows.map(row => <div className="rank" key={row.name}>
            <span><b>{row.name}</b><small>{row.operations} operaciones · {row.share.toFixed(1)} %</small></span>
            <strong>{eur(row.amount)}</strong>
        </div>) : <div className="empty">Sin datos para este período.</div>}
    </div>;
}
