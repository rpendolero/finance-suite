import {useEffect, useState} from 'react';
import {api, CategoryDefinition, ClassificationResult, Movement} from './api';
import ClassificationFields, {treatmentDefaults, type ClassificationChoice} from './ClassificationFields';
export {TREATMENTS, treatmentDefaults} from './ClassificationFields';
export type {ClassificationChoice} from './ClassificationFields';

const initialChoice = (movement: Movement): ClassificationChoice => ({
    ...treatmentDefaults(movement.kind || 'NORMAL'),
    category: movement.category || '', subcategory: movement.subcategory || undefined
});
const eur = (amount: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(amount);
const dateLabel = (date: string) => new Intl.DateTimeFormat('es-ES', {day: '2-digit', month: 'short', year: 'numeric'}).format(new Date(date + 'T00:00:00'));

export default function ClassificationMovementRow({movement, catalog, canEdit, busy = false, productName, onSaved, onDelete, onSaving, onCancel, saveLabel = 'Solo este'}: {
    movement: Movement; catalog: CategoryDefinition[]; canEdit: boolean; busy?: boolean; productName?: string;
    onSaved: (result: ClassificationResult, appliedToMerchant: boolean) => void | Promise<void>;
    onDelete?: () => void; onSaving?: (saving: boolean) => void; onCancel?: () => void; saveLabel?: string;
}) {
    const [choice, setChoice] = useState<ClassificationChoice>(() => initialChoice(movement));
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    useEffect(() => { setChoice(initialChoice(movement)); setError(''); },
        [movement.id, movement.category, movement.subcategory, movement.kind]);
    const definition = catalog.find(item => item.code === choice.category);
    const disabled = busy || saving || !canEdit;
    const valid = !!definition && (choice.category !== 'NO_COMPUTABLE' || !!choice.subcategory);
    const canApplyToMerchant = valid && choice.category !== 'UNCLASSIFIED'
        && !!(movement.normalizedMerchant || movement.merchant || movement.description)?.trim();

    const save = async (appliedToMerchant: boolean) => {
        if (disabled || !valid || !choice.category) return;
        setSaving(true); onSaving?.(true); setError('');
        try {
            const result = await api.classifyMovement(movement.id, {
                category: choice.category, subcategory: choice.subcategory, kind: choice.kind || 'NORMAL',
                createRule: appliedToMerchant, applyToSimilar: appliedToMerchant
            });
            await onSaved(result, appliedToMerchant);
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : 'No se pudo guardar la clasificación');
        } finally { setSaving(false); onSaving?.(false); }
    };

    return <div className="classification-row">
        <div className="classification-movement">
            <b>{movement.normalizedMerchant || movement.merchant || movement.description}</b>
            <small>{dateLabel(movement.date)} · {eur(movement.amount)}</small>
            {productName && <small>{productName}</small>}
            <span>{movement.description}</span>
        </div>
        <ClassificationFields choice={choice} setChoice={setChoice} catalog={catalog} disabled={disabled} allowRefund={movement.amount > 0}/>
        {canEdit && <div className="classification-actions">
            <button type="button" disabled={disabled || !valid} onClick={() => void save(false)}>{saving ? "Guardando…" : saveLabel}</button>
            <button type="button" className="secondary" disabled={disabled || !canApplyToMerchant}
                title="Aplicar a todos los movimientos de este comercio, incluidos los manuales, y guardar una regla"
                onClick={() => void save(true)}>Aplicar al comercio</button>
            {onCancel && <button type="button" className="secondary" disabled={disabled} onClick={onCancel}>Cancelar</button>}
            {onDelete && <button type="button" className="secondary" disabled={disabled} onClick={onDelete}>Eliminar</button>}
        </div>}
        {error && <div className="api-error classification-row-message" role="alert">{error}</div>}
    </div>;
}
