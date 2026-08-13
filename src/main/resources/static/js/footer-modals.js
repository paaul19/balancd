document.addEventListener('DOMContentLoaded', function() {
    // Botón del footer
    const footerBtn = document.querySelector('.footer-btn.center');
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // Función para obtener mes y año seleccionados
    function getMesAnioSeleccionados() {
        const currentMonthElem = document.querySelector('.current-month');
        if (!currentMonthElem) return { mes: (new Date().getMonth() + 1), anio: (new Date().getFullYear()) };

        const texto = currentMonthElem.textContent.trim();
        const partes = texto.split(' ');
        if (partes.length < 2) return { mes: (new Date().getMonth() + 1), anio: (new Date().getFullYear()) };

        const meses = ['enero','febrero','marzo','abril','mayo','junio','julio','agosto','septiembre','octubre','noviembre','diciembre'];
        const mes = meses.indexOf(partes[0].toLowerCase()) + 1;
        const anio = parseInt(partes[1]);
        return { mes, anio };
    }

    // Barra de arrastre para descartar el sheet con gesto (solo si el usuario no pide reducir movimiento)
    const SHEET_HANDLE_HTML = '<div class="sheet-handle" aria-hidden="true"><span></span></div>';

    /**
     * Inserta un modal en el DOM y lo muestra con la transición de "materializar".
     * Además engancha el gesto de arrastrar hacia abajo para descartar (1:1, con
     * rubber-banding y hand-off de velocidad a un spring, interrumpible en todo momento).
     */
    function presentModal(modal, onDismiss) {
        document.body.appendChild(modal);
        // Fuerza un reflow para que el navegador no colapse el estado inicial con el final
        void modal.offsetHeight;
        modal.classList.add('show');
        attachSheetDrag(modal, onDismiss);
        return modal;
    }

    /** Cierra un modal con la misma transición (invertida) con la que apareció. */
    function dismissModal(modal, onDone) {
        if (!modal || modal.dataset.dismissing === '1') return;
        modal.dataset.dismissing = '1';
        modal.classList.remove('show');
        // Si el cierre viene de un gesto de arrastre, el contenido ya quedó fuera
        // de pantalla con su propia transform inline: no lo tocamos para no
        // provocar un salto visible de vuelta al centro antes de desaparecer.
        const cleanup = () => {
            if (modal.parentNode) modal.parentNode.removeChild(modal);
            if (onDone) onDone();
        };
        if (prefersReducedMotion) {
            cleanup();
            return;
        }
        let done = false;
        const finish = () => { if (!done) { done = true; cleanup(); } };
        modal.addEventListener('transitionend', finish, { once: true });
        setTimeout(finish, 360); // red de seguridad si transitionend no llega
    }

    /** Gesto de arrastre 1:1 con rubber-banding y hand-off de velocidad a un spring. */
    function attachSheetDrag(modal, onDismiss) {
        if (prefersReducedMotion || !window.Spring) return;
        const content = modal.querySelector('.modal-content');
        const handle = modal.querySelector('.sheet-handle');
        if (!content || !handle) return;

        let dragging = false;
        let startY = 0;
        let currentY = 0;
        let lastY = 0;
        let lastT = 0;
        let velocity = 0;
        const spring = new Spring(0);

        const setY = (y) => { content.style.transform = y ? `translateY(${y}px)` : ''; };

        handle.addEventListener('pointerdown', (e) => {
            dragging = true;
            spring.stop();
            handle.setPointerCapture(e.pointerId);
            startY = currentY = lastY = e.clientY;
            lastT = performance.now();
            velocity = 0;
            content.style.transition = 'none';
        });

        handle.addEventListener('pointermove', (e) => {
            if (!dragging) return;
            const now = performance.now();
            const dy = e.clientY - startY;
            const y = dy < 0 ? rubberband(dy, content.offsetHeight || 400) : dy;
            currentY = y;
            setY(y);
            const dt = now - lastT;
            if (dt > 0) velocity = ((e.clientY - lastY) / dt) * 1000; // px/s
            lastY = e.clientY;
            lastT = now;
        });

        function endDrag(commit) {
            dragging = false;
            // La transición CSS se mantiene desactivada mientras el spring escribe
            // transform por frame: si no, cada frame heredaría la transición larga
            // del stylesheet y el gesto se vería con retraso ("efecto pantano").
            const distance = content.offsetHeight || 400;
            if (commit) {
                spring.set(currentY);
                spring.to(distance + 80, {
                    velocity,
                    onUpdate: (v) => setY(v),
                    onSettle: () => { if (onDismiss) onDismiss(); }
                });
            } else {
                spring.set(currentY);
                spring.to(0, {
                    velocity,
                    onUpdate: (v) => setY(v),
                    onSettle: () => {
                        content.style.transform = '';
                        content.style.transition = '';
                    }
                });
            }
        }

        handle.addEventListener('pointerup', () => {
            if (!dragging) return;
            const distance = content.offsetHeight || 400;
            const pastThreshold = currentY > distance * 0.28;
            const flungDown = velocity > 700;
            endDrag(pastThreshold || flungDown);
        });

        handle.addEventListener('pointercancel', () => {
            if (!dragging) return;
            endDrag(false);
        });
    }

    // Crear modal principal
    function createMainModal() {
        const modal = document.createElement('div');
        modal.id = 'mainModal';
        modal.className = 'modal';
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.innerHTML = `
            <div class="modal-content" style="max-width: 350px;">
                ${SHEET_HANDLE_HTML}
                <div class="modal-header">
                    <h3 class="modal-title">Añadir movimiento</h3>
                    <button type="button" class="modal-close" id="closeMainModal" aria-label="Cerrar">&times;</button>
                </div>
                <div class="modal-body">
                    <div class="modal-options">
                        <button type="button" class="modal-option" id="optionIngreso">
                            <span class="modal-option-icon modal-option-icon--income">
                                <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            </span>
                            <span class="modal-option-label">Añadir ingreso</span>
                        </button>
                        <button type="button" class="modal-option" id="optionGasto">
                            <span class="modal-option-icon modal-option-icon--expense">
                                <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M5 12h14" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            </span>
                            <span class="modal-option-label">Añadir gasto</span>
                        </button>
                        <button type="button" class="modal-option" id="optionRecurrente">
                            <span class="modal-option-icon modal-option-icon--recurring">
                                <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M17 1v6h6M3 11V9a4 4 0 0 1 4-4h14M7 23v-6H1M21 13v2a4 4 0 0 1-4 4H3" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            </span>
                            <span class="modal-option-label">Añadir recurrente</span>
                        </button>
                    </div>
                </div>
            </div>
        `;
        return modal;
    }

    // Crear modal de ingreso/gasto
    function createMovimientoModal(tipo) {
        const { mes, anio } = getMesAnioSeleccionados();
        const titulo = tipo === 'ingreso' ? 'Añadir ingreso' : 'Añadir gasto';
        const claseTipo = tipo === 'ingreso' ? 'btn-save--income' : 'btn-save--expense';

        const modal = document.createElement('div');
        modal.id = 'movimientoModal';
        modal.className = 'modal';
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.innerHTML = `
            <div class="modal-content" style="max-width: 400px;">
                ${SHEET_HANDLE_HTML}
                <div class="modal-header">
                    <h3 class="modal-title">${titulo}</h3>
                    <button type="button" class="modal-close" id="closeMovimientoModal" aria-label="Cerrar">&times;</button>
                </div>
                <form id="movimientoForm" class="modal-form" method="post" action="/movimientos/add">
                    <div class="form-group">
                        <label for="cantidad">Cantidad (€)</label>
                        <input type="number" id="cantidad" name="cantidad" step="0.01" min="0" placeholder="0.00" required>
                    </div>
                    <div class="form-group">
                        <label for="asunto">Asunto (opcional)</label>
                        <input type="text" id="asunto" name="asunto" maxlength="50">
                    </div>
                    <div class="form-group">
                        <label for="fecha">Fecha del movimiento</label>
                        <input type="date" id="fecha" name="fecha" value="${new Date().toISOString().slice(0,10)}" required>
                    </div>
                    <div class="form-group">
                        <label for="categoria">Categoría</label>
                        <select id="categoria" name="categoria">
                            <option value="" selected>Sin categoría</option>
                            <option value="TRANSPORTE">Transporte</option>
                            <option value="COMIDA">Comida</option>
                            <option value="OCIO_ENTRETENIMIENTO">Ocio y Entretenimiento</option>
                            <option value="HOGAR">Hogar</option>
                            <option value="SALUD_BIENESTAR">Salud y Bienestar</option>
                            <option value="EDUCACION_CURSOS">Educación y Cursos</option>
                            <option value="COMPRAS">Compras</option>
                            <option value="COMPRAS_ONLINE">Compras Online</option>
                            <option value="SUSCRIPCION">Suscripción</option>
                        </select>
                    </div>
                    <input type="hidden" name="ingreso" value="${tipo === 'ingreso' ? 'true' : 'false'}">
                    <input type="hidden" name="mes" value="${mes}">
                    <input type="hidden" name="anio" value="${anio}">
                    <div class="modal-actions">
                        <button type="button" class="btn-cancel" id="cancelarMovimiento">Cancelar</button>
                        <button type="submit" class="btn-save ${claseTipo}">Guardar</button>
                    </div>
                </form>
            </div>
        `;
        return modal;
    }

    // Crear modal de recurrente
    function createRecurrenteModal() {
        const modal = document.createElement('div');
        modal.id = 'recurrenteModal';
        modal.className = 'modal';
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.innerHTML = `
            <div class="modal-content recurrente-modal-content" style="max-width: 400px;">
                ${SHEET_HANDLE_HTML}
                <div class="modal-header">
                    <h3 class="modal-title">Añadir movimiento recurrente</h3>
                    <button type="button" class="modal-close" id="closeRecurrenteModal" aria-label="Cerrar">&times;</button>
                </div>
                <form id="recurrenteForm" class="modal-form" method="post" action="/movimientos/recurrente">
                    <div class="form-group">
                        <label for="recCantidad">Cantidad (€)</label>
                        <input type="number" id="recCantidad" name="cantidad" step="0.01" min="0.01" placeholder="0.00" required>
                    </div>
                    <div class="form-group">
                        <label for="recAsunto">Asunto</label>
                        <input type="text" id="recAsunto" name="asunto" maxlength="50" required>
                    </div>
                    <div class="form-group">
                        <label for="recTipo">Tipo</label>
                        <select id="recTipo" name="ingreso" required>
                            <option value="true">Ingreso</option>
                            <option value="false">Gasto</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="recFecha">Primer día</label>
                        <input type="date" id="recFecha" name="fecha" required>
                    </div>
                    <div class="form-group">
                        <label for="recFrecuencia">Frecuencia</label>
                        <select id="recFrecuencia" name="frecuencia" required>
                            <option value="semana">Cada semana</option>
                            <option value="dos_semanas">Cada dos semanas</option>
                            <option value="mes">Cada mes</option>
                            <option value="dos_meses">Cada dos meses</option>
                            <option value="anio">Cada año</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="recCategoria">Categoría</label>
                        <select id="recCategoria" name="categoria">
                            <option value="" selected>Sin categoría</option>
                            <option value="TRANSPORTE">Transporte</option>
                            <option value="COMIDA">Comida</option>
                            <option value="OCIO_ENTRETENIMIENTO">Ocio y Entretenimiento</option>
                            <option value="HOGAR">Hogar</option>
                            <option value="SALUD_BIENESTAR">Salud y Bienestar</option>
                            <option value="EDUCACION_CURSOS">Educación y Cursos</option>
                            <option value="COMPRAS">Compras</option>
                            <option value="COMPRAS_ONLINE">Compras Online</option>
                            <option value="SUSCRIPCION">Suscripción</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="recFechaFin">Fecha fin (opcional)</label>
                        <input type="date" id="recFechaFin" name="fechaFin">
                    </div>
                    <div class="modal-actions">
                        <button type="button" class="btn-cancel" id="cancelarRecurrente">Cancelar</button>
                        <button type="submit" class="btn-save btn-save--recurring">Guardar</button>
                    </div>
                </form>
            </div>
        `;
        return modal;
    }

    // Elimina cualquier modal abierto antes de crear uno nuevo (sin transición: es un reemplazo instantáneo)
    function removeAnyOpenModal() {
        const modals = document.querySelectorAll('.modal');
        modals.forEach(m => {
            if (m.parentNode) m.parentNode.removeChild(m);
        });
    }

    // Evento del botón del footer
    if (footerBtn) {
        footerBtn.addEventListener('click', function() {
            removeAnyOpenModal();
            const modal = createMainModal();
            presentModal(modal, () => dismissModal(modal));

            // Eventos de las opciones
            document.getElementById('optionIngreso').addEventListener('click', function() {
                dismissModal(modal, () => {
                    const movimientoModal = createMovimientoModal('ingreso');
                    presentModal(movimientoModal, () => dismissModal(movimientoModal));
                });
            });

            document.getElementById('optionGasto').addEventListener('click', function() {
                dismissModal(modal, () => {
                    const movimientoModal = createMovimientoModal('gasto');
                    presentModal(movimientoModal, () => dismissModal(movimientoModal));
                });
            });

            document.getElementById('optionRecurrente').addEventListener('click', function() {
                dismissModal(modal, () => {
                    const recurrenteModal = createRecurrenteModal();
                    presentModal(recurrenteModal, () => dismissModal(recurrenteModal));
                    const closeBtn = document.getElementById('closeRecurrenteModal');
                    const cancelBtn = document.getElementById('cancelarRecurrente');
                    if (closeBtn) closeBtn.onclick = () => dismissModal(recurrenteModal);
                    if (cancelBtn) cancelBtn.onclick = () => dismissModal(recurrenteModal);
                    recurrenteModal.addEventListener('click', function(e) {
                        if (e.target === recurrenteModal) dismissModal(recurrenteModal);
                    });
                });
            });

            // Cerrar modal principal
            document.getElementById('closeMainModal').addEventListener('click', function() {
                dismissModal(modal);
            });

            // Cerrar al hacer clic fuera del modal
            modal.addEventListener('click', function(e) {
                if (e.target === modal) dismissModal(modal);
            });
        });
    }

    // Botón "Crear mi primer recurrente" (estado vacío de la página de recurrentes):
    // abre directamente el modal de alta de recurrente, sin pasar por el menú de opciones.
    const btnPrimerRecurrente = document.getElementById('btnCrearPrimerRecurrente');
    if (btnPrimerRecurrente) {
        btnPrimerRecurrente.addEventListener('click', function(e) {
            e.preventDefault();
            removeAnyOpenModal();
            const recurrenteModal = createRecurrenteModal();
            presentModal(recurrenteModal, () => dismissModal(recurrenteModal));
            const closeBtn = document.getElementById('closeRecurrenteModal');
            const cancelBtn = document.getElementById('cancelarRecurrente');
            if (closeBtn) closeBtn.onclick = () => dismissModal(recurrenteModal);
            if (cancelBtn) cancelBtn.onclick = () => dismissModal(recurrenteModal);
            recurrenteModal.addEventListener('click', function(ev) {
                if (ev.target === recurrenteModal) dismissModal(recurrenteModal);
            });
        });
    }

    // Delegación de eventos para modales de movimiento y recurrente
    document.addEventListener('click', function(e) {
        if (e.target.id === 'closeMovimientoModal' || e.target.id === 'cancelarMovimiento') {
            const modal = document.getElementById('movimientoModal');
            if (modal) dismissModal(modal);
        }

        if (e.target.id === 'closeRecurrenteModal' || e.target.id === 'cancelarRecurrente') {
            const modal = document.getElementById('recurrenteModal');
            if (modal) dismissModal(modal);
        }

        if (e.target.id === 'movimientoModal') {
            dismissModal(e.target);
        }

        if (e.target.id === 'recurrenteModal') {
            dismissModal(e.target);
        }
    });

    // Cerrar el modal abierto con Escape (accesibilidad de teclado)
    document.addEventListener('keydown', function(e) {
        if (e.key !== 'Escape') return;
        const openModal = document.querySelector('.modal.show');
        if (openModal) dismissModal(openModal);
    });

    // Estilos CSS para los modales
    const styles = document.createElement('style');
    styles.textContent = `
        .modal {
            display: flex;
            align-items: center;
            justify-content: center;
            position: fixed;
            z-index: 10000;
            left: 0;
            top: 0;
            width: 100%;
            height: 100%;
            background-color: rgba(8,9,12,0.5);
            opacity: 0;
            visibility: hidden;
            pointer-events: none;
            transition: opacity var(--dur-base, 320ms) var(--ease-standard, ease), visibility var(--dur-base, 320ms);
        }

        .modal.show {
            opacity: 1;
            visibility: visible;
            pointer-events: auto;
        }

        .modal-content {
            position: relative;
            background: var(--bg-primary);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-md, 16px);
            box-shadow: 0 20px 60px rgba(0,0,0,0.35);
            transform: scale(0.94) translateY(10px);
            opacity: 0;
            transition: transform var(--dur-base, 320ms) var(--ease-standard, ease), opacity var(--dur-fast, 180ms) var(--ease-standard, ease);
        }
        .modal.show .modal-content {
            transform: scale(1) translateY(0);
            opacity: 1;
        }

        .sheet-handle {
            display: flex;
            justify-content: center;
            padding: 10px 0 2px 0;
            cursor: grab;
            touch-action: none;
        }
        .sheet-handle span {
            width: 36px;
            height: 4px;
            border-radius: 3px;
            background: var(--border-color);
        }
        .sheet-handle:active {
            cursor: grabbing;
        }

        .modal-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0.5rem 1.5rem 0 1.5rem;
            margin-bottom: 1rem;
        }

        .modal-title {
            margin: 0;
            color: var(--text-primary);
            font-size: 1.15rem;
            font-weight: 700;
            letter-spacing: -0.015em;
        }

        .modal-close {
            background: var(--bg-elevated, var(--bg-secondary));
            border: 1px solid var(--border-color);
            font-size: 1.1rem;
            color: var(--text-secondary);
            cursor: pointer;
            padding: 0;
            width: 30px;
            height: 30px;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 50%;
            transition: background-color var(--dur-fast, 180ms) var(--ease-standard, ease), transform var(--dur-instant, 100ms) var(--ease-standard, ease);
        }

        .modal-close:hover {
            background-color: var(--border-color);
        }
        .modal-close:active {
            transform: scale(0.9);
        }

        .modal-body {
            padding: 0 1.5rem 1.5rem 1.5rem;
        }

        .modal-form {
            padding: 0 1.5rem 1.5rem 1.5rem;
        }

        .form-group {
            margin-bottom: 1rem;
        }

        .form-group label {
            display: block;
            margin-bottom: 0.5rem;
            color: var(--text-primary);
            font-weight: 500;
            font-size: 0.9rem;
        }

        .form-group input,
        .form-group select {
            width: 100%;
            padding: 0.75rem;
            border: 1px solid var(--input-border, var(--border-color));
            border-radius: 10px;
            background: var(--bg-secondary);
            color: var(--text-primary);
            font-size: 1rem;
        }

        .form-group input:focus,
        .form-group select:focus {
            border-color: var(--accent);
        }

        .modal-actions {
            display: flex;
            gap: 0.75rem;
            justify-content: flex-end;
            margin-top: 1.5rem;
        }

        .btn-cancel,
        .btn-save {
            padding: 0.75rem 1.5rem;
            border: none;
            border-radius: 10px;
            font-size: 0.95rem;
            font-weight: 600;
            cursor: pointer;
            transition: background-color var(--dur-fast, 180ms) var(--ease-standard, ease), transform var(--dur-instant, 100ms) var(--ease-standard, ease);
        }
        .btn-cancel:active,
        .btn-save:active {
            transform: scale(0.96);
        }

        .btn-cancel {
            background: var(--border-color);
            color: var(--text-primary);
        }

        .btn-save {
            background: var(--accent);
            color: #06120a;
        }
        .btn-save--income {
            background: var(--accent);
            color: #06120a;
        }
        .btn-save--expense {
            background: var(--danger);
            color: #fff;
        }
        .btn-save--recurring {
            background: var(--info);
            color: #fff;
        }

        .btn-cancel:hover {
            background: var(--bg-secondary);
        }

        .modal-options {
            display: flex;
            flex-direction: column;
            gap: 0.7rem;
        }
        .modal-option {
            display: flex;
            align-items: center;
            gap: 0.9rem;
            padding: 0.9rem 1rem;
            border: 1px solid var(--border-color);
            border-radius: 14px;
            background: var(--bg-secondary);
            color: var(--text-primary);
            cursor: pointer;
            text-align: left;
            font-size: 0.98rem;
            font-weight: 600;
            transition: background-color var(--dur-fast, 180ms) var(--ease-standard, ease), border-color var(--dur-fast, 180ms) var(--ease-standard, ease), transform var(--dur-instant, 100ms) var(--ease-standard, ease);
        }
        .modal-option:hover {
            border-color: var(--text-tertiary, var(--text-secondary));
        }
        .modal-option:active {
            transform: scale(0.98);
        }
        .modal-option-icon {
            width: 38px;
            height: 38px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
        }
        .modal-option-icon svg {
            width: 20px;
            height: 20px;
        }
        .modal-option-icon--income {
            background: var(--accent-soft);
            color: var(--accent);
        }
        .modal-option-icon--expense {
            background: var(--danger-soft);
            color: var(--danger);
        }
        .modal-option-icon--recurring {
            background: var(--info-soft);
            color: var(--info);
        }

        @media (max-width: 600px) {
            .modal-content {
                margin: 1rem;
                max-width: calc(100% - 2rem) !important;
            }
        }

        .recurrente-modal-content {
            max-width: 400px !important;
            width: 100%;
            box-sizing: border-box;
            max-height: 92vh;
            overflow-y: auto;
            padding: 0 0.7rem 0.7rem 0.7rem !important;
            font-size: 0.85rem;
            scrollbar-width: none;
            -ms-overflow-style: none;
        }
        .recurrente-modal-content::-webkit-scrollbar {
            display: none;
        }
        .recurrente-modal-content .modal-title {
            font-size: 0.98rem;
        }
        .recurrente-modal-content .form-group {
            margin-bottom: 0.45rem;
        }
        .recurrente-modal-content .form-group label {
            font-size: 0.85rem;
            margin-bottom: 0.12rem;
        }
        .recurrente-modal-content .form-group input,
        .recurrente-modal-content .form-group select {
            font-size: 0.85rem;
            padding: 0.4rem 0.6rem;
        }
        .recurrente-modal-content .modal-actions {
            margin-top: 0.4rem;
        }
        @media (max-width: 700px) {
            .recurrente-modal-content {
                max-width: 98vw !important;
                margin: 0.2rem !important;
                padding: 0.1rem !important;
            }
        }
        @media (max-width: 480px) {
            .recurrente-modal-content {
                max-width: 100vw !important;
                margin: 0 !important;
                border-radius: 0 !important;
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .modal, .modal-content {
                transition: opacity 150ms linear !important;
                transform: none !important;
            }
        }
    `;
    document.head.appendChild(styles);
});
