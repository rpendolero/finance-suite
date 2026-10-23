import type {Forecast} from './api';

const eur = (amount: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(amount);
const dateLabel = (date: string) => new Date(date + 'T00:00:00').toLocaleDateString('es-ES');

export default function ForecastCard({forecast, from, to, error}: {
    forecast: Forecast | null; from: string; to: string; error?: string;
}) {
    const valid = forecast && Number.isInteger(forecast.days) && forecast.days > 0
        && [forecast.accountBalances, forecast.estimatedNetFlow, forecast.estimatedAccountBalance].every(Number.isFinite);
    return <section className="card forecast" aria-label="Previsión de saldo">
        <div className="title"><h2>Previsión de saldo</h2></div>
        <p>Estimación de ingresos y gastos recurrentes, junto con el flujo variable del histórico.</p>
        {error ? <p role="alert">No se pudo cargar la previsión: {error}</p> : !forecast || !valid ?
            <div className="empty">No hay una previsión de saldo disponible para este período.</div> : <>
            <span className="forecast-label">Saldo estimado dentro de {forecast.days} días</span>
            <strong className={forecast.estimatedAccountBalance < 0 ? 'neg' : ''}>{eur(forecast.estimatedAccountBalance)}</strong>
            <dl className="forecast-breakdown">
                <div><dt>Saldo de partida</dt><dd>{eur(forecast.accountBalances)}</dd></div>
                {forecast.estimatedIncome != null && <div><dt>Ingresos previstos (estimados)</dt><dd className="pos">{eur(forecast.estimatedIncome)}</dd></div>}
                {forecast.estimatedExpenses != null && <div><dt>Gastos previstos (estimados)</dt><dd className="neg">−{eur(forecast.estimatedExpenses)}</dd></div>}
                <div><dt>Balance neto previsto</dt><dd className={forecast.estimatedNetFlow < 0 ? 'neg' : 'pos'}>{eur(forecast.estimatedNetFlow)}</dd></div>
            </dl>
            <p>Histórico utilizado: {dateLabel(from)} — {dateLabel(to)}.<br/>Calculada el {dateLabel(forecast.asOf)}.</p>
            {forecast.recurringPatterns != null && <p>Patrones recurrentes identificados: {forecast.recurringPatterns}. Ingresos recurrentes: {eur(forecast.recurringIncome ?? 0)}; gastos recurrentes: {eur(forecast.recurringExpenses ?? 0)}.</p>}
            <p>{forecast.limitations}</p>
        </>}
    </section>;
}
