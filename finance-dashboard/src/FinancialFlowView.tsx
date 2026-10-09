import {useEffect, useId, useState} from 'react';
import {ChevronDown} from 'lucide-react';
import {api, FinancialFlow, FlowDirection, Product} from './api';

const eur = (amount: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(amount);
const dateLabel = (date: string) => new Intl.DateTimeFormat('es-ES').format(new Date(date + 'T00:00:00'));
const PAGE_SIZE = 25;
type Expansion = {context: string; category: string; offset: number};

export default function FinancialFlowView({from, to, products}: {from: string; to: string; products: Product[]}) {
    const [direction, setDirection] = useState<FlowDirection>('EXPENSE');
    const context = `${from}:${to}:${direction}`;
    const [expanded, setExpanded] = useState<Expansion | null>(null);
    const category = expanded?.context === context ? expanded.category : undefined;
    const offset = expanded?.context === context ? expanded.offset : 0;
    const [summary, setSummary] = useState<{context: string; data: FinancialFlow} | null>(null);
    const [summaryError, setSummaryError] = useState('');
    const detailKey = JSON.stringify([context, category, offset]);
    const [detail, setDetail] = useState<{key: string; data?: FinancialFlow; error?: string} | null>(null);
    const accordionId = useId();

    useEffect(() => { setExpanded(null); }, [context]);

    useEffect(() => {
        let active = true;
        setSummaryError('');
        setSummary(null);
        api.financialFlow(from, to, direction, 0, 1)
            .then(data => { if (active) setSummary({context, data}); })
            .catch(reason => { if (active) setSummaryError(reason instanceof Error ? reason.message : 'No se pudieron cargar las categorías'); });
        return () => { active = false; };
    }, [from, to, direction, context]);

    useEffect(() => {
        let active = true;
        setDetail(null);
        if (category !== undefined) {
            api.financialFlow(from, to, direction, offset, PAGE_SIZE, category)
                .then(data => { if (active) setDetail({key: detailKey, data}); })
                .catch(reason => { if (active) setDetail({key: detailKey, error: reason instanceof Error ? reason.message : 'No se pudieron cargar los movimientos'}); });
        }
        return () => { active = false; };
    }, [from, to, direction, category, offset, detailKey]);

    const income = direction === 'INCOME';
    const title = income ? 'Ingresos' : 'Gastos';
    const data = summary?.context === context ? summary.data : null;
    const selectedDetail = detail?.key === detailKey ? detail : null;
    const productNames = new Map(products.map(product => [product.id, product.name]));
    const selectDirection = (value: FlowDirection) => {
        if (value !== direction) {
            setDirection(value);
            setExpanded(null);
        }
    };
    const toggleCategory = (value: string) => setExpanded(category === value ? null : {context, category: value, offset: 0});

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
        {summaryError ? <div className="api-error" role="alert">{summaryError}</div> : !data ?
            <div className="loading" role="status">Cargando {title.toLowerCase()}…</div> : <>
            <div className="card flow-total">
                <span>Total de {title.toLowerCase()} del período</span>
                <strong>{eur(data.total)}</strong>
                <small>{data.operations} operaciones · {from} — {to}</small>
            </div>
            <div className="card flow-categories">
                <div className="title"><h2>Categorías</h2></div>
                <p className="flow-note">Pulsa una categoría para mostrar u ocultar sus movimientos.</p>
                {data.categories.length ? data.categories.map((item, index) => {
                    const open = category === item.category;
                    const regionId = `${accordionId}-category-${index}`;
                    return <section className="flow-category" key={item.category}>
                        <h3><button type="button" className="flow-category-toggle" id={`${regionId}-toggle`}
                            aria-expanded={open} aria-controls={regionId} onClick={() => toggleCategory(item.category)}>
                            <span><b>{item.category}</b><small>{item.operations} operaciones · {item.share.toFixed(1)} %</small></span>
                            <strong>{eur(item.amount)}</strong><ChevronDown size={20} aria-hidden="true"/>
                        </button></h3>
                        {open && <div className="flow-category-detail" id={regionId} role="region" aria-labelledby={`${regionId}-toggle`}>
                            {selectedDetail?.error ? <div className="api-error" role="alert">{selectedDetail.error}</div> : !selectedDetail?.data ?
                                <div className="loading" role="status">Cargando movimientos…</div> :
                                <CategoryMovements data={selectedDetail.data} income={income} productNames={productNames}
                                    offset={offset} onPage={value => setExpanded({context, category: item.category, offset: value})}/>}
                        </div>}
                    </section>;
                }) : <div className="empty">No hay {title.toLowerCase()} en este período.</div>}
            </div>
        </>}
    </section>;
}

function CategoryMovements({data, income, productNames, offset, onPage}: {
    data: FinancialFlow; income: boolean; productNames: Map<string, string>; offset: number; onPage: (offset: number) => void;
}) {
    return <>
        {data.movements.length ? <div className="table"><table className="flow-table">
            <thead><tr><th>Fecha</th><th>{income ? 'Origen / concepto' : 'Comercio / concepto'}</th>
                <th>Producto</th><th>Subcategoría</th><th>Tratamiento</th><th>Importe</th></tr></thead>
            <tbody>{data.movements.map(movement => <tr key={movement.id}>
                <td>{dateLabel(movement.date)}</td>
                <td><b>{movement.merchant || movement.description}</b>
                    {movement.merchant && <small>{movement.description}</small>}</td>
                <td>{productNames.get(movement.productId) || movement.productId}</td>
                <td>{movement.subcategory || 'Sin subcategoría'}</td>
                <td>{movement.kind === 'REFUND' ? 'Devolución' : income ? 'Ingreso' : 'Gasto'}</td>
                <td className={movement.amount > 0 ? 'pos' : 'neg'}>{eur(movement.amount)}</td>
            </tr>)}</tbody>
        </table></div> : <div className="empty">No hay movimientos en esta categoría.</div>}
        {data.operations > 0 && <div className="pagination">
            <span>{offset + 1}–{Math.min(offset + PAGE_SIZE, data.operations)} de {data.operations}</span>
            <div className="flow-page-actions">
                <button type="button" disabled={offset === 0} onClick={() => onPage(Math.max(0, offset - PAGE_SIZE))}>Anterior</button>
                <button type="button" disabled={offset + PAGE_SIZE >= data.operations} onClick={() => onPage(offset + PAGE_SIZE)}>Siguiente</button>
            </div>
        </div>}
    </>;
}
