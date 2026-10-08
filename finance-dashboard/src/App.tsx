import ProductCreate from './ProductCreate';
import BankingConnections from './BankingConnections';
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
    Wallet,
    Tags
} from 'lucide-react';
import {Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis} from 'recharts';
import {
    AdminCategory,
    Anomaly,
    api,
    CalendarDay,
    CategoryStat,
    CategoryDefinition,
    Insight,
    MerchantStat,
    Movement,
    Overview,
    Product,
    ProductStat,
    Recurring,
    TrendPoint
} from './api';

const eur = (n: number) => new Intl.NumberFormat('es-ES', {style: 'currency', currency: 'EUR'}).format(Number(n || 0));

const CATEGORY_COLORS = [
    '#2563eb',
    '#16a34a',
    '#f59e0b',
    '#dc2626',
    '#7c3aed',
    '#0891b2',
    '#db2777',
    '#65a30d',
    '#ea580c',
    '#4f46e5'
];
const localDate = (d: Date) => {
    const y = d.getFullYear(), m = String(d.getMonth() + 1).padStart(2, '0'), day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
};
const periodRange = (preset: string, now = new Date()) => {
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    if (preset === 'LAST_MONTH') return {from: localDate(new Date(now.getFullYear(), now.getMonth() - 1, 1)), to: localDate(new Date(now.getFullYear(), now.getMonth(), 0))};
    if (preset === '3M') return {from: localDate(new Date(now.getFullYear(), now.getMonth() - 2, 1)), to: localDate(end)};
    if (preset === '6M') return {from: localDate(new Date(now.getFullYear(), now.getMonth() - 5, 1)), to: localDate(end)};
    if (preset === 'YEAR') return {from: localDate(new Date(now.getFullYear(), 0, 1)), to: localDate(end)};
    return {from: localDate(new Date(now.getFullYear(), now.getMonth(), 1)), to: localDate(end)};
};
const dateLabel = (d: string) => new Intl.DateTimeFormat('es-ES', {
    day: '2-digit',
    month: 'short'
}).format(new Date(d + 'T00:00:00'));

function Card({title, value, sub, icon: Icon, tone = 'blue'}: {
    title: string;
    value: string;
    sub: string;
    icon: any;
    tone?: string
}) {
    return <div className="card kpi">
        <div className={'icon ' + tone}><Icon size={21}/></div>
        <div><span className="muted">{title}</span><strong>{value}</strong><small>{sub}</small></div>
    </div>
}

function Title({t}: { t: string }) {
    return <div className="title"><h2>{t}</h2></div>
}

function Empty({children}: { children: string }) {
    return <div className="empty">{children}</div>
}

type View =
    'overview'
    | 'trend'
    | 'expenses'
    | 'products'
    | 'movements'
    | 'recurring'
    | 'calendar'
    | 'insights'
    | 'review'
    | 'categoryAdmin'
    | 'banking';
const menu: [View, string, any][] = [['overview', 'Inicio', LayoutDashboard], ['trend', 'Evolución', TrendingUp], ['expenses', 'Gastos', ReceiptText], ['products', 'Productos', CreditCard], ['movements', 'Movimientos', Search], ['recurring', 'Recurrentes', RefreshCw], ['calendar', 'Calendario', CalendarDays], ['insights', 'Insights', Lightbulb], ['review', 'Revisar', TriangleAlert], ['categoryAdmin', 'Categorías', Tags], ['banking', 'Bancos', Landmark]];

export default function App() {
    const [session,setSession]=useState<any>(null),[authLoading,setAuthLoading]=useState(true);
    useEffect(()=>{api.me().then(setSession).catch(()=>setSession(null)).finally(()=>setAuthLoading(false))},[]);
    if(authLoading)return <div className="auth-screen"><div className="login-card"><div className="login-logo">▥</div><h1>Finance Suite</h1><p>Comprobando sesión…</p></div></div>;
    if(!session)return <Login onLogin={setSession}/>;
    return <Dashboard session={session} onLogout={()=>api.logout().finally(()=>setSession(null))}/>;
}

function Login({onLogin}:{onLogin:(session:any)=>void}){
 const[username,setUsername]=useState('reader'),[password,setPassword]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 const submit=(e:any)=>{e.preventDefault();setBusy(true);setError('');api.login(username,password).then(onLogin).catch(()=>setError('Usuario o contraseña incorrectos')).finally(()=>setBusy(false))};
 return <div className="auth-screen"><form className="login-card" onSubmit={submit}><div className="login-logo">▥</div><h1>Finance Suite</h1><p>Accede a tu panel financiero</p><label>Usuario<input autoFocus autoComplete="username" value={username} onChange={e=>setUsername(e.target.value)}/></label><label>Contraseña<input type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)}/></label>{error&&<div className="login-error">{error}</div>}<button disabled={busy||!username||!password}>{busy?'Accediendo…':'Iniciar sesión'}</button></form></div>
}

