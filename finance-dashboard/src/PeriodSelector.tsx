import {isMonthPeriod, MONTHS} from './periods';

type Props = {
    preset: string;
    year: number;
    currentYear: number;
    from: string;
    to: string;
    onPresetChange: (preset: string) => void;
    onYearChange: (year: number) => void;
    onFromChange: (from: string) => void;
    onToChange: (to: string) => void;
};

export default function PeriodSelector({preset, year, currentYear, from, to, onPresetChange, onYearChange, onFromChange, onToChange}: Props) {
    const years = Array.from({length: currentYear + 2 - 1900}, (_, index) => currentYear + 1 - index);
    return <div className="period period-selector">
        <select aria-label="Período" value={preset} onChange={event => onPresetChange(event.target.value)}>
            <option value="THIS_MONTH">Este mes</option>
            <option value="LAST_MONTH">Mes anterior</option>
            <option value="3M">Últimos 3 meses</option>
            <option value="6M">Últimos 6 meses</option>
            <option value="YEAR">Este año</option>
            <option value="CUSTOM">Personalizado</option>
            <optgroup label="Meses">
                {MONTHS.map((month, index) => <option key={month} value={`MONTH_${String(index + 1).padStart(2, '0')}`}>{month}</option>)}
            </optgroup>
        </select>
        {isMonthPeriod(preset) && <label>Año <select aria-label="Año" value={year} onChange={event => onYearChange(Number(event.target.value))}>
            {years.map(value => <option key={value} value={value}>{value}</option>)}
        </select></label>}
        {preset === 'CUSTOM' && <>
            <input aria-label="Desde" type="date" value={from} max={to} onChange={event => onFromChange(event.target.value)}/>
            <span>—</span>
            <input aria-label="Hasta" type="date" value={to} min={from} onChange={event => onToChange(event.target.value)}/>
        </>}
        <small>{from} — {to}</small>
    </div>;
}
