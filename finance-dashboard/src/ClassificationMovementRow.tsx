import {useEffect, useId, useState} from 'react';
import {api, CategoryDefinition, ClassificationResult, Movement} from './api';

export const TREATMENTS = [
    {code: 'NON_COMPUTABLE', label: 'No computable - Otros', computable: false},
    {code: 'NORMAL', label: 'Gasto / ingreso normal', computable: true},
    {code: 'REFUND', label: 'Devolución', computable: true},
    {code: 'CARD_SETTLEMENT', label: 'No computable - Liquidación tarjeta', computable: false},
    {code: 'WALLET_SETTLEMENT', label: 'No computable - PayPal / monedero', computable: false},
    {code: 'DUPLICATE', label: 'No computable - Movimiento duplicado', computable: false},
    {code: 'INTERNAL_TRANSFER', label: 'No computable - Transferencia interna', computable: false}
] as const;

export type ClassificationChoice = {category?: string; subcategory?: string; kind?: string};
const excludedKinds: Record<string, string> = {
    LIQUIDACION_TARJETA: 'CARD_SETTLEMENT', LIQUIDACION_PAYPAL: 'WALLET_SETTLEMENT',
    TRASPASO_INTERNO: 'INTERNAL_TRANSFER', MOVIMIENTO_DUPLICADO: 'DUPLICATE', OTROS_NO_COMPUTABLES: 'NON_COMPUTABLE'
};

export function treatmentDefaults(kind: string): ClassificationChoice {
    const subcategory = Object.entries(excludedKinds).find(([, value]) => value === kind)?.[0];
    return subcategory ? {kind, category: 'NO_COMPUTABLE', subcategory} : {kind};
}

const initialChoice = (movement: Movement): ClassificationChoice => ({
    ...treatmentDefaults(movement.kind || 'NORMAL'),
    category: movement.category || '', subcategory: movement.subcategory || undefined
});
const eur = (amount: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(amount);
const dateLabel = (date: string) => new Intl.DateTimeFormat('es-ES', {day: '2-digit', month: 'short', year: 'numeric'}).format(new Date(date + 'T00:00:00'));

export default function ClassificationMovementRow({movement, catalog, canEdit, busy = false, productName, onSaved, onDelete, onSaving}: {
    movement: Movement; catalog: CategoryDefinition[]; canEdit: boolean; busy?: boolean; productName?: string;
    onSaved: (result: ClassificationResult, appliedToMerchant: boolean) => void | Promise<void>;
    onDelete?: () => void; onSaving?: (saving: boolean) => void;
}) {
    const [choice, setChoice] = useState<ClassificationChoice>(() => initialChoice(movement));
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const fieldId = useId();
    useEffect(() => { setChoice(initialChoice(movement)); setError(''); },
        [movement.id, movement.category, movement.subcategory, movement.kind]);
    const definition = catalog.find(item => item.code === choice.category);
    const treatment = TREATMENTS.find(item => item.code === choice.kind);
    const disabled = busy || saving || !canEdit;
    const valid = !!definition && (choice.category !== 'NO_COMPUTABLE' || !!choice.subcategory);
    const canApplyToMerchant = valid && choice.category !== 'UNCLASSIFIED'
        && !!(movement.normalizedMerchant || movement.merchant || movement.description)?.trim();

    const chooseCategory = (category: string) => setChoice({
        category, subcategory: undefined,
        kind: category === 'NO_COMPUTABLE' ? 'NON_COMPUTABLE' : choice.kind === 'REFUND' ? 'REFUND' : 'NORMAL'
    });
    const chooseTreatment = (kind: string) => setChoice({
        ...treatmentDefaults(kind),
        ...(kind === 'NORMAL' || kind === 'REFUND' ? {
            category: choice.category === 'NO_COMPUTABLE' ? '' : choice.category,
            subcategory: choice.category === 'NO_COMPUTABLE' ? undefined : choice.subcategory
        } : {})
    });
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
        <div className="classification-field">
            <label htmlFor={`${fieldId}-kind`}>Tratamiento</label>
            <select id={`${fieldId}-kind`} value={choice.kind || 'NORMAL'} disabled={disabled}
                onChange={event => chooseTreatment(event.target.value)}>
                {TREATMENTS.map(item => <option key={item.code} value={item.code}
                    disabled={item.code === 'REFUND' && movement.amount <= 0}>{item.label}</option>)}
            </select>
            {treatment && !treatment.computable && <small className="non-computable">No se incluirá en gastos, ingresos ni ahorro.</small>}
        </div>
        <div className="classification-field">
            <label htmlFor={`${fieldId}-category`}>Categoría</label>
            <select id={`${fieldId}-category`} value={choice.category || ''} disabled={disabled}
                onChange={event => chooseCategory(event.target.value)}>
                <option value="">Selecciona categoría</option>
                {choice.category && !definition && <option value={choice.category}>{choice.category}</option>}
                {catalog.map(item => <option value={item.code} key={item.code}>{item.label}</option>)}
            </select>
        </div>
        <div className="classification-field">
            <label htmlFor={`${fieldId}-subcategory`}>Subcategoría</label>
            <select id={`${fieldId}-subcategory`} value={choice.subcategory || ''} disabled={disabled || !definition}
                onChange={event => setChoice({...choice, subcategory: event.target.value || undefined,
                    kind: excludedKinds[event.target.value] || choice.kind})}>
                <option value="">Sin subcategoría</option>
                {(definition?.subcategories || []).map(value => <option value={value} key={value}>{value.replaceAll('_', ' ')}</option>)}
            </select>
        </div>
        {canEdit && <div className="classification-actions">
            <button type="button" disabled={disabled || !valid} onClick={() => void save(false)}>Solo este</button>
            <button type="button" className="secondary" disabled={disabled || !canApplyToMerchant}
                title="Aplicar a todos los movimientos de este comercio, incluidos los manuales, y guardar una regla"
                onClick={() => void save(true)}>Aplicar al comercio</button>
            {onDelete && <button type="button" className="secondary" disabled={disabled} onClick={onDelete}>Eliminar</button>}
        </div>}
        {error && <div className="api-error classification-row-message" role="alert">{error}</div>}
    </div>;
}
