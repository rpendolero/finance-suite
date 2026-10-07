import {useEffect, useState} from 'react';
import {
    CalendarDays,
    CreditCard,
    Landmark,
    LayoutDashboard,
    Lightbulb,
    PiggyBank,
    ReceiptText,
    RefreshCw,
    Search,
    TrendingDown,
    TrendingUp,
    TriangleAlert,
    Wallet
} from 'lucide-react';
import {
    Area,
    AreaChart,
    Bar,
    BarChart,
    CartesianGrid,
    Cell,
    Pie,
    PieChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis
} from 'recharts';
import {api, Movement, Product} from './api';

const eur = (n: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(n);
const demoTrend = [['May', 3150, 2050], ['Jun', 3200, 2240], ['Jul', 3100, 2180], ['Ago', 3350, 2320], ['Sep', 3250, 2410], ['Oct', 3400, 2145]].map(([month, income, expense]) => ({
    month,
    income,
    expense,
    saving: (income as number) - (expense as number)
}));
const cats = [['Alimentación', 543.21], ['Vivienda', 412.6], ['Transporte', 298.15], ['Restaurantes', 265.4], ['Compras', 210.32], ['Ocio', 156.8], ['Suministros', 142.11]];
const demoMov = [['24 oct', 'Supermercado DIA', -42.35, 'Alimentación'], ['24 oct', 'Nómina', 3250, 'Ingresos'], ['23 oct', 'Repsol', -60.2, 'Transporte'], ['22 oct', 'Amazon', -98.45, 'Compras'], ['22 oct', 'Netflix', -15.99, 'Suscripciones']];

function Card({title, value, sub, icon: Icon, tone = 'blue'}: any) {
    return <div className="card kpi">
        <div className={'icon ' + tone}><Icon size={21}/></div>
        <div><span className="muted">{title}</span><strong>{value}</strong><small>{sub}</small></div>
    </div>
}

export default function App() {
    const [now] = useState(new Date()), [products, setProducts] = useState<Product[]>([]), [movements, setMovements] = useState<Movement[]>([]), [summary, setSummary] = useState<any>(null), [live, setLive] = useState(false);
    const from = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10),
        to = now.toISOString().slice(0, 10);
    useEffect(() => {
        Promise.all([api.products(), api.summary(from, to), api.movements(from, to)]).then(([p, s, m]) => {
            setProducts(p);
            setSummary(s);
            setMovements(m);
            setLive(true)
        }).catch(() => setLive(false))
    }, [from, to]);
    const balance = live ? products.filter(p => p.type === 'ACCOUNT' || p.type === 'WALLET').reduce((a, p) => a + Number(p.balance || 0), 0) : 12480.32,
        income = live ? Number(summary?.income ?? 0) : 3250,
        expenses = live ? Math.abs(Number(summary?.expenses ?? 0)) : 2145.32, saving = income - expenses,
        rate = income ? saving / income * 100 : 0;
    const shown = live ? movements.slice(0, 5) : demoMov;
    return <div className="shell">
        <aside>
            <div className="brand"><b>▥</b><span>Finance Suite</span></div>
            <nav><a className="active"><LayoutDashboard/>Inicio</a><a><TrendingUp/>Evolución</a><a><ReceiptText/>Gastos</a><a><CreditCard/>Productos</a><a><Search/>Movimientos</a><a><RefreshCw/>Recurrentes</a><a><CalendarDays/>Calendario</a><a><Lightbulb/>Insights</a><a><TriangleAlert/>Revisar</a>
            </nav>
            <div className="version"><span
                className={live ? 'dot live' : 'dot'}></span>{live ? 'API conectada' : 'Modo demo'}<small>v0.5.0</small>
            </div>
        </aside>
        <main>
            <header>
                <div><h1>Resumen financiero</h1><p>Tu situación financiera de un vistazo</p></div>
                <div className="period">Este mes · {from} — {to}</div>
            </header>
            <section className="kpis"><Card title="Saldo total" value={eur(balance)} sub="Disponible en cuentas"
                                            icon={Wallet} tone="green"/><Card title="Ingresos del mes"
                                                                              value={eur(income)}
                                                                              sub="↑ 5,2 % vs. mes anterior"
                                                                              icon={TrendingUp}/><Card
                title="Gastos del mes" value={eur(expenses)} sub="↑ 18,4 % vs. mes anterior" icon={TrendingDown}
                tone="red"/><Card title="Ahorro del mes" value={eur(saving)} sub={rate.toFixed(1) + ' % tasa de ahorro'}
                                  icon={PiggyBank} tone="purple"/></section>
            <section className="grid3">
                <div className="card wide"><Title t="Evolución financiera"/><ResponsiveContainer width="100%"
                                                                                                 height={245}><BarChart
                    data={demoTrend}><CartesianGrid strokeDasharray="3 3" vertical={false}/><XAxis
                    dataKey="month"/><YAxis/><Tooltip formatter={(v: any) => eur(Number(v))}/><Bar dataKey="income"
                                                                                                   name="Ingresos"
                                                                                                   fill="#42c997"
                                                                                                   radius={[5, 5, 0, 0]}/><Bar
                    dataKey="expense" name="Gastos" fill="#ff7474"
                    radius={[5, 5, 0, 0]}/></BarChart></ResponsiveContainer></div>
                <div className="card"><Title t="Gastos por categoría"/>
                    <div className="pie"><ResponsiveContainer width="48%" height={220}><PieChart><Pie
                        data={cats.map(([name, value]) => ({name, value}))} dataKey="value" nameKey="name"
                        innerRadius={55} outerRadius={82}>{cats.map((_, i) => <Cell key={i}/>)}</Pie><Tooltip
                        formatter={(v: any) => eur(Number(v))}/></PieChart></ResponsiveContainer>
                        <div className="legend">{cats.map(([n, v]) => <div><span>{n}</span><b>{eur(Number(v))}</b>
                        </div>)}</div>
                    </div>
                </div>
                <div className="card forecast"><Title
                    t="Previsión fin de mes"/><strong>{eur(expenses / Math.max(now.getDate(), 1) * 30)}</strong>
                    <p>Estimación según el ritmo de gasto actual.</p><ResponsiveContainer width="100%"
                                                                                          height={140}><AreaChart
                        data={demoTrend}><Area dataKey="expense" fill="#dbeafe" stroke="#2563eb"/><XAxis dataKey="month"
                                                                                                         hide/><YAxis
                        hide/></AreaChart></ResponsiveContainer></div>
            </section>
            <section className="bottom">
                <div className="card"><Title t="Movimientos recientes"/>
                    <div className="rows">{shown.map((m: any, i) => <div className="row" key={i}>
                        <span>{Array.isArray(m) ? m[0] : m.date}</span><b>{Array.isArray(m) ? m[1] : m.merchant || m.description}</b><em
                        className={(Array.isArray(m) ? m[2] : m.amount) > 0 ? 'pos' : 'neg'}>{eur(Number(Array.isArray(m) ? m[2] : m.amount))}</em><small>{Array.isArray(m) ? m[3] : m.category || 'Sin categoría'}</small>
                    </div>)}</div>
                </div>
                <div className="card"><Title t="Financial Insights"/>
                    <div className="insight bad">↗ Has gastado un 18 % más que el mes pasado.</div>
                    <div className="insight good">↗ Tu tasa de ahorro es del {rate.toFixed(0)} %.</div>
                    <div className="insight">◎ Alimentación concentra el 25 % del gasto.</div>
                    <div className="insight warn">⚠ Revisa movimientos estadísticamente inusuales.</div>
                </div>
                <div className="card"><Title t="Productos financieros"/>{(live ? products : [{
                    id: 'kutxa-main',
                    name: 'Kutxabank Cuenta',
                    type: 'ACCOUNT',
                    provider: 'KUTXABANK',
                    balance: 10320.15,
                    currency: 'EUR'
                }, {
                    id: 'visa',
                    name: 'Visa Oro',
                    type: 'CREDIT_CARD',
                    provider: 'KUTXABANK',
                    balance: -640.2,
                    currency: 'EUR'
                }, {
                    id: 'ing',
                    name: 'Cuenta ING',
                    type: 'ACCOUNT',
                    provider: 'ING',
                    balance: 2160.17,
                    currency: 'EUR'
                }] as Product[]).slice(0, 5).map(p => <div className="product" key={p.id}>
                    <Landmark/><span><b>{p.name}</b><small>{p.provider} · {p.type}</small></span><strong>{eur(Number(p.balance))}</strong>
                </div>)}</div>
            </section>
        </main>
    </div>
}

function Title({t}: { t: string }) {
    return <div className="title"><h2>{t}</h2>
        <button>Este mes⌄</button>
    </div>
}