import {useEffect, useId, useRef, type ReactNode} from 'react';
import {createPortal} from 'react-dom';

export default function MovementDialog({title, busy = false, onClose, children}: {
    title: string; busy?: boolean; onClose: () => void; children: ReactNode;
}) {
    const titleId = useId();
    const panel = useRef<HTMLElement>(null);
    const current = useRef({busy, onClose});
    current.current = {busy, onClose};
    useEffect(() => {
        const previous = document.activeElement as HTMLElement | null;
        const overflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        panel.current?.focus();
        const onKey = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !current.current.busy) {
                event.preventDefault(); current.current.onClose();
            }
            if (event.key !== 'Tab') return;
            const controls = Array.from(panel.current?.querySelectorAll<HTMLElement>(
                'button:not(:disabled), input:not(:disabled), select:not(:disabled), [tabindex="0"]') || []);
            const first = controls[0], last = controls.at(-1);
            if (!first) {event.preventDefault(); panel.current?.focus(); return;}
            if (event.shiftKey && (document.activeElement === first || document.activeElement === panel.current)) {
                event.preventDefault(); last?.focus();
            } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === panel.current)) {
                event.preventDefault(); first.focus();
            }
        };
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('keydown', onKey);
            document.body.style.overflow = overflow;
            if (previous?.isConnected) previous.focus({preventScroll: true});
        };
    }, []);
    return createPortal(<div className="app-modal-backdrop" onMouseDown={event => {
        if (event.target === event.currentTarget && !busy) onClose();
    }}>
        <section ref={panel} tabIndex={-1} className="app-modal movement-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} aria-busy={busy}>
            <header className="app-modal-head">
                <h3 id={titleId}>{title}</h3>
                <button type="button" className="modal-close" aria-label="Cerrar" disabled={busy} onClick={onClose}>×</button>
            </header>
            <div className="app-modal-body">{children}</div>
        </section>
    </div>, document.body);
}
