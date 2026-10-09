import {useEffect, useRef, useState} from 'react';
import {Upload} from 'lucide-react';
import ProductCreate from './ProductCreate';
import {api, ImportResult, Product, StatementFormat} from './api';

export default function StatementImport({products, onImported, onProductCreated}: {
    products: Product[];
    onImported: () => void;
    onProductCreated: () => void;
}) {
    const [productId, setProductId] = useState(products[0]?.id || '');
    const [createdProduct, setCreatedProduct] = useState<Product | null>(null);
    const [creating, setCreating] = useState(false);
    const [formats, setFormats] = useState<StatementFormat[]>([]);
    const [formatId, setFormatId] = useState('');
    const [file, setFile] = useState<File | null>(null);
    const [loadingFormats, setLoadingFormats] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [result, setResult] = useState<ImportResult | null>(null);
    const fileInput = useRef<HTMLInputElement>(null);
    const availableProducts = createdProduct && !products.some(product => product.id === createdProduct.id)
        ? [...products, createdProduct] : products;
    const selectedFormat = formats.find(format => format.id === formatId);

    useEffect(() => {
        if (!productId && products.length) setProductId(products[0].id);
    }, [products, productId]);

    useEffect(() => {
        let active = true;
        setFormats([]); setFormatId(''); setFile(null); setResult(null); setError('');
        if (fileInput.current) fileInput.current.value = '';
        if (!productId) return;
        setLoadingFormats(true);
        api.importFormats(productId).then(items => {
            if (!active) return;
            setFormats(items);
            // Prefer the bank's native layout; CSV is always available as a fallback.
            setFormatId((items.find(item => item.id !== 'CSV') || items[0])?.id || '');
        }).catch(failure => {
            if (active) setError(failure instanceof Error ? failure.message : 'No se pudieron cargar los formatos');
        }).finally(() => { if (active) setLoadingFormats(false); });
        return () => { active = false; };
    }, [productId]);

    const submit = async (event: React.FormEvent) => {
        event.preventDefault(); setError(''); setResult(null);
        if (!productId || !selectedFormat || !file) return;
        if (file.size === 0) { setError('El fichero está vacío.'); return; }
        if (file.size > 10 * 1024 * 1024) { setError('Máximo 10 MB por fichero.'); return; }
        if (!file.name.toLowerCase().endsWith(selectedFormat.extension)) {
            setError(`Selecciona un fichero ${selectedFormat.extension} para este formato.`); return;
        }
        setBusy(true);
        try {
            const imported = await api.importStatement(productId, formatId, file);
            setResult(imported); setFile(null);
            if (fileInput.current) fileInput.current.value = '';
            onImported();
        } catch (failure) {
            setError(failure instanceof Error ? failure.message : 'No se pudieron importar los movimientos');
        } finally { setBusy(false); }
    };

    return <div className="statement-import">
        <section className="card">
            <div className="title"><h2>Importar movimientos</h2><button className="action" disabled={busy} onClick={() => setCreating(true)}>Nuevo producto</button></div>
            <p className="muted">Selecciona la cuenta o tarjeta y sube el extracto descargado de tu banco. Se importan todas las fechas del fichero.</p>
            {!availableProducts.length && <p className="empty">Crea primero el producto al que pertenecen los movimientos.</p>}
            <form className="statement-import-form" onSubmit={submit}>
                <label>Producto<select required disabled={busy || !availableProducts.length} value={productId} onChange={event => setProductId(event.target.value)}>
                    <option value="">Selecciona un producto</option>
                    {availableProducts.map(product => <option key={product.id} value={product.id}>{product.name} · {product.provider}{product.maskedPan ? ` · ${product.maskedPan}` : ''}</option>)}
                </select></label>
                <label>Formato del fichero<select required disabled={busy || loadingFormats || !formats.length} value={formatId} onChange={event => {
                    setFormatId(event.target.value); setFile(null); setResult(null); setError('');
                    if (fileInput.current) fileInput.current.value = '';
                }}>
                    <option value="">{loadingFormats ? 'Cargando formatos…' : 'Selecciona un formato'}</option>
                    {formats.map(format => <option key={format.id} value={format.id}>{format.label}</option>)}
                </select></label>
                <label>Extracto bancario<input ref={fileInput} type="file" required accept={selectedFormat?.extension || '.xls,.csv'} disabled={busy || !selectedFormat} onChange={event => {
                    setFile(event.target.files?.[0] || null); setResult(null); setError('');
                }}/><small className="muted">Máximo 10 MB · Hasta 50.000 movimientos</small></label>
                {error && <p role="alert" className="api-error">{error}</p>}
                <button className="action" type="submit" disabled={busy || loadingFormats || !file || !selectedFormat || !productId}><Upload size={17}/>{busy ? 'Importando…' : 'Importar movimientos'}</button>
            </form>
            <p className="muted">Se aplican tus reglas de categorización y se omiten los movimientos cuyo identificador ya existe en este producto.</p>
            {formatId === 'CSV' && <details className="statement-csv-help"><summary>Cómo preparar el CSV normalizado</summary>
                <p>Usa UTF-8 y separa las columnas con punto y coma. Mantén el mismo external_id al repetir una importación. Los ingresos llevan importe positivo y los gastos negativo.</p>
                <pre>external_id;date;amount;currency;description;merchant;category;kind;status{'\n'}compra-001;2026-10-09;-12.50;EUR;Compra;Comercio;UNCLASSIFIED;NORMAL;BOOKED</pre>
                <p>El formato CSV está disponible para todos los productos. PayPal todavía no dispone de un formato de fichero nativo.</p>
            </details>}
        </section>
        {result && <section className="card statement-import-result" role="status" aria-live="polite">
            <h3>Importación completada</h3>
            <dl><div><dt>Leídos</dt><dd>{result.read}</dd></div><div><dt>Importados</dt><dd>{result.inserted}</dd></div><div><dt>Duplicados omitidos</dt><dd>{result.duplicates}</dd></div></dl>
            <p className="muted">Puedes consultar los datos en Movimientos y completar las categorías pendientes en Revisar.</p>
        </section>}
        {creating && <ProductCreate products={availableProducts} onCancel={() => setCreating(false)} onCreated={product => {
            setCreatedProduct(product); setProductId(product.id); setCreating(false); onProductCreated();
        }}/>}
    </div>;
}
