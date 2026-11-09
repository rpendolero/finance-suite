import {useEffect, useState} from 'react';
import {ArrowDownLeft, ArrowRight, ArrowUpRight, Landmark, Wallet} from 'lucide-react';
import {Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis} from 'recharts';
import {api, type CategoryStat, type Forecast, type Movement, type Overview, type Product, type TrendPoint} from './api';
import {isComputableMovement} from './movementTotals';
import ForecastCard from './ForecastCard';

const eur = (n: number) => new Intl.NumberFormat('es-ES', {style:'currency', currency:'EUR'}).format(n);
const movementAmount = (m: Movement) => m.currency && /^[A-Z]{3}$/.test(m.currency)
    ? new Intl.NumberFormat('es-ES', {style:'currency', currency:m.currency}).format(m.amount)
    : new Intl.NumberFormat('es-ES', {minimumFractionDigits:2, maximumFractionDigits:2}).format(m.amount) + ' · moneda no disponible';
const day = (date: string) => new Date(date + 'T00:00:00').toLocaleDateString('es-ES', {day:'numeric', month:'short'});
const snapshotDate = (date?: string) => date && Number.isFinite(Date.parse(date))
    ? new Date(date).toLocaleDateString('es-ES') : 'Fecha no disponible';

type Props = {
    overview: Overview; trend: TrendPoint[]; categories: CategoryStat[]; products: Product[]; movements: Movement[];
    forecast: Forecast | null; forecastError: string; from: string; to: string;
    onIncome: () => void; onExpenses: () => void; onProducts: () => void; onMovements: () => void;
};