function Dashboard({session,onLogout}:{session:any;onLogout:()=>void}) {
    const [now] = useState(new Date()), [view, setView] = useState<View>(new URLSearchParams(window.location.search).has('banking') && session.roles?.includes('ADMIN') ? 'banking' : 'overview'), [overview, setOverview] = useState<Overview | null>(null), [trend, setTrend] = useState<TrendPoint[]>([]), [categories, setCategories] = useState<CategoryStat[]>([]), [products, setProducts] = useState<Product[]>([]), [movements, setMovements] = useState<Movement[]>([]), [insights, setInsights] = useState<Insight[]>([]), [forecast, setForecast] = useState<any>(null), [merchants, setMerchants] = useState<MerchantStat[]>([]), [productStats, setProductStats] = useState<ProductStat[]>([]), [calendar, setCalendar] = useState<CalendarDay[]>([]), [recurring, setRecurring] = useState<Recurring[]>([]), [anomalies, setAnomalies] = useState<Anomaly[]>([]), [loading, setLoading] = useState(true), [error, setError] = useState('');
    const initialPeriod = periodRange('THIS_MONTH', now);
    const [periodPreset, setPeriodPreset] = useState('THIS_MONTH');
    const [from, setFrom] = useState(initialPeriod.from);
    const [to, setTo] = useState(initialPeriod.to);
    const selectPeriod = (preset: string) => {
        setPeriodPreset(preset);
        if (preset !== 'CUSTOM') {
            const range = periodRange(preset, now);
            setFrom(range.from);
            setTo(range.to);
        }
    };
    useEffect(() => {
        let active = true;
        setLoading(true);
        setError('');
        Promise.all([api.overview(from, to), api.trend(from, to, undefined, 'DAY'), api.categories(from, to), api.products(), api.movements(from, to, undefined, 10), api.insights(from, to), api.forecast(from, to), api.merchants(from, to), api.productStats(from, to), api.calendar(from, to), api.recurring(from, to), api.anomalies(from, to)]).then(([o, t, c, p, m, i, f, mer, ps, cal, rec, ano]) => {
            if (!active) return;
            setOverview(o);
            setTrend(t);
            setCategories(c);
            setProducts(p);
            setMovements(m);
            setInsights(i);
            setForecast(f);
            setMerchants(mer);
            setProductStats(ps);
            setCalendar(cal);
            setRecurring(rec);
            setAnomalies(ano)
        }).catch(e => {
            if (active) setError(e instanceof Error ? e.message : 'No se pudo cargar el dashboard')
        }).finally(() => active && setLoading(false));
        return () => {
            active = false
        }
    }, [from, to]);
    const onProductCreated = () => {
        api.products().then(setProducts).catch(e => setError(e instanceof Error ? e.message : 'No se pudieron actualizar los productos'));
        api.productStats(from, to).then(setProductStats).catch(e => setError(e instanceof Error ? e.message : 'No se pudieron actualizar los saldos'));
        api.overview(from, to).then(setOverview).catch(e => setError(e instanceof Error ? e.message : 'No se pudo actualizar el resumen'));
    };
    const projected = Number(forecast?.projectedExpenses ?? forecast?.expenses ?? 0);
    return <div className="shell">
        <aside>
            <div className="brand"><b>▥</b><span>Finance Suite</span></div>
            <nav>{menu.filter(([id]) => (id !== 'categoryAdmin' && id !== 'banking') || session.roles?.includes('ADMIN')).map(([id, label, Icon]) => <button key={id} className={view === id ? 'active' : ''}
                                                          onClick={() => setView(id)}><Icon/>{label}</button>)}</nav>
            <div className="version"><span
                className={error ? 'dot' : 'dot live'}></span>{loading ? 'Cargando API' : error ? 'API no disponible' : 'API conectada'}<small>v0.5.0</small>
            </div>
        </aside>
        <main>
            <header>
                <div><h1>{menu.find(m => m[0] === view)?.[1]}</h1><p>Información financiera basada en los datos
                    importados</p></div>
                <div className="header-actions">
                    <div className="period period-selector">
                        <select aria-label="Período" value={periodPreset} onChange={e => selectPeriod(e.target.value)}>
                            <option value="THIS_MONTH">Este mes</option>
                            <option value="LAST_MONTH">Mes anterior</option>
                            <option value="3M">Últimos 3 meses</option>
                            <option value="6M">Últimos 6 meses</option>
                            <option value="YEAR">Este año</option>
                            <option value="CUSTOM">Personalizado</option>
                        </select>
                        {periodPreset === 'CUSTOM' && <>
                            <input aria-label="Desde" type="date" value={from} max={to} onChange={e => setFrom(e.target.value)}/>
                            <span>—</span>
                            <input aria-label="Hasta" type="date" value={to} min={from} onChange={e => setTo(e.target.value)}/>
                        </>}
                        <small>{from} — {to}</small>
                    </div>
                    <button className="logout" onClick={onLogout}>{session.username} · Salir</button>
                </div>
            </header>
            {error && <div className="api-error"><b>No se han podido cargar los datos.</b><span>{error}</span></div>}
            {view === 'banking' && session.roles?.includes('ADMIN') ? <BankingConnections products={products} onProductCreated={onProductCreated}/> : loading ?
                <div className="loading">Cargando información financiera…</div> : overview && view === 'overview' ? <>
                    <section className="kpis"><Card title="Saldo total" value={eur(overview.totalBalance)}
                                                    sub="Disponible en cuentas y monederos" icon={Wallet} tone="green"/><Card
                        title="Ingresos del período" value={eur(overview.income)} sub="Movimientos contabilizados"
                        icon={TrendingUp}/><Card title="Gastos del período" value={eur(overview.expenses)}
                                                 sub={eur(overview.averageDailyExpense) + ' / día'} icon={TrendingDown}
                                                 tone="red"/><Card title="Ahorro del período" value={eur(overview.savings)}
                                                                   sub={Number(overview.savingsRate).toFixed(1) + ' % tasa de ahorro'}
                                                                   icon={PiggyBank} tone="purple"/></section>
                    <section className="grid3">
                        <div className="card wide"><Title t="Evolución financiera"/>{trend.length ?
                            <ResponsiveContainer width="100%" height={245}><BarChart data={trend}><CartesianGrid
                                strokeDasharray="3 3" vertical={false}/><XAxis dataKey="date"
                                                                               tickFormatter={dateLabel}/><YAxis/><Tooltip
                                labelFormatter={(v) => dateLabel(String(v))} formatter={(v) => eur(Number(v))}/><Bar
                                dataKey="income" name="Ingresos" fill="#42c997" radius={[5, 5, 0, 0]}/><Bar
                                dataKey="expenses" name="Gastos" fill="#ff7474"
                                radius={[5, 5, 0, 0]}/></BarChart></ResponsiveContainer> :
                            <Empty>Sin movimientos en el período.</Empty>}</div>
                        <div className="card"><Title t="Gastos por categoría"/>{categories.length ?
                            <div className="pie"><ResponsiveContainer width="48%" height={220}><PieChart><Pie
                                data={categories} dataKey="amount" nameKey="category" innerRadius={55}
                                outerRadius={82}>{categories.map((_, i) => <Cell key={i}
                                fill={CATEGORY_COLORS[i % CATEGORY_COLORS.length]}/>)}</Pie><Tooltip
                                formatter={(v) => eur(Number(v))}/></PieChart></ResponsiveContainer>
                                <div className="legend">{categories.slice(0, 7).map((c, i) => <div key={c.category}>
                                    <span className="legend-label"><i
                                        style={{backgroundColor: CATEGORY_COLORS[i % CATEGORY_COLORS.length]}}></i>{c.category}</span><b>{eur(c.amount)}</b></div>)}</div>
                            </div> : <Empty>Sin gastos clasificados.</Empty>}</div>
                        <div className="card forecast"><Title t="Previsión"/>{projected > 0 ? <>
                                <strong>{eur(projected)}</strong><p>Proyección calculada por el servidor para los próximos
                                30 días.</p></> :
                            <Empty>El servidor no dispone de una proyección para este período.</Empty>}</div>
                    </section>
                    <section className="bottom">
                        <div className="card"><Title t="Movimientos recientes"/>{movements.length ?
                            <div className="rows">{movements.slice(0, 5).map(m => <div className="row" key={m.id}>
                                <span>{dateLabel(m.date)}</span><b>{m.merchant || m.description}</b><em
                                className={Number(m.amount) > 0 ? 'pos' : 'neg'}>{eur(m.amount)}</em><small>{m.category || 'Sin categoría'}</small>
                            </div>)}</div> : <Empty>Sin movimientos recientes.</Empty>}</div>
                        <div className="card"><Title t="Financial Insights"/>{insights.length ? insights.map(i => <div
                            key={i.code}
                            className={'insight ' + (i.severity === 'POSITIVE' ? 'good' : i.severity === 'WARNING' ? 'warn' : '')}>
                            <b>{i.title}</b><br/>{i.detail}</div>) : <Empty>No hay insights para este período.</Empty>}
                        </div>
                        <div className="card"><Title
                            t="Productos financieros"/>{products.length ? products.slice(0, 5).map(p => <div
                            className="product" key={p.id}>
                            <Landmark/><span><b>{p.name}</b><small>{p.provider} · {p.type}{p.maskedPan ? ' · ' + p.maskedPan : ''}</small></span><strong>{eur(p.balance)}</strong>
                        </div>) : <Empty>No hay productos registrados.</Empty>}</div>
                    </section>
                </> : !loading && overview ?
                    <SectionView view={view} trend={trend} categories={categories} merchants={merchants}
                                 productStats={productStats} products={products} movements={movements}
                                 recurring={recurring} calendar={calendar} insights={insights}
                                 anomalies={anomalies} session={session} from={from} to={to} onProductCreated={onProductCreated}/> : null}</main>
    </div>
}

