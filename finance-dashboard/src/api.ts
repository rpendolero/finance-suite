export type BankConnection = {id: string; bankName: string; country: string; status: string; validUntil?: string; lastSyncAt?: string};
export type BankAccount = {id: string; externalAccountId: string; productId?: string; name: string; currency: string; cashAccountType?: string};
export type BankSyncResult = {read: number; inserted: number; duplicates: number; balancesUpdated: number; balancesSkipped: number};
export type UserSession = { username: string; roles: string[] };
export type Product = {
    id: string;
    name: string;
    type: string;
    currency: string;
    balance: number;
    provider: string;
    maskedPan?: string
};
export type ProductInput = Product & {
    balanceAt: string;
    linkedAccountId?: string;
    creditLimit?: number;
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
export type AdminSubcategory = { id: number; code: string; name: string; active: boolean; displayOrder: number };
export type AdminCategory = { id: number; code: string; name: string; active: boolean; displayOrder: number; subcategories: AdminSubcategory[] };
export type CatalogItemInput = { code: string; name: string; active: boolean; displayOrder: number };

export type ReclassificationResult = {
    scanned: number;
    updated: number;
    unclassified: number
};
export type ClassificationResult = {movement: Movement; reclassified: number};
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
export type FlowDirection = 'EXPENSE' | 'INCOME';
export type FinancialFlow = {
    direction: FlowDirection;
    total: number;
    operations: number;
    categories: CategoryStat[];
    counterparties: MerchantStat[];
    movements: Movement[];
    offset: number;
    limit: number;
};
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

const csrfToken = () => document.cookie
    .split('; ')
    .find(cookie => cookie.startsWith('XSRF-TOKEN='))
    ?.split('=').slice(1).join('=');

const withCsrf = (headers: HeadersInit = {}): HeadersInit => {
    const token = csrfToken();
    return token ? {...headers, 'X-XSRF-TOKEN': decodeURIComponent(token)} : headers;
};

const json = async <T>(url: string, init: RequestInit = {}): Promise<T> => {
    const method = (init.method || 'GET').toUpperCase();
    const headers = ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method) ? withCsrf(init.headers) : init.headers;
    const r = await fetch(url, {...init, headers, credentials: 'include'});
    if (!r.ok) {
        const problem = await r.json().catch(() => null);
        throw new Error(typeof problem?.detail === 'string' ? problem.detail : `API ${r.status}: ${r.statusText}`);
    }
    return r.status === 204 ? undefined as T : r.json()
};
const sendJson = async <T>(url: string, method: 'POST' | 'PUT' | 'PATCH', body?: unknown): Promise<T> => {
    const r = await fetch(url, {
        method,
        credentials: 'include',
        headers: withCsrf(body === undefined ? {} : {'Content-Type': 'application/json'}),
        body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (!r.ok) {
        const problem = await r.json().catch(() => null);
        throw new Error(typeof problem?.detail === 'string' ? problem.detail : `API ${r.status}: ${r.statusText}`);
    }
    return r.status === 204 ? undefined as T : r.json()
};
const q = (from: string, to: string, productId?: string) => `from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}${productId ? `&productId=${encodeURIComponent(productId)}` : ''}`;

export const api = {
    banks: () => json<string[]>('/api/v1/banking/enable-banking/banks'),
    bankConnections: () => json<BankConnection[]>('/api/v1/banking/connections'),
    authorizeBank: (bankName: string) => sendJson<{connectionId: string; authorizationUrl: string}>('/api/v1/banking/enable-banking/authorizations', 'POST', {bankName}),
    bankAccounts: (id: string) => json<BankAccount[]>(`/api/v1/banking/connections/${encodeURIComponent(id)}/accounts`),
    discoverBankAccounts: (id: string) => sendJson<BankAccount[]>(`/api/v1/banking/connections/${encodeURIComponent(id)}/discover`, 'POST'),
    linkBankAccount: (id: string, externalAccountId: string, productId: string) => sendJson<BankAccount>(`/api/v1/banking/connections/${encodeURIComponent(id)}/accounts/link`, 'POST', {externalAccountId, productId}),
    syncBank: (id: string) => sendJson<BankSyncResult>(`/api/v1/banking/connections/${encodeURIComponent(id)}/sync`, 'POST'),
    login: (username: string, password: string) => json<UserSession>('/api/auth/login', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({username, password})}),
    me: () => json<UserSession>('/api/auth/me'),
    logout: () => json<void>('/api/auth/logout', {method: 'POST'}),
    products: () => json<Product[]>('/api/products'),
    saveProduct: (product: ProductInput) => sendJson<Product>(`/api/products/${encodeURIComponent(product.id)}`, 'PUT', product),
    categoriesCatalog: () => json<CategoryDefinition[]>('/api/categories'),
    adminCategories: () => json<AdminCategory[]>('/api/admin/categories'),
    createCategory: (item: CatalogItemInput) => sendJson<AdminCategory>('/api/admin/categories', 'POST', item),
    updateCategory: (code: string, item: CatalogItemInput) => sendJson<AdminCategory>(`/api/admin/categories/${encodeURIComponent(code)}`, 'PUT', item),
    createSubcategory: (category: string, item: CatalogItemInput) => sendJson<AdminSubcategory>(`/api/admin/categories/${encodeURIComponent(category)}/subcategories`, 'POST', item),
    updateSubcategory: (category: string, code: string, item: CatalogItemInput) => sendJson<AdminSubcategory>(`/api/admin/categories/${encodeURIComponent(category)}/subcategories/${encodeURIComponent(code)}`, 'PUT', item),
    unclassified: (from: string, to: string, productId?: string, limit = 100) =>
        json<Movement[]>(`/api/classification/unclassified?${q(from, to, productId)}&limit=${limit}`),
    deleteMovement: (id: string) => json<void>(`/api/movements/${encodeURIComponent(id)}`, {method: 'DELETE'}),
    classifyMovement: (
        id: string,
        classification: {
            category: string;
            subcategory?: string;
            kind: string;
            createRule: boolean;
            applyToSimilar: boolean
        }) => sendJson<ClassificationResult>(`/api/movements/${encodeURIComponent(id)}/classification`, 'PATCH', classification),
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
    financialFlow: (from: string, to: string, direction: FlowDirection, offset = 0, limit = 25, category?: string) =>
        json<FinancialFlow>(`/api/dashboard/flows?${q(from, to)}&direction=${direction}&offset=${offset}&limit=${limit}${category !== undefined ? `&category=${encodeURIComponent(category)}` : ''}`),
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
