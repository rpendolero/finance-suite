import {useEffect, useId, useState} from 'react';
import {ChevronDown} from 'lucide-react';
import ClassificationMovementRow from './ClassificationMovementRow';
import {api, CategoryDefinition, ClassificationResult, FinancialFlow, FlowDirection, Product} from './api';

const eur = (amount: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(amount);
const PAGE_SIZE = 25;
type Expansion = {context: string; category: string; offset: number};

export default function FinancialFlowView({from, to, products, canEdit, onClassified}: {
    from: string; to: string; products: Product[]; canEdit: boolean; onClassified: () => void;
}) {
    const [direction, setDirection] = useState<FlowDirection>('EXPENSE');
    const context = `${from}:${to}:${direction}`;
    const [expanded, setExpanded] = useState<Expansion | null>(null);
    const category = expanded?.context === context ? expanded.category : undefined;
    const offset = expanded?.context === context ? expanded.offset : 0;
    const [summary, setSummary] = useState<{context: string; data: FinancialFlow} | null>(null);
    const [summaryError, setSummaryError] = useState('');
    const [revision, setRevision] = useState(0);
    const [catalog, setCatalog] = useState<CategoryDefinition[]>([]);
    const [catalogError, setCatalogError] = useState('');
    const [message, setMessage] = useState('');
    const detailKey = JSON.stringify([context, category, offset, revision]);
    const [detail, setDetail] = useState<{key: string; data?: FinancialFlow; error?: string} | null>(null);
    const accordionId = useId();

    useEffect(() => { setExpanded(null); setMessage(''); }, [context]);

    useEffect(() => {
        let active = true;
        api.categoriesCatalog().then(result => { if (active) setCatalog(result); })
            .catch(reason => { if (active) setCatalogError(reason instanceof Error ? reason.message : 'No se pudo cargar el catálogo'); });
        return () => { active = false; };
    }, []);

    useEffect(() => {
        let active = true;
        setSummaryError('');
        setSummary(null);
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
    const onSaved = (result: ClassificationResult, appliedToMerchant: boolean) => {
        setMessage(appliedToMerchant
            ? `Clasificación guardada. Regla del comercio aplicada a ${result.reclassified} movimientos de este comercio en el histórico.`
            : 'Clasificación manual guardada.');
        setExpanded(current => current ? {...current, offset: 0} : current);
        setRevision(current => current + 1);
        onClassified();
    };

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
        {message && <div className="classification-message" role="status">{message}</div>}
        {catalogError && <div className="api-error" role="alert">{catalogError}</div>}
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
                                <CategoryMovements data={selectedDetail.data} catalog={catalog} canEdit={canEdit} onSaved={onSaved} productNames={productNames}
                                    offset={offset} onPage={value => setExpanded({context, category: item.category, offset: value})}/>}
                        </div>}
                    </section>;
                }) : <div className="empty">No hay {title.toLowerCase()} en este período.</div>}
            </div>
        </>}
    </section>;
}

function CategoryMovements({data, catalog, canEdit, onSaved, productNames, offset, onPage}: {
    data: FinancialFlow; catalog: CategoryDefinition[]; canEdit: boolean;
    onSaved: (result: ClassificationResult, appliedToMerchant: boolean) => void;
    productNames: Map<string, string>; offset: number; onPage: (offset: number) => void;
}) {
    const [saving, setSaving] = useState(false);
    return <>
        {data.movements.length ? <div className="classification-list">{data.movements.map(movement =>
            <ClassificationMovementRow key={movement.id} movement={movement} catalog={catalog} canEdit={canEdit} busy={saving}
                productName={productNames.get(movement.productId)} onSaved={onSaved} onSaving={setSaving}/>)}</div>
            : <div className="empty">No hay movimientos en esta categoría.</div>}
        {data.operations > 0 && <div className="pagination">
            <span>{offset + 1}–{Math.min(offset + PAGE_SIZE, data.operations)} de {data.operations}</span>
            <div className="flow-page-actions">
                <button type="button" disabled={saving || offset === 0} onClick={() => onPage(Math.max(0, offset - PAGE_SIZE))}>Anterior</button>
                <button type="button" disabled={saving || offset + PAGE_SIZE >= data.operations} onClick={() => onPage(offset + PAGE_SIZE)}>Siguiente</button>
            </div>
        </div>}
    </>;
}
