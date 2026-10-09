import type {CalendarDay} from './api';

const WEEKDAYS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
const dayLabel = new Intl.DateTimeFormat('es-ES', {day: '2-digit', month: 'short', timeZone: 'UTC'});

export function calendarCells(days: CalendarDay[]) {
    const ordered = [...days].sort((left, right) => left.date.localeCompare(right.date));
    if (!ordered.length) return [];
    const first = new Date(`${ordered[0].date}T00:00:00Z`);
    const firstWeekday = (first.getUTCDay() + 6) % 7;
    return ordered.map(day => {
        const date = new Date(`${day.date}T00:00:00Z`);
        const offset = (date.getTime() - first.getTime()) / 86400000 + firstWeekday;
        return {day, column: offset % 7 + 1, row: Math.floor(offset / 7) + 2};
    });
}

const eur = (amount: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(amount);

export default function FinancialCalendar({days}: {days: CalendarDay[]}) {
    return <>
        <div className="calendar-scroll" tabIndex={0} role="region" aria-label="Calendario financiero de lunes a domingo">
            <div className="calendar-grid">
                {WEEKDAYS.map((weekday, index) => <div className="calendar-weekday" key={weekday}
                    style={{gridColumn: index + 1, gridRow: 1}}>{weekday}</div>)}
                {calendarCells(days).map(({day, column, row}) => <div key={day.date}
                    className={'day ' + (day.expenses > 0 ? 'has-expense' : '')}
                    style={{gridColumn: column, gridRow: row}}>
                    <b><time dateTime={day.date}>{dayLabel.format(new Date(`${day.date}T00:00:00Z`))}</time></b>
                    <small>{day.operations} op.</small>
                    <span className="neg">{day.expenses ? '-' + eur(day.expenses) : ''}</span>
                    <span className="pos">{day.income ? '+' + eur(day.income) : ''}</span>
                </div>)}
            </div>
        </div>
        {!days.length && <div className="empty">Sin datos en el período seleccionado.</div>}
    </>;
}