function SectionView({
                         view,
                         trend,
                         categories,
                         merchants,
                         productStats,
                         products,
                         movements,
                         recurring,
                         calendar,
                         insights,
                         anomalies,
                         session,
                         from,
                         to,
                         onProductCreated
                     }: any) {
    if (view === 'trend') return <Page title="Evolución financiera">
        <div className="card chart-full"><ResponsiveContainer width="100%" height={420}><BarChart
            data={trend}><CartesianGrid strokeDasharray="3 3"/><XAxis dataKey="date" tickFormatter={dateLabel}/><YAxis/><Tooltip
            formatter={(v) => eur(Number(v))}/><Bar dataKey="income" name="Ingresos" fill="#42c997"/><Bar
            dataKey="expenses" name="Gastos" fill="#ff7474"/><Bar dataKey="savings" name="Ahorro"
                                                                  fill="#6c7cff"/></BarChart></ResponsiveContainer>
        </div>
    </Page>;
    if (view === 'expenses') return <Page title="Análisis de gastos">
        <div className="two-cols"><Rank title="Categorías"
                                        rows={categories.map((x: CategoryStat) => [x.category, x.amount, x.operations, x.share])}/><Rank
            title="Comercios" rows={merchants.map((x: MerchantStat) => [x.merchant, x.amount, x.operations, x.share])}/>
        </div>
    </Page>;
    if (view === 'products') return <Page title="Productos financieros">
        {session?.roles?.includes('ADMIN') && <ProductCreateButton products={products} onCreated={onProductCreated}/>}
        <div className="card table">
            <div className="thead"><b>Producto</b><b>Entidad</b><b>Tipo</b><b>Saldo</b><b>Gasto período</b></div>
            {productStats.map((s: ProductStat) => <div className="trow" key={s.productId}>
                <b>{s.name}</b><span>{s.provider}</span><span>{s.type}</span><span>{eur(s.balance)}</span><strong>{eur(s.expenses)}</strong>
            </div>)}</div>
    </Page>;
    if (view === 'movements') return <MovementSearch products={products} initialFrom={from} initialTo={to} canEdit={session?.roles?.includes('ADMIN')}/>;
    if (view === 'recurring') return <Page title="Gastos recurrentes">
        <div className="card list-cards">{recurring.length ? recurring.map((r: Recurring, i: number) => <div
            className="list-item" key={i}>
            <RefreshCw/><span><b>{String(r.merchant || r.description || 'Movimiento recurrente')}</b><small>{String(r.category || '')}</small></span><strong>{r.amount != null ? eur(Number(r.amount)) : ''}</strong>
        </div>) : <Empty>No se han detectado movimientos recurrentes.</Empty>}</div>
    </Page>;
    if (view === 'calendar') return <Page title="Calendario financiero">
        <div className="calendar-grid">{calendar.map((d: CalendarDay) => <div
            className={'day ' + (d.expenses > 0 ? 'has-expense' : '')} key={d.date}>
            <b>{new Date(d.date + 'T00:00:00').getDate()}</b><small>{d.operations} op.</small><span
            className="neg">{d.expenses ? '-' + eur(d.expenses) : ''}</span><span
            className="pos">{d.income ? '+' + eur(d.income) : ''}</span></div>)}</div>
    </Page>;
    if (view === 'insights') return <Page title="Insights financieros">
        <div className="insight-grid">{insights.length ? insights.map((i: Insight) => <div
            className={'card insight ' + (i.severity === 'POSITIVE' ? 'good' : i.severity === 'WARNING' ? 'warn' : '')}
            key={i.code}><b>{i.title}</b><p>{i.detail}</p><strong>{i.value != null ? eur(Number(i.value)) : ''}</strong>
        </div>) : <Empty>No hay insights para este período.</Empty>}</div>
    </Page>;
    if (view === 'review') return <ClassificationReview />;
    if (view === 'categoryAdmin' && session?.roles?.includes('ADMIN')) return <CategoryAdministration />;
    return null
}

