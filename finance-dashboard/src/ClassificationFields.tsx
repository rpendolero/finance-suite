import {useId} from 'react';
import {type CategoryDefinition} from './api';

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

export default function ClassificationFields({choice, setChoice, catalog, disabled, allowRefund}: {
    choice: ClassificationChoice; setChoice: (choice: ClassificationChoice) => void;
    catalog: CategoryDefinition[]; disabled: boolean; allowRefund: boolean;
}) {
    const fieldId = useId();
    const definition = catalog.find(item => item.code === choice.category);
    const treatment = TREATMENTS.find(item => item.code === choice.kind);
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
    return <>
        <div className="classification-field">
            <label htmlFor={`${fieldId}-kind`}>Tratamiento</label>
            <select id={`${fieldId}-kind`} value={choice.kind || 'NORMAL'} disabled={disabled}
                onChange={event => chooseTreatment(event.target.value)}>
                {TREATMENTS.map(item => <option key={item.code} value={item.code}
                    disabled={item.code === 'REFUND' && !allowRefund}>{item.label}</option>)}
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
                {(definition?.subcategories || []).map(value => <option value={value} key={value}>{definition?.subcategoryLabels?.[value] || value.replaceAll('_', ' ')}</option>)}
            </select>
        </div>
    </>;
}