export default function FinancialOverview({overview: o, trend, categories, products, movements, forecast, forecastError,
    from, to, onIncome, onExpenses, onProducts, onMovements}: Props) {
    const [categoryNames, setCategoryNames] = useState<Record<string,string>>({});
    useEffect(() => {
        let active = true;
        api.categoriesCatalog().then(items => {
            if (active) setCategoryNames(Object.fromEntries(items.map(item => [item.code, item.label])));
        }).catch(() => { /* Keep the API category code visible if the optional catalog is unavailable. */ });
        return () => {active = false;};
    }, []);
    const expenses = categories.filter(c => c.amount > 0);
    const liquid = products.filter(p => p.type === 'ACCOUNT' || p.type === 'WALLET');
    const range = `${day(from)} — ${day(to)} · ${from.slice(0,4)}${from.slice(0,4) !== to.slice(0,4) ? ' / ' + to.slice(0,4) : ''}`;
    return <div className="financial-overview">
        <section className="overview-balance" aria-labelledby="balance-heading">
            <div className="overview-balance-copy">
                <span className="overview-eyebrow">TU DINERO · FINANCE SUITE</span>
                <h2 id="balance-heading">Saldo registrado</h2>
                <strong className="overview-balance-amount">{eur(o.totalBalance)}</strong>
                <p>Últimos saldos guardados de cuentas y monederos.<br/>No depende del período ni descuenta deuda de tarjetas.</p>
                <button type="button" onClick={onProducts}>Consultar productos <ArrowRight size={17} aria-hidden="true"/></button>
            </div>
            <div className="overview-balance-note"><Wallet size={26} aria-hidden="true"/><span>SALDOS REGISTRADOS</span><p>El resultado cuenta lo que ingresas y gastas.<br/>El saldo muestra el dinero registrado en tus cuentas.</p></div>
        </section>

        <section className="overview-result" aria-labelledby="result-heading">
            <div className="overview-section-label"><span className="overview-eyebrow">01 / REALIZADO</span><span>{range} · EUR</span></div>
            <div className="overview-result-grid">
                <div className="overview-net"><h2 id="result-heading">Resultado del período</h2><strong>{eur(o.availableAfterAllocations)}</strong><p>Ingresos menos gastos computables.</p></div>
                <button type="button" className="overview-metric income" onClick={onIncome} aria-label="Ver ingresos del período">
                    <span><ArrowDownLeft size={20} aria-hidden="true"/> Ingresos <ArrowRight size={16} aria-hidden="true"/></span><strong>{eur(o.income)}</strong><small>Ver abonos computables · incluye devoluciones</small>
                </button>
                <button type="button" className="overview-metric expense" onClick={onExpenses} aria-label="Ver gastos del período">
                    <span><ArrowUpRight size={20} aria-hidden="true"/> Gastos <ArrowRight size={16} aria-hidden="true"/></span><strong>{eur(o.expenses)}</strong><small>Ver cargos computables</small>
                </button>
            </div>
            <p className="overview-rule">Traspasos propios y liquidaciones quedan fuera del resultado cuando están clasificados como no computables. Las compras se cuentan en su producto de origen.</p>
            <details className="overview-allocations"><summary>Desglose de ahorro y financiación</summary><p>Estos importes ya forman parte del resultado. No se restan otra vez. El principal de préstamos y sus intereses solo se separan si están clasificados de forma explícita.</p>
                <dl><div><dt>Ahorro e inversión · aportaciones netas</dt><dd>{eur(o.savings)}</dd></div><div><dt>Financiación · pagos netos</dt><dd>{eur(o.financingPayments)}</dd></div><div><dt>Principal identificado</dt><dd>{eur(o.financingCapital)}</dd></div><div><dt>Intereses identificados</dt><dd>{eur(o.financingInterest)}</dd></div><div><dt>Financiación sin desglose</dt><dd>{eur(o.financingUnallocated)}</dd></div></dl>
            </details>
        </section>

        <div className="overview-analysis-grid">
            <section className="overview-panel overview-history" aria-labelledby="history-heading">
                <div className="overview-section-label"><span className="overview-eyebrow">02 / EVOLUCIÓN</span><span>Movimientos computables · EUR</span></div>
                <h2 id="history-heading">El ritmo de tu dinero</h2><p className="overview-caption">{range}</p>
                {trend.length ? <>
                    <div className="overview-chart" aria-label="Gráfico de ingresos y gastos en euros; valores en la tabla siguiente">
                        <ResponsiveContainer width="100%" height={260}><BarChart data={trend} accessibilityLayer margin={{left:0,right:8,top:16,bottom:0}}>
                            <CartesianGrid vertical={false} stroke="var(--fs-border)"/><XAxis dataKey="date" tickFormatter={day} tick={{fill:'var(--fs-muted)',fontSize:'var(--fs-text-xs)'}} axisLine={false} tickLine={false}/>
                            <YAxis width={62} tickFormatter={n => new Intl.NumberFormat('es-ES',{notation:'compact'}).format(Number(n)) + ' €'} tick={{fill:'var(--fs-muted)',fontSize:'var(--fs-text-xs)'}} axisLine={false} tickLine={false}/>
                            <Tooltip contentStyle={{backgroundColor:'var(--fs-surface)',border:'1px solid var(--fs-border)',borderRadius:'var(--fs-radius-control)',color:'var(--fs-ink)'}} labelFormatter={v => day(String(v))} formatter={v => eur(Number(v))}/><Legend/>
                            <Bar isAnimationActive={false} dataKey="income" name="Ingresos (€)" fill="var(--fs-positive)" radius={[3,3,0,0]} maxBarSize={36}/><Bar isAnimationActive={false} dataKey="expenses" name="Gastos (€)" fill="var(--fs-negative)" radius={[3,3,0,0]} maxBarSize={36}/>
                        </BarChart></ResponsiveContainer>
                    </div>
                    <details className="overview-chart-data"><summary>Ver valores de la evolución</summary><table><caption>Ingresos y gastos computables · EUR · {range}</caption><thead><tr><th scope="col">Fecha</th><th scope="col">Ingresos</th><th scope="col">Gastos</th></tr></thead><tbody>{trend.map(t=><tr key={t.date}><th scope="row">{day(t.date)}</th><td>{eur(t.income)}</td><td>{eur(t.expenses)}</td></tr>)}</tbody></table></details>
                </> : <p className="overview-empty">Sin movimientos en el período.</p>}
            </section>
            <section className="overview-panel overview-categories" aria-labelledby="category-heading">
                <div className="overview-section-label"><span className="overview-eyebrow">03 / DISTRIBUCIÓN</span><span>EUR</span></div><h2 id="category-heading">Dónde se va</h2><p className="overview-caption">Gastos por categoría · {range}</p>
                {expenses.length ? <ul>{expenses.map(c=><li key={c.category}><div><span>{categoryNames[c.category] || c.category}</span><strong>{eur(c.amount)}</strong></div><div className="overview-category-track" aria-hidden="true"><span style={{width:`${Math.max(0,Math.min(100,c.share))}%`}}/></div><small>{new Intl.NumberFormat('es-ES',{maximumFractionDigits:1}).format(c.share)} % del gasto · {c.operations} operaciones</small></li>)}</ul> : <p className="overview-empty">Sin gastos clasificados.</p>}
                <p className="overview-caption">Las devoluciones positivas se incluyen en ingresos.</p>
            </section>
        </div>

        <div className="overview-planning-grid">
            <div className="overview-estimate"><span className="overview-eyebrow">04 / ESTIMADO · NO CONFIRMADO</span><ForecastCard forecast={forecast} from={from} to={to} error={forecastError}/></div>
            <section className="overview-panel overview-accounts" aria-labelledby="accounts-heading"><div className="overview-section-label"><span className="overview-eyebrow">05 / TUS CUENTAS</span><Landmark size={19} aria-hidden="true"/></div><h2 id="accounts-heading">Saldos con fecha</h2><p className="overview-caption">Snapshots de cuentas y monederos; las tarjetas se consultan en Productos.</p>
                {liquid.length ? <ul>{liquid.map(p=><li key={p.id}><div><b>{p.name}</b><small>{p.provider} · {snapshotDate(p.balanceAt)}</small></div><strong>{eur(p.balance)}</strong></li>)}</ul> : <p className="overview-empty">No hay cuentas ni monederos registrados.</p>}
                <button type="button" className="overview-link" onClick={onProducts}>Ver cuentas y tarjetas <ArrowRight size={16} aria-hidden="true"/></button>
            </section>
        </div>

        <section className="overview-panel overview-recent" aria-labelledby="recent-heading"><div className="overview-section-label"><div><span className="overview-eyebrow">06 / ACTIVIDAD</span><h2 id="recent-heading">Movimientos recientes</h2></div><button type="button" className="overview-link" onClick={onMovements}>Ver todos <ArrowRight size={16} aria-hidden="true"/></button></div>
            <p className="overview-caption">Últimas operaciones del período, incluidas las excluidas del resultado.</p>
            {movements.length ? <ul>{movements.slice(0,5).map(m=><li key={m.id}><time dateTime={m.date}>{day(m.date)}</time><div><b>{m.merchant || m.description}</b><small>{isComputableMovement(m) ? (categoryNames[m.category || ''] || m.category || 'Sin categoría') : 'Excluido del resultado'}</small></div><strong>{m.amount > 0 ? '+' : ''}{movementAmount(m)}</strong></li>)}</ul> : <p className="overview-empty">Sin movimientos recientes.</p>}
        </section>
    </div>;
}