const TREATMENTS = [
    {code: 'NON_COMPUTABLE', label: 'No computable - Otros', computable: false},
    {code: 'NORMAL', label: 'Gasto / ingreso normal', computable: true},
    {code: 'REFUND', label: 'Devolución', computable: true},
    {code: 'CARD_SETTLEMENT', label: 'No computable - Liquidación tarjeta', computable: false},
    {code: 'WALLET_SETTLEMENT', label: 'No computable - PayPal / monedero', computable: false},
    {code: 'DUPLICATE', label: 'No computable - Movimiento duplicado', computable: false},
    {code: 'INTERNAL_TRANSFER', label: 'No computable - Transferencia interna', computable: false}
] as const;

type ClassificationChoice = {
    category?: string;
    subcategory?: string;
    kind?: string;
};

function treatmentDefaults(kind: string): ClassificationChoice {
    switch (kind) {
        case 'NON_COMPUTABLE':
            return {kind, category: 'NO_COMPUTABLE', subcategory: 'OTROS_NO_COMPUTABLES'};
        case 'DUPLICATE':
            return {kind, category: 'NO_COMPUTABLE', subcategory: 'MOVIMIENTO_DUPLICADO'};
        case 'CARD_SETTLEMENT':
            return {kind, category: 'NO_COMPUTABLE', subcategory: 'LIQUIDACION_TARJETA'};
        case 'WALLET_SETTLEMENT':
            return {kind, category: 'NO_COMPUTABLE', subcategory: 'LIQUIDACION_PAYPAL'};
        case 'INTERNAL_TRANSFER':
            return {kind, category: 'NO_COMPUTABLE', subcategory: 'TRASPASO_INTERNO'};
        default:
            return {kind};
    }
}

