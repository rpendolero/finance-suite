import {useCallback, useEffect, useRef, useState, type FormEvent} from 'react';
import {Search} from 'lucide-react';
import {api, type CategoryDefinition, type MovementFilters, type Product} from './api';
import MovementResults from './MovementResults';

export default function MovementSearch({products, initialFrom, initialTo, canEdit, onClassified, selection}: {
    products: Product[]; initialFrom: string; initialTo: string; canEdit: boolean; onClassified: () => void;
    selection?: {productId?: string; date?: string} | null;
}) {
    const [filters, setFilters] = useState<MovementFilters>(() => ({
        from: selection?.date || initialFrom, to: selection?.date || initialTo, productId: selection?.productId || '',
        sortBy: 'DATE', sortDirection: 'DESC'
    }));
    const [applied, setApplied] = useState(filters);
    const [revision, setRevision] = useState(0);
    const [catalog, setCatalog] = useState<CategoryDefinition[]>([]);
    const [catalogError, setCatalogError] = useState('');
    const [locked, setLocked] = useState(true), [message, setMessage] = useState('');
    const currentFilters = useRef(filters);
    currentFilters.current = filters;
    const dirty = JSON.stringify(filters) !== JSON.stringify(applied);
    const queryKey = JSON.stringify([applied, revision]);
    const loadPage = useCallback((offset: number, limit: number) => api.searchMovements({...applied, offset, limit}), [applied]);

    useEffect(() => {
        let active = true;
        api.categoriesCatalog().then(result => {if (active) setCatalog(result);})
            .catch(() => {if (active) setCatalogError('No se pudo cargar el catálogo de categorías. Recarga la pantalla para editar.');});
        return () => {active = false;};
    }, []);
    useEffect(() => {
        const next = {...currentFilters.current, from: selection?.date || initialFrom, to: selection?.date || initialTo,
            ...(selection ? {productId: selection.productId || ''} : {})};
        setFilters(next); setApplied(next); setMessage('');
    }, [initialFrom, initialTo, selection?.date, selection?.productId]);
    const change = <K extends keyof MovementFilters>(key: K, value: MovementFilters[K]) => {
        setFilters(current => ({...current, [key]: value})); setMessage('');
    };
    const submit = (event: FormEvent) => {
        event.preventDefault(); setApplied(filters); setRevision(current => current + 1); setMessage('');
    };

    return <section className="page movement-search finance-page">
        <div className="finance-page-hero"><div><span className="products-eyebrow">CONSULTA DE OPERACIONES</span><h2>Movimientos</h2><p>Busca, filtra y consulta los movimientos de tus cuentas y tarjetas.</p></div></div>
        <form className="filters card" onSubmit={submit}>
            <label>Desde<input type="date" required value={filters.from} onChange={e => change('from', e.target.value)}/></label>
            <label>Hasta<input type="date" required min={filters.from} value={filters.to} onChange={e => change('to', e.target.value)}/></label>
            <input aria-label="Buscar concepto o comercio" placeholder="Buscar concepto o comercio" value={filters.text || ''} onChange={e => change('text', e.target.value)}/>
            <select aria-label="Producto" value={filters.productId || ''} onChange={e => change('productId', e.target.value)}><option value="">Todos los productos</option>{products.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select>
            <select aria-label="Filtrar por categoría" value={filters.category || ''} onChange={e => change('category', e.target.value)}><option value="">Todas las categorías</option>{catalog.map(category => <option key={category.code} value={category.code}>{category.label}</option>)}</select>
            <input type="number" step="0.01" aria-label="Importe mínimo" placeholder="Importe mín." value={filters.minAmount ?? ''} onChange={e => change('minAmount', e.target.value === '' ? undefined : Number(e.target.value))}/>
            <input type="number" step="0.01" aria-label="Importe máximo" placeholder="Importe máx." value={filters.maxAmount ?? ''} onChange={e => change('maxAmount', e.target.value === '' ? undefined : Number(e.target.value))}/>
            <select aria-label="Estado" value={filters.status || ''} onChange={e => change('status', e.target.value)}><option value="">Todos los estados</option><option value="BOOKED">Contabilizado</option><option value="PENDING">Pendiente</option></select>
            <select aria-label="Ordenar por" value={filters.sortBy} onChange={e => change('sortBy', e.target.value as MovementFilters['sortBy'])}><option value="DATE">Ordenar por fecha</option><option value="AMOUNT">Ordenar por importe</option><option value="MERCHANT">Ordenar por comercio</option><option value="CATEGORY">Ordenar por categoría</option></select>
            <select aria-label="Dirección de ordenación" value={filters.sortDirection} onChange={e => change('sortDirection', e.target.value as MovementFilters['sortDirection'])}><option value="DESC">Descendente</option><option value="ASC">Ascendente</option></select>
            <button type="submit" disabled={locked}><Search size={16}/>Buscar</button>
            <button type="button" className="secondary" disabled={locked || dirty} onClick={() => api.exportMovements(applied)}>Exportar CSV</button>
        </form>
        {dirty && <p className="movement-search-note" role="status">Filtros modificados. Pulsa Buscar para actualizar los resultados.</p>}
        {message && <div className="classification-message" role="status">{message}</div>}
        {catalogError && <div className="api-error" role="alert">{catalogError}</div>}
        <MovementResults products={products} catalog={catalog} canEdit={canEdit} loadPage={loadPage}
            queryKey={queryKey} selectionKey={JSON.stringify([queryKey, filters])} dirty={dirty} onLockedChange={setLocked}
            onChanged={text => {setMessage(text); onClassified();}}/>
    </section>;
}
