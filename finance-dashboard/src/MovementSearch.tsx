import {useEffect, useRef, useState, type FormEvent} from 'react';
import {Pencil, Search} from 'lucide-react';
import {api, type CategoryDefinition, type ClassificationResult, type Movement, type MovementFilters, type MovementPage, type Product} from './api';
import ClassificationMovementRow from './ClassificationMovementRow';
import {TREATMENTS} from './ClassificationFields';
import MovementDialog from './MovementDialog';
import BulkClassificationModal from './BulkClassificationModal';

const PAGE_SIZE_KEY = 'finance.movements.pageSize';
const pageSizes = [25, 50, 100];
const savedPageSize = () => {
    try {const size = Number(localStorage.getItem(PAGE_SIZE_KEY)); return pageSizes.includes(size) ? size : 25;}
    catch {return 25;}
};
const filterKey = ({offset: _offset, limit: _limit, ...filters}: MovementFilters) => JSON.stringify(filters);
const eur = (amount: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(amount);
const dateLabel = (date: string) => new Date(date + 'T00:00:00').toLocaleDateString('es-ES');

export default function MovementSearch({products, initialFrom, initialTo, canEdit, onClassified, selection}: {
    products: Product[]; initialFrom: string; initialTo: string; canEdit: boolean; onClassified: () => void;
    selection?: {productId?: string; date?: string} | null;
}) {
    const [filters, setFilters] = useState<MovementFilters>(() => ({
        from: selection?.date || initialFrom, to: selection?.date || initialTo, productId: selection?.productId || '',
        sortBy: 'DATE', sortDirection: 'DESC', offset: 0, limit: savedPageSize()
    }));
    const [applied, setApplied] = useState(filters);
    const [page, setPage] = useState<MovementPage>({items: [], total: 0, offset: 0, limit: filters.limit!});
    const [catalog, setCatalog] = useState<CategoryDefinition[]>([]);
    const [catalogError, setCatalogError] = useState('');
    const [editing, setEditing] = useState<Movement | null>(null);
    const [deleting, setDeleting] = useState<Movement | null>(null);
    const [selected, setSelected] = useState<Map<string, Movement>>(new Map());
    const [bulk, setBulk] = useState<Movement[] | null>(null);
    const [busy, setBusy] = useState(false), [selecting, setSelecting] = useState(false);
    const [saving, setSaving] = useState(false), [error, setError] = useState(''), [message, setMessage] = useState('');
    const request = useRef(0), selectionRequest = useRef(0);
    const currentFilters = useRef(filters);
    currentFilters.current = filters;
    const pageCheckbox = useRef<HTMLInputElement>(null);
    const dirty = filterKey(filters) !== filterKey(applied);
    const locked = busy || selecting || saving;
    const canSelect = canEdit && !locked && !dirty && !error;
    const pageSelected = page.items.filter(movement => selected.has(movement.id)).length;
    const allPageSelected = page.items.length > 0 && pageSelected === page.items.length;

    useEffect(() => {
        if (pageCheckbox.current) pageCheckbox.current.indeterminate = pageSelected > 0 && !allPageSelected;
    }, [pageSelected, allPageSelected]);

    const clearSelection = () => {
        selectionRequest.current++; setSelecting(false); setSelected(new Map());
    };
    const load = async (next: MovementFilters) => {
        const version = ++request.current;
        setBusy(true); setError('');
        try {
            let result = await api.searchMovements(next);
            if (version !== request.current) return;
            // A saved category can remove the last row from the last filtered page.
            if (result.offset >= result.total && result.offset > 0) {
                next = {...next, offset: result.total ? Math.floor((result.total - 1) / result.limit) * result.limit : 0};
                result = await api.searchMovements(next);
            }
            if (version !== request.current) return;
            setPage(result); setApplied({...next, offset: result.offset, limit: result.limit});
        } catch (cause) {
            if (version === request.current) setError(cause instanceof Error ? cause.message : 'Error de búsqueda');
        } finally {if (version === request.current) setBusy(false);}
    };
    useEffect(() => {
        let active = true;
        api.categoriesCatalog().then(result => {if (active) setCatalog(result);})
            .catch(() => {if (active) setCatalogError('No se pudo cargar el catálogo de categorías. Recarga la pantalla para editar.');});
        return () => {active = false; request.current++; selectionRequest.current++;};
    }, []);
    useEffect(() => {
        const next = {...currentFilters.current, from: selection?.date || initialFrom, to: selection?.date || initialTo,
            ...(selection ? {productId: selection.productId || ''} : {}), offset: 0};
        setFilters(next); clearSelection(); setMessage(''); void load(next);
    }, [initialFrom, initialTo, selection?.date, selection?.productId]);

    const change = <K extends keyof MovementFilters>(key: K, value: MovementFilters[K]) => {
        setFilters(current => ({...current, [key]: value, offset: 0})); clearSelection(); setMessage('');
    };
    const submit = (event: FormEvent) => {
        event.preventDefault(); clearSelection(); setMessage(''); void load({...filters, offset: 0});
    };
    const changeSize = (limit: number) => {
        if (!pageSizes.includes(limit)) return;
        try {localStorage.setItem(PAGE_SIZE_KEY, String(limit));} catch {/* Storage may be unavailable. */}
        setFilters(current => ({...current, limit, offset: 0}));
        void load({...applied, limit, offset: 0});
    };
    const toggle = (movement: Movement) => setSelected(current => {
        const next = new Map(current);
        if (next.has(movement.id)) next.delete(movement.id); else next.set(movement.id, movement);
        return next;
    });
    const togglePage = () => setSelected(current => {
        const next = new Map(current);
        page.items.forEach(movement => {if (allPageSelected) next.delete(movement.id); else next.set(movement.id, movement);});
        return next;
    });
    const selectAll = async () => {
        const version = ++selectionRequest.current;
        setSelecting(true); setError('');
        try {
            const matches = new Map<string, Movement>();
            for (let offset = 0; ; offset += 200) {
                const result = await api.searchMovements({...applied, offset, limit: 200});
                if (version !== selectionRequest.current) return;
                if (result.total !== page.total) throw new Error('Los resultados han cambiado. Pulsa Buscar y vuelve a seleccionar.');
                result.items.forEach(movement => matches.set(movement.id, movement));
                if (!result.items.length || offset + result.items.length >= result.total) break;
            }
            if (matches.size !== page.total) throw new Error('Los resultados han cambiado. Pulsa Buscar y vuelve a seleccionar.');
            setSelected(matches);
        } catch (cause) {
            if (version === selectionRequest.current) setError(cause instanceof Error ? cause.message : 'No se pudo seleccionar la búsqueda');
        } finally {if (version === selectionRequest.current) setSelecting(false);}
    };
    const saved = (result: ClassificationResult, merchant: boolean) => {
        setEditing(null); clearSelection();
        setMessage(merchant ? `Clasificación guardada. Regla aplicada a ${result.reclassified} movimientos de este comercio en el histórico.` : 'Clasificación manual guardada.');
        onClassified(); void load(applied);
    };
    const savedBulk = (updated: number) => {
        setBulk(null); clearSelection(); setMessage(`${updated} movimientos recategorizados.`);
        onClassified(); void load(applied);
    };
    const deleteMovement = async () => {
        if (!deleting || saving) return;
        setSaving(true); setError('');
        try {
            await api.deleteMovement(deleting.id);
            setDeleting(null); clearSelection(); setMessage('Movimiento eliminado.');
            onClassified(); void load(applied);
        } catch (cause) {setError(cause instanceof Error ? cause.message : 'No se pudo eliminar el movimiento');}
        finally {setSaving(false);}
    };
    const open = (movement: Movement) => {if (canEdit && !locked && !dirty && !error && catalog.length) setEditing(movement);};

    return <section className="page movement-search">
        <div className="section-head"><h2>Movimientos</h2></div>
        {editing && <MovementDialog title="Editar movimiento" busy={saving} onClose={() => setEditing(null)}>
            <ClassificationMovementRow movement={editing} catalog={catalog} canEdit={canEdit} onSaving={setSaving}
                productName={products.find(product => product.id === editing.productId)?.name} onSaved={saved}
                saveLabel="Guardar" onCancel={() => setEditing(null)} onDelete={() => {setEditing(null); setDeleting(editing);}}/>
            <p className="movement-edit-note">«Aplicar al comercio» también actualiza su histórico y guarda una regla para futuras importaciones.</p>
        </MovementDialog>}
        {bulk && <BulkClassificationModal movements={bulk} catalog={catalog} onClose={() => setBulk(null)} onSaved={savedBulk}/>}
        {deleting && <MovementDialog title="Eliminar movimiento" busy={saving} onClose={() => setDeleting(null)}>
            <p>{dateLabel(deleting.date)} · {deleting.merchant || deleting.description} · {eur(deleting.amount)}</p>
            <p>Se eliminará definitivamente. Una nueva importación o sincronización puede volver a incorporarlo.</p>
            {error && <div className="api-error" role="alert">{error}</div>}
            <div className="app-modal-actions">
                <button type="button" className="secondary" disabled={saving} onClick={() => setDeleting(null)}>Cancelar</button>
                <button type="button" disabled={saving} onClick={() => void deleteMovement()}>Eliminar definitivamente</button>
            </div>
        </MovementDialog>}
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
        {error && <div className="api-error" role="alert">{error}</div>}
        {catalogError && <div className="api-error" role="alert">{catalogError}</div>}
        <div className="card movement-results" aria-busy={busy || selecting}>
            <div className="movement-list-toolbar">
                <span role="status">{busy ? 'Buscando movimientos…' : `${page.total} movimientos`}</span>
                <label>Movimientos por página <select value={filters.limit} disabled={locked || dirty} onChange={e => changeSize(Number(e.target.value))}>
                    {pageSizes.map(size => <option key={size} value={size}>{size}</option>)}
                </select></label>
            </div>
            {canEdit && page.items.length > 0 && <div className="movement-selection-toolbar">
                <label><input ref={pageCheckbox} type="checkbox" checked={allPageSelected} disabled={!canSelect} onChange={togglePage}/>Seleccionar esta página</label>
                <span role="status">{selected.size} seleccionados</span>
                {selected.size > 0 && <>
                    {page.total > selected.size && <button type="button" className="secondary" disabled={!canSelect} onClick={() => void selectAll()}>Seleccionar los {page.total} resultados de la búsqueda</button>}
                    <button type="button" disabled={!canSelect || !catalog.length} onClick={() => setBulk([...selected.values()])}>Recategorizar seleccionados ({selected.size})</button>
                    <button type="button" className="secondary" disabled={saving} onClick={clearSelection}>Limpiar selección</button>
                </>}
                {selecting && <span role="status">Seleccionando todos los resultados…</span>}
            </div>}
            <div className="movement-result-list">
                {page.items.map(movement => {
                    const category = catalog.find(item => item.code === movement.category);
                    const treatment = TREATMENTS.find(item => item.code === movement.kind);
                    const editable = canSelect && catalog.length > 0;
                    return <div key={movement.id} className={`movement-result-row${canEdit ? ' is-editable' : ''}${selected.has(movement.id) ? ' is-selected' : ''}`}
                        onClick={() => open(movement)}>
                        {canEdit && <input type="checkbox" aria-label={`Seleccionar ${movement.merchant || movement.description}, ${dateLabel(movement.date)}, ${eur(movement.amount)}`}
                            checked={selected.has(movement.id)} disabled={!canSelect} onClick={e => e.stopPropagation()} onChange={() => toggle(movement)}/>}
                        <div className="movement-result-description"><strong>{movement.normalizedMerchant || movement.merchant || movement.description}</strong>
                            <small>{dateLabel(movement.date)} · {products.find(product => product.id === movement.productId)?.name || movement.productId}</small>
                            <span>{movement.description}</span></div>
                        <div className="movement-result-category"><span>{category?.label || movement.category || 'No categorizado'}</span>
                            {movement.subcategory && <small>{category?.subcategoryLabels?.[movement.subcategory] || movement.subcategory.replaceAll('_', ' ')}</small>}
                            {treatment && movement.kind !== 'NORMAL' && <small className={!treatment.computable ? 'non-computable' : ''}>{treatment.label}</small>}</div>
                        <strong className={movement.amount >= 0 ? 'pos' : 'neg'}>{eur(movement.amount)}</strong>
                        {canEdit && <button type="button" className="secondary movement-edit" disabled={!editable} aria-label={`Editar ${movement.merchant || movement.description}`}
                            onClick={e => {e.stopPropagation(); open(movement);}}><Pencil size={15}/>Editar</button>}
                    </div>;
                })}
                {!busy && !page.items.length && <p className="empty">No hay movimientos con esos filtros.</p>}
            </div>
            <div className="pagination">
                <span>{page.total ? `${page.offset + 1}–${Math.min(page.offset + page.items.length, page.total)} de ${page.total}` : '0 movimientos'} · Página {page.total ? Math.floor(page.offset / page.limit) + 1 : 1} de {Math.max(1, Math.ceil(page.total / page.limit))}</span>
                <div><button disabled={locked || dirty || page.offset <= 0} onClick={() => void load({...applied, offset: Math.max(0, page.offset - page.limit)})}>Anterior</button>
                    <button disabled={locked || dirty || page.offset + page.limit >= page.total} onClick={() => void load({...applied, offset: page.offset + page.limit})}>Siguiente</button></div>
            </div>
        </div>
    </section>;
}