function ClassificationReview() {
    const initial = periodRange('THIS_MONTH');
    const [periodPreset, setPeriodPreset] = useState('THIS_MONTH');
    const [from, setFrom] = useState(initial.from);
    const [to, setTo] = useState(initial.to);
    const [rows, setRows] = useState<Movement[]>([]);
    const [catalog, setCatalog] = useState<CategoryDefinition[]>([]);
    const [choices, setChoices] = useState<Record<string, ClassificationChoice>>({});
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState('');
    const [error, setError] = useState('');

    const load = () => {
        setBusy(true);
        setError('');
        Promise.all([api.unclassified(from, to), api.categoriesCatalog()])
            .then(([movements, categories]) => {
                setRows(movements);
                setCatalog(categories);
            })
            .catch(e => setError(e instanceof Error ? e.message : 'No se pudieron cargar los movimientos'))
            .finally(() => setBusy(false));
    };

    useEffect(() => {
        load()
    }, [from, to]);

    const choose = (id: string, key: 'category' | 'subcategory', value: string) => {
        setChoices(current => ({
            ...current,
            [id]: {
                ...current[id],
                [key]: value || undefined,
                ...(key === 'category' ? {subcategory: undefined, kind: value === 'NO_COMPUTABLE' ? undefined : (current[id]?.kind === 'REFUND' ? 'REFUND' : 'NORMAL')} : {}),
                ...(key === 'subcategory' ? {kind: ({LIQUIDACION_TARJETA: 'CARD_SETTLEMENT', LIQUIDACION_PAYPAL: 'WALLET_SETTLEMENT', TRASPASO_INTERNO: 'INTERNAL_TRANSFER', MOVIMIENTO_DUPLICADO: 'DUPLICATE', OTROS_NO_COMPUTABLES: 'NON_COMPUTABLE'} as Record<string, string>)[value] || current[id]?.kind} : {})
            }
        }))
    };

    const chooseTreatment = (id: string, kind: string) => {
        setChoices(current => ({
            ...current,
            [id]: treatmentDefaults(kind)
        }))
    };

    const save = async (movement: Movement, createRule: boolean) => {
        const selected = choices[movement.id] || treatmentDefaults(movement.kind || 'NORMAL');
        if (!selected.category) {
            setError('Selecciona una categoría antes de guardar.');
            return;
        }
        setBusy(true);
        setError('');
        setMessage('');
        try {
            await api.classifyMovement(movement.id, {
                category: selected.category,
                subcategory: selected.subcategory,
                kind: selected.kind || movement.kind || 'NORMAL',
                createRule,
                applyToSimilar: createRule
            });
            const treatment = TREATMENTS.find(t => t.code === (selected.kind || movement.kind || 'NORMAL'));
            setMessage(createRule
                ? `Clasificación guardada y aplicada a movimientos equivalentes. Tratamiento: ${treatment?.label || 'Normal'}.`
                : `Clasificación manual guardada. Tratamiento: ${treatment?.label || 'Normal'}.`);
            load();
        } catch (e) {
            setBusy(false);
            setError(e instanceof Error ? e.message : 'No se pudo guardar la clasificación')
        }
    };

    const reclassify = async () => {
        setBusy(true);
        setError('');
        setMessage('');
        try {
            const result = await api.reclassify();
            setMessage(`Recategorización completada: ${result.updated} de ${result.scanned} movimientos actualizados; ${result.unclassified} pendientes.`);
            load();
        } catch (e) {
            setBusy(false);
            setError(e instanceof Error ? e.message : 'No se pudo recategorizar el histórico')
        }
    };

    return <Page title="Movimientos pendientes de categorizar">
        <div className="classification-toolbar">
            <div className="review-period">
                <select value={periodPreset} onChange={e => {
                    const preset = e.target.value;
                    setPeriodPreset(preset);
                    if (preset !== 'CUSTOM') {
                        const range = periodRange(preset);
                        setFrom(range.from); setTo(range.to);
                    }
                }}>
                    <option value="THIS_MONTH">Este mes</option>
                    <option value="LAST_MONTH">Mes anterior</option>
                    <option value="3M">Últimos 3 meses</option>
                    <option value="6M">Últimos 6 meses</option>
                    <option value="YEAR">Este año</option>
                    <option value="CUSTOM">Personalizado</option>
                </select>
                {periodPreset === 'CUSTOM' && <>
                    <input type="date" value={from} max={to} onChange={e => setFrom(e.target.value)}/>
                    <input type="date" value={to} min={from} onChange={e => setTo(e.target.value)}/>
                </>}
                <span>{rows.length} movimientos pendientes · {from} — {to}</span>
            </div>
            <button onClick={reclassify} disabled={busy}>Recategorizar histórico</button>
        </div>
        {message && <div className="classification-message">{message}</div>}
        {error && <div className="api-error">{error}</div>}
        <div className="card classification-list">
            {busy && !rows.length ? <div className="loading">Cargando clasificación…</div> :
                rows.length ? rows.map(m => {
                    const selected = choices[m.id] || treatmentDefaults(m.kind || 'NORMAL');
                    const definition = catalog.find(c => c.code === selected.category);
                    const treatment = TREATMENTS.find(t => t.code === (selected.kind || m.kind || 'NORMAL'));
                    return <div className="classification-row" key={m.id}>
                        <div className="classification-movement">
                            <b>{m.normalizedMerchant || m.merchant || m.description}</b>
                            <small>{dateLabel(m.date)} · {eur(m.amount)}</small>
                            <span>{m.description}</span>
                        </div>
                        <div className="classification-field">
                            <label>Tratamiento</label>
                            <select value={selected.kind || m.kind || 'NORMAL'}
                                    onChange={e => chooseTreatment(m.id, e.target.value)}>
                                {TREATMENTS.map(t => <option value={t.code} key={t.code}>{t.label}</option>)}
                            </select>
                            {treatment && !treatment.computable &&
                                <small className="non-computable">No se incluirá en gastos, ingresos ni ahorro.</small>}
                        </div>
                        <div className="classification-field">
                            <label>Categoría</label>
                            <select value={selected.category || ''}
                                    onChange={e => choose(m.id, 'category', e.target.value)}>
                                <option value="">Selecciona categoría</option>
                                
                                {catalog.map(c => <option value={c.code} key={c.code}>{c.label}</option>)}
                            </select>
                        </div>
                        <div className="classification-field">
                            <label>Subcategoría</label>
                            <select value={selected.subcategory || ''} disabled={!definition}
                                    onChange={e => choose(m.id, 'subcategory', e.target.value)}>
                                <option value="">Sin subcategoría</option>
                                {(definition?.subcategories || []).map(s =>
                                    <option value={s} key={s}>{s.replaceAll('_', ' ')}</option>)}
                            </select>
                        </div>
                        <div className="classification-actions">
                            <button disabled={!selected.category || busy} onClick={() => save(m, false)}>Solo este</button>
                            <button className="secondary" disabled={!selected.category || busy}
                                    onClick={() => save(m, true)}>Aplicar al comercio</button>
                        </div>
                    </div>
                }) : <Empty>No quedan movimientos pendientes en el período seleccionado.</Empty>}
        </div>
    </Page>
}


