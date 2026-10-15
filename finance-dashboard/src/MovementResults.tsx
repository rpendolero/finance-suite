import {useCallback, useEffect, useRef, useState} from 'react';
import {Pencil} from 'lucide-react';
import {api, type CategoryDefinition, type ClassificationResult, type Movement, type MovementPage, type Product} from './api';
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
const eur = (amount: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(amount);
const dateLabel = (date: string) => new Date(date + 'T00:00:00').toLocaleDateString('es-ES');

export default function MovementResults({products, catalog, canEdit, loadPage, queryKey, selectionKey = queryKey,
    dirty = false, selectionScope = 'resultados de la búsqueda', selectionPageSize = 200, onChanged, onLockedChange}: {
    products: Product[]; catalog: CategoryDefinition[]; canEdit: boolean;
    loadPage: (offset: number, limit: number) => Promise<MovementPage>;
    queryKey: string; selectionKey?: string; dirty?: boolean; selectionScope?: string; selectionPageSize?: number;
    onChanged: (message: string) => void; onLockedChange?: (locked: boolean) => void;
}) {
    const [pageSize, setPageSize] = useState(savedPageSize);
    const sizeRef = useRef(pageSize);
    const [page, setPage] = useState<MovementPage>({items: [], total: 0, offset: 0, limit: pageSize});
    const [editing, setEditing] = useState<Movement | null>(null);
    const [deleting, setDeleting] = useState<Movement | null>(null);
    const [selected, setSelected] = useState<Map<string, Movement>>(new Map());
    const [bulk, setBulk] = useState<Movement[] | null>(null);
    const [busy, setBusy] = useState(true), [selecting, setSelecting] = useState(false);
    const [saving, setSaving] = useState(false), [error, setError] = useState('');
    const request = useRef(0), selectionRequest = useRef(0);
    const pageCheckbox = useRef<HTMLInputElement>(null);
    const locked = busy || selecting || saving;
    const canSelect = canEdit && !locked && !dirty && !error;
    const pageSelected = page.items.filter(movement => selected.has(movement.id)).length;
    const allPageSelected = page.items.length > 0 && pageSelected === page.items.length;

    useEffect(() => {onLockedChange?.(locked);}, [locked, onLockedChange]);
    useEffect(() => {
        if (pageCheckbox.current) pageCheckbox.current.indeterminate = pageSelected > 0 && !allPageSelected;
    }, [pageSelected, allPageSelected]);

    const clearSelection = () => {
        selectionRequest.current++; setSelecting(false); setSelected(new Map());
    };
    const load = useCallback(async (offset: number, limit: number) => {
        const version = ++request.current;
        setBusy(true); setError('');
        try {
            let result = await loadPage(offset, limit);
            if (version !== request.current) return;
            // Recategorizing or deleting can empty the last page of this query.
            if (result.offset >= result.total && result.offset > 0) {
                const lastOffset = result.total ? Math.floor((result.total - 1) / result.limit) * result.limit : 0;
                result = await loadPage(lastOffset, limit);
            }
            if (version === request.current) setPage(result);
        } catch (cause) {
            if (version === request.current) setError(cause instanceof Error ? cause.message : 'No se pudieron cargar los movimientos');
        } finally {if (version === request.current) setBusy(false);}
    }, [loadPage]);
    useEffect(() => {
        void load(0, sizeRef.current);
        return () => {request.current++; selectionRequest.current++;};
    }, [load, queryKey]);
    useEffect(() => {
        clearSelection(); setEditing(null); setDeleting(null); setBulk(null);
    }, [selectionKey]);

    const changeSize = (limit: number) => {
        if (!pageSizes.includes(limit)) return;
        try {localStorage.setItem(PAGE_SIZE_KEY, String(limit));} catch {/* Storage may be unavailable. */}
        sizeRef.current = limit; setPageSize(limit); void load(0, limit);
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
            for (let offset = 0; ; offset += selectionPageSize) {
                const result = await loadPage(offset, selectionPageSize);
                if (version !== selectionRequest.current) return;
                if (result.total !== page.total) throw new Error('Los resultados han cambiado. Actualiza la lista y vuelve a seleccionar.');
                result.items.forEach(movement => matches.set(movement.id, movement));
                if (!result.items.length || offset + result.items.length >= result.total) break;
            }
            if (matches.size !== page.total) throw new Error('Los resultados han cambiado. Actualiza la lista y vuelve a seleccionar.');
            setSelected(matches);
        } catch (cause) {
            if (version === selectionRequest.current) setError(cause instanceof Error ? cause.message : 'No se pudo completar la selección');
        } finally {if (version === selectionRequest.current) setSelecting(false);}
    };
    const saved = (result: ClassificationResult, merchant: boolean) => {
        setEditing(null); clearSelection();
        onChanged(merchant ? `Clasificación guardada. Regla aplicada a ${result.reclassified} movimientos de este comercio en el histórico.` : 'Clasificación manual guardada.');
        void load(page.offset, page.limit);
    };
    const savedBulk = (updated: number) => {
        setBulk(null); clearSelection(); onChanged(`${updated} movimientos recategorizados.`);
        void load(page.offset, page.limit);
    };
    const deleteMovement = async () => {
        if (!deleting || saving) return;
        setSaving(true); setError('');
        try {
            await api.deleteMovement(deleting.id);
            setDeleting(null); clearSelection(); onChanged('Movimiento eliminado.');
            void load(page.offset, page.limit);
        } catch (cause) {setError(cause instanceof Error ? cause.message : 'No se pudo eliminar el movimiento');}
        finally {setSaving(false);}
    };
    const open = (movement: Movement) => {if (canEdit && !locked && !dirty && !error && catalog.length) setEditing(movement);};

    return <>
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
        {error && !deleting && <div className="api-error" role="alert">{error}
            <button type="button" className="secondary movement-refresh" disabled={locked || dirty}
                onClick={() => {clearSelection(); void load(page.offset, page.limit);}}>Actualizar lista</button>
        </div>}
        <div className="card movement-results" aria-busy={busy || selecting}>
            <div className="movement-list-toolbar">
                <span role="status">{busy ? 'Buscando movimientos…' : `${page.total} movimientos`}</span>
                <label>Movimientos por página <select value={pageSize} disabled={locked || dirty} onChange={e => changeSize(Number(e.target.value))}>
                    {pageSizes.map(size => <option key={size} value={size}>{size}</option>)}
                </select></label>
            </div>
            {canEdit && page.items.length > 0 && <div className="movement-selection-toolbar">
                <label><input ref={pageCheckbox} type="checkbox" checked={allPageSelected} disabled={!canSelect} onChange={togglePage}/>Seleccionar esta página</label>
                <span role="status">{selected.size} seleccionados</span>
                {selected.size > 0 && <>
                    {page.total > selected.size && <button type="button" className="secondary" disabled={!canSelect} onClick={() => void selectAll()}>Seleccionar los {page.total} {selectionScope}</button>}
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
                <div><button type="button" disabled={locked || dirty || page.offset <= 0} onClick={() => void load(Math.max(0, page.offset - page.limit), page.limit)}>Anterior</button>
                    <button type="button" disabled={locked || dirty || page.offset + page.limit >= page.total} onClick={() => void load(page.offset + page.limit, page.limit)}>Siguiente</button></div>
            </div>
        </div>
    </>;
}
