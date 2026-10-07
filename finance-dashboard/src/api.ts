export type Product = {
    id: string;
    name: string;
    type: string;
    currency: string;
    balance: number;
    provider: string;
    maskedPan?: string
};
export type Movement = {
    id: string;
    productId: string;
    date: string;
    amount: number;
    description: string;
    merchant?: string;
    normalizedMerchant?: string;
    category?: string;
    subcategory?: string;
    kind: string;
    classificationSource?: string;
    classificationConfidence?: number
};
export type CategoryDefinition = {
    code: string;
    label: string;
    subcategories: string[]
};
export type ReclassificationResult = {
    scanned: number;
    updated: number;
    unclassified: number
};
export type Overview = {
    totalBalance: number;
    income: number;
    expenses: number;
    savings: number;
    savingsRate: number;
    averageDailyExpense: number;
    categories: CategoryStat[];
    merchants: MerchantStat[]
};
export type TrendPoint = { date: string; income: number; expenses: number; savings: number };
export type CategoryStat = { category: string; amount: number; operations: number; average: number; share: number };
export type MerchantStat = { merchant: string; amount: number; operations: number; average: number; share: number };
export type ProductStat = {
    productId: string;
    name: string;
    type: string;
    provider: string;
    balance: number;
    expenses: number;
    operations: number;
    average: number
};
export type CalendarDay = { date: string; income: number; expenses: number; net: number; operations: number };
export type Insight = {
    severity: 'POSITIVE' | 'WARNING' | 'INFO' | string;
    code: string;
    title: string;
    detail: string;
    value: number
};
export type Forecast = { projectedBalance?: number; projectedExpenses?: number; [key: string]: unknown };
export type Recurring = {
    merchant?: string;
    description?: string;
    amount?: number;
    category?: string;
    [key: string]: unknown
};
export type MovementPage = { items: Movement[]; total: number; offset: number; limit: number };
export type MovementFilters = {
    from: string;
    to: string;
    productId?: string;
    category?: string;
    merchant?: string;
    text?: string;
    minAmount?: number;
    maxAmount?: number;
    kind?: string;
    status?: string;
    sortBy?: 'DATE' | 'AMOUNT' | 'MERCHANT' | 'CATEGORY';
    sortDirection?: 'ASC' | 'DESC';
    offset?: number;
    limit?: number
};
export type Anomaly = {
    merchant?: string;
    description?: string;
    amount?: number;
    category?: string;
    [key: string]: unknown
};

const json = async <T>(url: string): Promise<T> => {
    const r = await fetch(url, {credentials: 'include'});
    if (!r.ok) throw new Error(`API ${r.status}: ${r.statusText}`);
    return r.json()
};
const sendJson = async <T>(url: string, method: 'POST' | 'PATCH', body?: unknown): Promise<T> => {
    const r = await fetch(url, {
        method,
        credentials: 'include',
        headers: body === undefined ? undefined : {'Content-Type': 'application/json'},
        body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (!r.ok) throw new Error(`API ${r.status}: ${r.statusText}`);
    return r.status === 204 ? undefined as T : r.json()
};
const q = (from: string, to: string, productId?: string) => `from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}${productId ? `&productId=${encodeURIComponent(productId)}` : ''}`;

export const api = {
    products: () => json<Product[]>('/api/products'),
    categoriesCatalog: () => json<CategoryDefinition[]>('/api/categories'),
    unclassified: (from: string, to: string, productId?: string, limit = 100) =>
        json<Movement[]>(`/api/classification/unclassified?${q(from, to, productId)}&limit=${limit}`),
    classifyMovement: (
        id: string,
        classification: {
            category: string;
            subcategory?: string;
            kind: string;
            createRule: boolean;
            applyToSimilar: boolean
        }) => sendJson<unknown>(`/api/movements/${encodeURIComponent(id)}/classification`, 'PATCH', classification),
    reclassify: () => sendJson<ReclassificationResult>('/api/classification/reclassify', 'POST'),
    movements: (from: string, to: string, productId?: string, limit = 10) => json<Movement[]>(`/api/movements?${q(from, to, productId)}&limit=${limit}`),
    exportMovements: (f: MovementFilters) => {
        const p = new URLSearchParams();
        Object.entries(f).forEach(([k, v]) => {
            if (k !== 'offset' && k !== 'limit' && v !== undefined && v !== '' && v !== null) p.set(k, String(v))
        });
        window.location.href = `/api/movements/export?${p}`
    },
    searchMovements: (f: MovementFilters) => {
        const p = new URLSearchParams();
        Object.entries(f).forEach(([k, v]) => {
            if (v !== undefined && v !== '' && v !== null) p.set(k, String(v))
        });
        return json<MovementPage>(`/api/movements/search?${p}`)
    },
    overview: (from: string, to: string, productId?: string) => json<Overview>(`/api/dashboard/overview?${q(from, to, productId)}`),
    trend: (from: string, to: string, productId?: string, groupBy = 'DAY') => json<TrendPoint[]>(`/api/dashboard/trend?${q(from, to, productId)}&groupBy=${groupBy}`),
    categories: (from: string, to: string, productId?: string) => json<CategoryStat[]>(`/api/dashboard/categories?${q(from, to, productId)}`),
    merchants: (from: string, to: string, productId?: string, limit = 10) => json<MerchantStat[]>(`/api/dashboard/merchants?${q(from, to, productId)}&limit=${limit}`),
    productStats: (from: string, to: string) => json<ProductStat[]>(`/api/dashboard/products?${q(from, to)}`),
    calendar: (from: string, to: string, productId?: string) => json<CalendarDay[]>(`/api/dashboard/calendar?${q(from, to, productId)}`),
    insights: (from: string, to: string, productId?: string) => json<Insight[]>(`/api/dashboard/insights?${q(from, to, productId)}`),
    recurring: (from: string, to: string) => json<Recurring[]>(`/api/analysis/recurring?${q(from, to)}`),
    anomalies: (from: string, to: string) => json<Anomaly[]>(`/api/analysis/anomalies?${q(from, to)}`),
    forecast: (from: string, to: string) => json<Forecast>(`/api/analysis/forecast?${q(from, to)}&days=30`)
};