function CategoryAdministration() {
    const [categories, setCategories] = useState<AdminCategory[]>([]);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [newCategory, setNewCategory] = useState({code: '', name: '', active: true, displayOrder: 150});
    const [subcategoryModal, setSubcategoryModal] = useState<{category: AdminCategory; code: string; name: string; displayOrder: number} | null>(null);

    const load = () => api.adminCategories().then(setCategories).catch(e => setError(e instanceof Error ? e.message : 'No se pudo cargar el catálogo'));
    useEffect(() => { load() }, []);

    const saveCategory = async (category: AdminCategory) => {
        setError(''); setMessage('');
        try {
            await api.updateCategory(category.code, {code: category.code, name: category.name, active: category.active, displayOrder: category.displayOrder});
            setMessage('Categoría actualizada.'); load();
        } catch (e) { setError(e instanceof Error ? e.message : 'No se pudo actualizar'); }
    };

    const createCategory = async () => {
        if (!newCategory.code.trim() || !newCategory.name.trim()) return;
        setError(''); setMessage('');
        try {
            await api.createCategory(newCategory);
            setNewCategory({code: '', name: '', active: true, displayOrder: 150});
            setMessage('Categoría creada.'); load();
        } catch (e) { setError(e instanceof Error ? e.message : 'No se pudo crear'); }
    };

    const patchCategory = (code: string, patch: Partial<AdminCategory>) =>
        setCategories(current => current.map(c => c.code === code ? {...c, ...patch} : c));

    const patchSubcategory = (categoryCode: string, subCode: string, patch: any) =>
        setCategories(current => current.map(c => c.code !== categoryCode ? c : {...c, subcategories: c.subcategories.map(s => s.code === subCode ? {...s, ...patch} : s)}));

    const saveSubcategory = async (category: AdminCategory, sub: any) => {
        setError(''); setMessage('');
        try {
            await api.updateSubcategory(category.code, sub.code, {code: sub.code, name: sub.name, active: sub.active, displayOrder: sub.displayOrder});
            setMessage('Subcategoría actualizada.'); load();
        } catch (e) { setError(e instanceof Error ? e.message : 'No se pudo actualizar'); }
    };

    const openSubcategoryModal = (category: AdminCategory) => {
        setError(''); setMessage('');
        setSubcategoryModal({category, code: '', name: '', displayOrder: (category.subcategories.length + 1) * 10});
    };

    const createSubcategory = async () => {
        if (!subcategoryModal) return;
        const code = subcategoryModal.code.trim();
        const name = subcategoryModal.name.trim();
        if (!code || !name) {
            setError('El código y el nombre de la subcategoría son obligatorios.');
            return;
        }
        setError(''); setMessage('');
        try {
            await api.createSubcategory(subcategoryModal.category.code, {
                code,
                name,
                active: true,
                displayOrder: subcategoryModal.displayOrder
            });
            setSubcategoryModal(null);
            setMessage('Subcategoría creada.');
            load();
        } catch (e) { setError(e instanceof Error ? e.message : 'No se pudo crear la subcategoría'); }
    };

    return <Page title="Administración de categorías">
        <div className="card category-create">
            <input placeholder="CÓDIGO" value={newCategory.code} onChange={e => setNewCategory({...newCategory, code: e.target.value.toUpperCase().replaceAll(' ', '_')})}/>
            <input placeholder="Nombre" value={newCategory.name} onChange={e => setNewCategory({...newCategory, name: e.target.value})}/>
            <input type="number" min="0" value={newCategory.displayOrder} onChange={e => setNewCategory({...newCategory, displayOrder: Number(e.target.value)})}/>
            <button onClick={createCategory}>Nueva categoría</button>
        </div>
        {message && <div className="classification-message">{message}</div>}
        {error && <div className="api-error">{error}</div>}
        {subcategoryModal && <div className="app-modal-backdrop" role="presentation" onMouseDown={e => {
            if (e.target === e.currentTarget) setSubcategoryModal(null);
        }}>
            <div className="app-modal" role="dialog" aria-modal="true" aria-labelledby="subcategory-modal-title">
                <div className="app-modal-head">
                    <div><h3 id="subcategory-modal-title">Nueva subcategoría</h3><p>{subcategoryModal.category.name}</p></div>
                    <button type="button" className="modal-close" aria-label="Cerrar" onClick={() => setSubcategoryModal(null)}>×</button>
                </div>
                <div className="app-modal-body">
                    <label>Código
                        <input autoFocus placeholder="Ej. PLAN_PENSIONES" value={subcategoryModal.code}
                            onChange={e => {
                                const code = e.target.value.toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+/, '');
                                setSubcategoryModal({...subcategoryModal, code});
                            }}/>
                    </label>
                    <label>Nombre
                        <input placeholder="Nombre visible" value={subcategoryModal.name}
                            onChange={e => setSubcategoryModal({...subcategoryModal, name: e.target.value})}/>
                    </label>
                    <label>Orden
                        <input type="number" min="0" value={subcategoryModal.displayOrder}
                            onChange={e => setSubcategoryModal({...subcategoryModal, displayOrder: Number(e.target.value)})}/>
                    </label>
                </div>
                <div className="app-modal-actions">
                    <button type="button" className="secondary" onClick={() => setSubcategoryModal(null)}>Cancelar</button>
                    <button type="button" disabled={!subcategoryModal.code.trim() || !subcategoryModal.name.trim()} onClick={createSubcategory}>Crear subcategoría</button>
                </div>
            </div>
        </div>}
        <div className="category-admin-list">{categories.map(category =>
            <div className="card category-admin" key={category.code}>
                <div className="category-admin-head">
                    <code>{category.code}</code>
                    <input value={category.name} onChange={e => patchCategory(category.code, {name: e.target.value})}/>
                    <input className="order-input" type="number" min="0" value={category.displayOrder} onChange={e => patchCategory(category.code, {displayOrder: Number(e.target.value)})}/>
                    <label><input type="checkbox" checked={category.active} onChange={e => patchCategory(category.code, {active: e.target.checked})}/> Activa</label>
                    <button onClick={() => saveCategory(category)}>Guardar</button>
                    <button className="secondary" onClick={() => openSubcategoryModal(category)}>+ Subcategoría</button>
                </div>
                <div className="subcategory-admin">
                    {category.subcategories.map(sub => <div className="subcategory-admin-row" key={sub.code}>
                        <code>{sub.code}</code>
                        <input value={sub.name} onChange={e => patchSubcategory(category.code, sub.code, {name: e.target.value})}/>
                        <input className="order-input" type="number" min="0" value={sub.displayOrder} onChange={e => patchSubcategory(category.code, sub.code, {displayOrder: Number(e.target.value)})}/>
                        <label><input type="checkbox" checked={sub.active} onChange={e => patchSubcategory(category.code, sub.code, {active: e.target.checked})}/> Activa</label>
                        <button onClick={() => saveSubcategory(category, sub)}>Guardar</button>
                    </div>)}
                </div>
            </div>)}</div>
    </Page>;
}

