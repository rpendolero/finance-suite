import type {Movement} from './api';

// Same inclusion rules as Movement.included() in the server domain.
export function isComputableMovement(movement: Movement): boolean {
    return movement.status === 'BOOKED'
        && (movement.kind === 'NORMAL' || movement.kind === 'REFUND')
        && movement.category?.toUpperCase() !== 'NO_COMPUTABLE';
}

export function movementTotals(movements: Movement[]) {
    let incomeCents = 0, expenseCents = 0;
    for (const movement of movements) {
        if (!isComputableMovement(movement)) continue;
        const cents = Math.round(Number(movement.amount) * 100);
        if (cents > 0) incomeCents += cents;
        else expenseCents -= cents;
    }
    return {income: incomeCents / 100, expenses: expenseCents / 100, net: (incomeCents - expenseCents) / 100};
}
