import {useCallback, useEffect, useId, useState} from 'react';
import {ChevronDown} from 'lucide-react';
import MovementResults from './MovementResults';
import {api, CategoryDefinition, FinancialFlow, FlowDirection, Product} from './api';

const eur = (amount: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(amount);
type Expansion = {context: string; category: string};

export default function FinancialFlowView({from, to, products, canEdit, onClassified, initialDirection = 'EXPENSE'}: {
    from: string; to: string; products: Product[]; canEdit: boolean; onClassified: () => void; initialDirection?: FlowDirection;
}) {
    const [direction, setDirection] = useState<FlowDirection>(initialDirection);
    const context = `${from}:${to}:${direction}`;
    const [expanded, setExpanded] = useState<Expansion | null>(null);
    const category = expanded?.context === context ? expanded.category : undefined;
    const [summary, setSummary] = useState<{context: string; data: FinancialFlow} | null>(null);
    const [summaryError, setSummaryError] = useState('');
    const [revision, setRevision] = useState(0);
    const [catalog, setCatalog] = useState<CategoryDefinition[]>([]);
    const [categoryNames, setCategoryNames] = useState<Record<string, string>>({});
    const [catalogError, setCatalogError] = useState('');
    const [message, setMessage] = useState('');
    const accordionId = useId();

    useEffect(() => { setExpanded(null); setMessage(''); }, [context]);

    useEffect(() => {
        let active = true;
        api.adminCategories().then(items => {
            if (active) setCategoryNames(Object.fromEntries(items.map(item => [item.code, item.name])));
        }).catch(() => { /* Fall back to public catalog labels for non-admin users. */ });
        return () => { active = false; };
    }, []);

    useEffect(() => {
        let active = true;
        api.categoriesCatalog().then(result => { if (active) setCatalog(result); })
            .catch(reason => { if (active) setCatalogError(reason instanceof Error ? reason.message : 'No se pudo cargar el catálogo'); });
        return () => { active = false; };
    }, []);

    useEffect(() => {
        let active = true;
        setSummaryError('');
        api.financialFlow(from, to, direction, 0, 1)
            .then(data => {
                if (!active) return;
                setSummary({context, data});
                setExpanded(current => current?.context === context && !data.categories.some(item => item.category === current.category)
                    ? null : current);
            })
            .catch(reason => { if (active) setSummaryError(reason instanceof Error ? reason.message : 'No se pudieron cargar las categorías'); });
        return () => { active = false; };
    }, [from, to, direction, context, revision]);

    const income = direction === 'INCOME';
    const titles: Record<FlowDirection, string> = {
        INCOME: 'Ingresos', EXPENSE: 'Gastos',
        SAVINGS: 'Ahorro e inversión', FINANCING: 'Financiación'
    };
    const title = titles[direction];
    const data = summary?.context === context ? summary.data : null;
    const visibleCategories = (data?.categories ?? []).filter(item => item.operations > 0);
    const categoryLabel = (code: string) => categoryNames[code] ?? catalog.find(item => item.code === code)?.label ?? code;
    const selectDirection = (value: FlowDirection) => {
        if (value !== direction) {
            setDirection(value);
            setExpanded(null);
        }
    };
    const toggleCategory = (value: string) => {setExpanded(category === value ? null : {context, category: value}); setMessage('');};
    const onChanged = (text: string) => {
        setMessage(text);
        setRevision(current => current + 1);
        onClassified();
    };

    return <section className="page financial-flow">
        <div className="finance-page-hero"><div><span className="products-eyebrow">ANÁLISIS FINANCIERO</span><h2>Ingresos y gastos</h2><p>Explora tus ingresos, consumo, ahorro y financiación por categorías y períodos.</p></div></div>
        <div className="flow-switch" role="group" aria-label="Tipo de análisis">
            <button type="button" aria-pressed={direction === 'EXPENSE'} onClick={() => selectDirection('EXPENSE')}>Gastos</button>
            <button type="button" aria-pressed={income} onClick={() => selectDirection('INCOME')}>Ingresos</button>
        </div>
        <p className="flow-note">{direction === 'INCOME'
            ? 'Abonos computables, incluidas las devoluciones y los reembolsos de inversión. Los cargos de financiación y ahorro e inversión se muestran en Gastos.'
            : direction === 'EXPENSE'
                ? 'Gastos de consumo, financiación y ahorro e inversión, separados por categorías. Las devoluciones positivas se muestran en ingresos.'
                : direction === 'SAVINGS'
                    ? 'Aportaciones netas a planes de pensiones, fondos y otras inversiones; no son gasto de consumo.'
                    : 'Cuotas y amortizaciones de deuda. Solo se distinguen capital e intereses cuando vienen desglosados.'}
            {' '}Los movimientos no computables y pendientes quedan excluidos.</p>
        {message && <div className="classification-message" role="status">{message}</div>}
        {catalogError && <div className="api-error" role="alert">{catalogError}</div>}
        {summaryError ? <div className="api-error" role="alert">{summaryError}</div> : !data ?
            <div className="loading" role="status">Cargando {title.toLowerCase()}…</div> : <>
            <div className="card flow-total">
                <span>{income ? 'Total de ingresos del período' : 'Total de gastos, financiación y ahorro del período'}</span>
                <strong>{eur(data.total)}</strong>
                <small>{data.operations} operaciones · {from} — {to}</small>
            </div>
            <div className="card flow-categories">
                <div className="title"><h2>Categorías</h2></div>
                <p className="flow-note">Pulsa una categoría para mostrar u ocultar sus movimientos.</p>
                {visibleCategories.length ? visibleCategories.map((item, index) => {
                    const open = category === item.category;
                    const regionId = `${accordionId}-category-${index}`;
                    return <section className="flow-category" key={item.category}>
                        <h3><button type="button" className="flow-category-toggle" id={`${regionId}-toggle`}
                            aria-expanded={open} aria-controls={regionId} onClick={() => toggleCategory(item.category)}>
                            <span><b>{categoryLabel(item.category)}</b><small>{item.operations} operaciones · {item.share.toFixed(1)} %</small></span>
                            <strong>{eur(item.amount)}</strong><ChevronDown size={20} aria-hidden="true"/>
                        </button></h3>
                        {open && <div className="flow-category-detail" id={regionId} role="region" aria-labelledby={`${regionId}-toggle`}>
                            <CategoryMovements key={JSON.stringify([context, item.category])} from={from} to={to} direction={direction}
                                category={item.category} products={products} catalog={catalog} canEdit={canEdit} onChanged={onChanged}/>
                        </div>}
                    </section>;
                }) : <div className="empty">No hay {title.toLowerCase()} en este período.</div>}
            </div>
        </>}
    </section>;
}

function CategoryMovements({from, to, direction, category, products, catalog, canEdit, onChanged}: {
    from: string; to: string; direction: FlowDirection; category: string; products: Product[];
    catalog: CategoryDefinition[]; canEdit: boolean; onChanged: (message: string) => void;
}) {
    const loadPage = useCallback(async (offset: number, limit: number) => {
        const data = await api.financialFlow(from, to, direction, offset, limit, category);
        return {items: data.movements, total: data.operations, offset: data.offset, limit: data.limit};
    }, [from, to, direction, category]);
    return <MovementResults products={products} catalog={catalog} canEdit={canEdit} loadPage={loadPage}
        queryKey={JSON.stringify([from, to, direction, category])} selectionScope="movimientos de esta categoría"
        selectionPageSize={100} onChanged={onChanged}/>;
}
