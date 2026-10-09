export const MONTHS = [
    'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
    'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'
];

export const isMonthPeriod = (preset: string) => /^MONTH_(0[1-9]|1[0-2])$/.test(preset);

const localDate = (date: Date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

export const periodRange = (preset: string, now = new Date(), year = now.getFullYear()) => {
    if (isMonthPeriod(preset)) {
        const month = Number(preset.slice(6)) - 1;
        return {from: localDate(new Date(year, month, 1)), to: localDate(new Date(year, month + 1, 0))};
    }
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    if (preset === 'LAST_MONTH') return {from: localDate(new Date(now.getFullYear(), now.getMonth() - 1, 1)), to: localDate(new Date(now.getFullYear(), now.getMonth(), 0))};
    if (preset === '3M') return {from: localDate(new Date(now.getFullYear(), now.getMonth() - 2, 1)), to: localDate(end)};
    if (preset === '6M') return {from: localDate(new Date(now.getFullYear(), now.getMonth() - 5, 1)), to: localDate(end)};
    if (preset === 'YEAR') return {from: localDate(new Date(now.getFullYear(), 0, 1)), to: localDate(end)};
    return {from: localDate(new Date(now.getFullYear(), now.getMonth(), 1)), to: localDate(end)};
};