function Page({title, children}: { title: string; children: any }) {
    return <section className="page">
        <div className="section-head"><h2>{title}</h2></div>
        {children}</section>
}

function Rank({title, rows}: { title: string; rows: [string, number, number, number][] }) {
    return <div className="card"><Title t={title}/>{rows.length ? rows.map(([name, amount, ops, share]) => <div
        className="rank" key={name}>
        <span><b>{name}</b><small>{ops} operaciones · {Number(share).toFixed(1)} %</small></span><strong>{eur(amount)}</strong>
    </div>) : <Empty>Sin datos.</Empty>}</div>
}

function MovementSearch({products, initialFrom, initialTo, canEdit}: { products: Product[]; initialFrom: string; initialTo: string; canEdit: boolean }) {
    const [filters, setFilters] = useState<any>({from: initialFrom, to: initialTo, sortBy: 'DATE', sortDirection: 'DESC', offset: 0, limit: 25});
    const [page, setPage] = useState<any>({items: [], total: 0, offset: 0, limit: 25});
    const [catalog, setCatalog] = useState<CategoryDefinition[]>([]);
    const [deleting, setDeleting] = useState<Movement | null>(null);
    const [editing, setEditing] = useState<string | null>(null);
    const [choice, setChoice] = useState<ClassificationChoice>({});
    const [busy, setBusy] = useState(false), [err, setErr] = useState('');

    const load = (next = filters) => {
        setBusy(true); setErr('');
        api.searchMovements(next).then(setPage).catch(e => setErr(e instanceof Error ? e.message : 'Error de búsqueda')).finally(() => setBusy(false));
    };
    useEffect(() => { load(filters); api.categoriesCatalog().then(setCatalog).catch(() => undefined) }, []);
    useEffect(() => {
        const next = {...filters, from: initialFrom, to: initialTo, offset: 0};
        setFilters(next); load(next);
    }, [initialFrom, initialTo]);

    const change = (k: string, v: any) => setFilters((x: any) => ({...x, [k]: v, offset: 0}));
    const submit = (e: any) => { e.preventDefault(); load(filters) };
    const move = (offset: number) => { const next = {...filters, offset}; setFilters(next); load(next) };
    const edit = (m: Movement) => {
        setEditing(m.id);
        setChoice({category: m.category || '', subcategory: m.subcategory || '', kind: m.kind || 'NORMAL'});
    };
    const saveClassification = async (m: Movement) => {
        if (!choice.category) { setErr('Selecciona una categoría.'); return; }
        setBusy(true); setErr('');
        try {
            await api.classifyMovement(m.id, {category: choice.category, subcategory: choice.subcategory, kind: choice.kind || m.kind || 'NORMAL', createRule: false, applyToSimilar: false});
            setEditing(null);
            load(filters);
        } catch (e) {
            setBusy(false);
            setErr(e instanceof Error ? e.message : 'No se pudo actualizar la categoría');
        }
    };
    const deleteMovement = async () => {
        if (!deleting) return;
        setBusy(true); setErr('');
        try {
            await api.deleteMovement(deleting.id);
            setDeleting(null); setEditing(null);
            const next = {...filters, offset: page.items.length === 1 ? Math.max(0, page.offset - page.limit) : page.offset};
            setFilters(next); load(next);
        } catch (e) {
            setBusy(false);
            setErr(e instanceof Error ? e.message : 'No se pudo eliminar el movimiento');
        }
    };
    const selectedDefinition = catalog.find(c => c.code === choice.category);

    return <Page title="Movimientos">
        {deleting && <div className="app-modal-backdrop">
            <div className="app-modal" role="dialog" aria-modal="true" aria-labelledby="delete-movement-title">
                <h3 id="delete-movement-title">Eliminar movimiento</h3>
                <p>{dateLabel(deleting.date)} · {deleting.merchant || deleting.description} · {eur(deleting.amount)}</p>
                <p>Se eliminará definitivamente. Una nueva importación o sincronización puede volver a incorporarlo.</p>
                <div className="modal-actions">
                    <button type="button" className="secondary" disabled={busy} onClick={() => setDeleting(null)}>Cancelar</button>
                    <button type="button" disabled={busy} onClick={() => void deleteMovement()}>Eliminar definitivamente</button>
                </div>
            </div>
        </div>}
        <form className="filters card" onSubmit={submit}>
            <label>Desde<input type="date" value={filters.from} onChange={e => change('from', e.target.value)}/></label>
            <label>Hasta<input type="date" value={filters.to} onChange={e => change('to', e.target.value)}/></label>
            <input placeholder="Buscar concepto o comercio" value={filters.text || ''} onChange={e => change('text', e.target.value)}/>
            <select value={filters.productId || ''} onChange={e => change('productId', e.target.value)}><option value="">Todos los productos</option>{products.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select>
            <input placeholder="Categoría" value={filters.category || ''} onChange={e => change('category', e.target.value)}/>
            <input type="number" step="0.01" placeholder="Importe mín." value={filters.minAmount ?? ''} onChange={e => change('minAmount', e.target.value)}/>
            <input type="number" step="0.01" placeholder="Importe máx." value={filters.maxAmount ?? ''} onChange={e => change('maxAmount', e.target.value)}/>
            <select value={filters.status || ''} onChange={e => change('status', e.target.value)}><option value="">Todos los estados</option><option value="BOOKED">Contabilizado</option><option value="PENDING">Pendiente</option></select>
            <select value={filters.sortBy} onChange={e => change('sortBy', e.target.value)}><option value="DATE">Ordenar por fecha</option><option value="AMOUNT">Ordenar por importe</option><option value="MERCHANT">Ordenar por comercio</option><option value="CATEGORY">Ordenar por categoría</option></select>
            <select value={filters.sortDirection} onChange={e => change('sortDirection', e.target.value)}><option value="DESC">Descendente</option><option value="ASC">Ascendente</option></select>
            <button type="submit"><Search size={16}/>Buscar</button>
            <button type="button" className="secondary" onClick={() => api.exportMovements(filters)}>Exportar CSV</button>
        </form>
        {err && <div className="api-error">{err}</div>}
        <div className="card table">{busy && !page.items.length ? <div className="loading">Buscando movimientos…</div> : <>
            <div className="thead movement-grid editable-movement-grid"><b>Fecha</b><b>Concepto</b><b>Categoría</b><b>Importe</b>{canEdit && <b>Acción</b>}</div>
            {page.items.length ? page.items.map((m: Movement) => <div key={m.id}>
                <div className="trow movement-grid editable-movement-grid">
                    <span>{dateLabel(m.date)}</span><b>{m.merchant || m.description}</b>
                    <span>{m.category || 'Sin categoría'}{m.subcategory ? ' / ' + m.subcategory.replaceAll('_', ' ') : ''}</span>
                    <strong className={m.amount >= 0 ? 'pos' : 'neg'}>{eur(m.amount)}</strong>
                    {canEdit && <div><button type="button" disabled={busy} className="secondary movement-edit" onClick={() => editing === m.id ? setEditing(null) : edit(m)}>{editing === m.id ? 'Cancelar' : 'Cambiar'}</button><button type="button" disabled={busy} className="secondary" onClick={() => setDeleting(m)}>Eliminar</button></div>}
                </div>
                {canEdit && editing === m.id && <div className="movement-classification-editor">
                    <label>Categoría<select value={choice.category || ''} onChange={e => setChoice({category: e.target.value, subcategory: '', kind: e.target.value === 'NO_COMPUTABLE' ? 'NON_COMPUTABLE' : (choice.kind === 'REFUND' ? 'REFUND' : 'NORMAL')})}><option value="">Selecciona categoría</option>{catalog.map(c => <option key={c.code} value={c.code}>{c.label}</option>)}</select></label>
                    <label>Subcategoría<select disabled={!choice.category} value={choice.subcategory || ''} onChange={e => setChoice({...choice, subcategory: e.target.value, kind: ({LIQUIDACION_TARJETA: 'CARD_SETTLEMENT', LIQUIDACION_PAYPAL: 'WALLET_SETTLEMENT', TRASPASO_INTERNO: 'INTERNAL_TRANSFER', MOVIMIENTO_DUPLICADO: 'DUPLICATE', OTROS_NO_COMPUTABLES: 'NON_COMPUTABLE'} as Record<string, string>)[e.target.value] || choice.kind})}><option value="">Sin subcategoría</option>{(selectedDefinition?.subcategories || []).map(s => <option key={s} value={s}>{s.replaceAll('_', ' ')}</option>)}</select></label>
                    <label>Tratamiento<select value={choice.kind || 'NORMAL'} onChange={e => setChoice({...treatmentDefaults(e.target.value), ...(e.target.value === 'NORMAL' || e.target.value === 'REFUND' ? {category: choice.category === 'NO_COMPUTABLE' ? '' : choice.category, subcategory: choice.category === 'NO_COMPUTABLE' ? undefined : choice.subcategory} : {})})}>{TREATMENTS.map(t => <option key={t.code} value={t.code}>{t.label}</option>)}</select></label>
                    <button type="button" disabled={!choice.category || busy} onClick={() => saveClassification(m)}>Guardar</button>
                </div>}
            </div>) : <Empty>No hay movimientos con esos filtros.</Empty>}
            <div className="pagination"><span>{page.total} movimientos</span><div><button disabled={page.offset <= 0} onClick={() => move(Math.max(0, page.offset - page.limit))}>Anterior</button><button disabled={page.offset + page.limit >= page.total} onClick={() => move(page.offset + page.limit)}>Siguiente</button></div></div>
        </>}</div>
    </Page>
}

function ProductCreateButton({products, onCreated}: {products: Product[]; onCreated: () => void}) {
    const [open, setOpen] = useState(false);
    return <><button type="button" onClick={() => setOpen(true)}>Nuevo producto</button>
        {open && <ProductCreate products={products} onCancel={() => setOpen(false)} onCreated={() => {setOpen(false); onCreated();}}/>}
    </>;
}
