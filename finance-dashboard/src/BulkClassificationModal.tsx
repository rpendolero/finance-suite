import {useState, type FormEvent} from 'react';
import {api, type CategoryDefinition, type Movement} from './api';
import ClassificationFields, {type ClassificationChoice} from './ClassificationFields';
import MovementDialog from './MovementDialog';

export default function BulkClassificationModal({movements, catalog, onClose, onSaved}: {
    movements: Movement[]; catalog: CategoryDefinition[]; onClose: () => void; onSaved: (updated: number) => void;
}) {
    const [choice, setChoice] = useState<ClassificationChoice>({kind: 'NORMAL'});
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const valid = catalog.some(item => item.code === choice.category)
        && (choice.category !== 'NO_COMPUTABLE' || !!choice.subcategory);
    const save = async (event: FormEvent) => {
        event.preventDefault();
        if (saving || !valid || !choice.category || !movements.length) return;
        setSaving(true); setError('');
        try {
            const result = await api.classifyMovements(movements.map(movement => movement.id), {
                category: choice.category, subcategory: choice.subcategory, kind: choice.kind || 'NORMAL'
            });
            onSaved(result.updated);
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : 'No se pudo guardar la selección');
        } finally {setSaving(false);}
    };
    return <MovementDialog title="Recategorizar seleccionados" busy={saving} onClose={onClose}>
        <p>Se modificarán <strong>{movements.length} movimientos seleccionados</strong>.</p>
        <form onSubmit={save} className="bulk-classification-form">
            <ClassificationFields choice={choice} setChoice={setChoice} catalog={catalog} disabled={saving}
                allowRefund={movements.every(movement => movement.amount > 0)}/>
            <p className="movement-edit-note">La categoría, subcategoría y tratamiento elegidos se aplicarán a toda la selección. No se crearán reglas por comercio.</p>
            {choice.kind === 'NORMAL' && <p className="movement-edit-note">Las devoluciones existentes conservarán su tratamiento de devolución.</p>}
            {error && <div className="api-error" role="alert">{error}</div>}
            <div className="app-modal-actions">
                <button type="button" className="secondary" disabled={saving} onClick={onClose}>Cancelar</button>
                <button type="submit" disabled={saving || !valid}>{saving ? 'Guardando…' : `Aplicar a ${movements.length} movimientos`}</button>
            </div>
        </form>
    </MovementDialog>;
}
