document.addEventListener('DOMContentLoaded', function() {
    // Botón del footer
    const footerBtn = document.querySelector('.footer-btn.center');
    
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

    // Crear modal principal
    function createMainModal() {
        const modal = document.createElement('div');
        modal.id = 'mainModal';
        modal.className = 'modal';
        modal.innerHTML = `
            <div class="modal-content" style="max-width: 350px;">
                <div class="modal-header">
                    <h3 class="modal-title">Añadir Movimiento</h3>
                    <button type="button" class="modal-close" id="closeMainModal">&times;</button>
                </div>
                <div class="modal-body" style="padding: 1rem;">
                    <div class="modal-options" style="display: flex; flex-direction: column; gap: 1rem;">
                        <button type="button" class="modal-option" id="optionIngreso" style="display: flex; align-items: center; gap: 0.8rem; padding: 1rem; border: 1px solid var(--border-color); border-radius: 8px; background: var(--bg-primary); color: var(--text-primary); cursor: pointer; transition: all 0.2s;">
                            <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" style="width: 24px; height: 24px; color: #090;">
                                <path d="M12 5v14M5 12h14" stroke-linecap="round" stroke-linejoin="round"/>
                            </svg>
                            <span style="font-weight: 500;">Añadir Ingreso</span>
                        </button>
                        <button type="button" class="modal-option" id="optionGasto" style="display: flex; align-items: center; gap: 0.8rem; padding: 1rem; border: 1px solid var(--border-color); border-radius: 8px; background: var(--bg-primary); color: var(--text-primary); cursor: pointer; transition: all 0.2s;">
                            <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" style="width: 24px; height: 24px; color: #c00;">
                                <path d="M12 5v14M5 12h14" stroke-linecap="round" stroke-linejoin="round"/>
                            </svg>
                            <span style="font-weight: 500;">Añadir Gasto</span>
                        </button>
                        <button type="button" class="modal-option" id="optionRecurrente" style="display: flex; align-items: center; gap: 0.8rem; padding: 1rem; border: 1px solid var(--border-color); border-radius: 8px; background: var(--bg-primary); color: var(--text-primary); cursor: pointer; transition: all 0.2s;">
                            <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" style="width: 24px; height: 24px; color: #007bff;">
                                <path d="M17 1v6h6M3 11V9a4 4 0 0 1 4-4h14M7 23v-6H1M21 13v2a4 4 0 0 1-4 4H3" stroke-linecap="round" stroke-linejoin="round"/>
                            </svg>
                            <span style="font-weight: 500;">Añadir Recurrente</span>
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
        const titulo = tipo === 'ingreso' ? 'Añadir Ingreso' : 'Añadir Gasto';
        const color = tipo === 'ingreso' ? '#090' : '#c00';
        
        const modal = document.createElement('div');
        modal.id = 'movimientoModal';
        modal.className = 'modal';
        modal.innerHTML = `
            <div class="modal-content" style="max-width: 400px;">
                <div class="modal-header">
                    <h3 class="modal-title">${titulo}</h3>
                    <button type="button" class="modal-close" id="closeMovimientoModal">&times;</button>
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
                        <button type="submit" class="btn-save" style="background: ${color}; color: white;">Guardar</button>
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
        modal.innerHTML = `
            <div class="modal-content recurrente-modal-content" style="max-width: 400px;">
                <div class="modal-header">
                    <h3 class="modal-title">Añadir Movimiento Recurrente</h3>
                    <button type="button" class="modal-close" id="closeRecurrenteModal">&times;</button>
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
                        <button type="submit" class="btn-save" style="background: #007bff; color: white;">Guardar</button>
                    </div>
                </form>
            </div>
        `;
        return modal;
    }

    // Elimina cualquier modal abierto antes de crear uno nuevo
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
            document.body.appendChild(modal);
            modal.classList.add('show');
            
            // Eventos de las opciones
            document.getElementById('optionIngreso').addEventListener('click', function() {
                modal.classList.remove('show');
                setTimeout(() => {
                    removeAnyOpenModal();
                    const movimientoModal = createMovimientoModal('ingreso');
                    document.body.appendChild(movimientoModal);
                    movimientoModal.classList.add('show');
                }, 200);
            });
            
            document.getElementById('optionGasto').addEventListener('click', function() {
                modal.classList.remove('show');
                setTimeout(() => {
                    removeAnyOpenModal();
                    const movimientoModal = createMovimientoModal('gasto');
                    document.body.appendChild(movimientoModal);
                    movimientoModal.classList.add('show');
                }, 200);
            });
            
            document.getElementById('optionRecurrente').addEventListener('click', function() {
                modal.classList.remove('show');
                setTimeout(() => {
                    removeAnyOpenModal();
                    const recurrenteModal = createRecurrenteModal();
                    document.body.appendChild(recurrenteModal);
                    recurrenteModal.classList.add('show');
                    // Añadir listeners para cerrar el modal
                    const closeBtn = document.getElementById('closeRecurrenteModal');
                    const cancelBtn = document.getElementById('cancelarRecurrente');
                    if (closeBtn) closeBtn.onclick = function() {
                        recurrenteModal.classList.remove('show');
                        setTimeout(() => { document.body.removeChild(recurrenteModal); }, 200);
                    };
                    if (cancelBtn) cancelBtn.onclick = function() {
                        recurrenteModal.classList.remove('show');
                        setTimeout(() => { document.body.removeChild(recurrenteModal); }, 200);
                    };
                    // Cerrar al hacer clic fuera del modal
                    recurrenteModal.addEventListener('click', function(e) {
                        if (e.target === recurrenteModal) {
                            recurrenteModal.classList.remove('show');
                            setTimeout(() => { document.body.removeChild(recurrenteModal); }, 200);
                        }
                    });
                }, 200);
            });
            
            // Cerrar modal principal
            document.getElementById('closeMainModal').addEventListener('click', function() {
                modal.classList.remove('show');
                setTimeout(() => {
                    document.body.removeChild(modal);
                }, 200);
            });
            
            // Cerrar al hacer clic fuera del modal
            modal.addEventListener('click', function(e) {
                if (e.target === modal) {
                    modal.classList.remove('show');
                    setTimeout(() => {
                        document.body.removeChild(modal);
                    }, 200);
                }
            });
        });
    }

    // Delegación de eventos para modales de movimiento y recurrente
    document.addEventListener('click', function(e) {
        // Cerrar modal de movimiento
        if (e.target.id === 'closeMovimientoModal' || e.target.id === 'cancelarMovimiento') {
            const modal = document.getElementById('movimientoModal');
            if (modal) {
                modal.classList.remove('show');
                setTimeout(() => {
                    document.body.removeChild(modal);
                }, 200);
            }
        }
        
        // Cerrar modal de recurrente
        if (e.target.id === 'closeRecurrenteModal' || e.target.id === 'cancelarRecurrente') {
            const modal = document.getElementById('recurrenteModal');
            if (modal) {
                modal.classList.remove('show');
                setTimeout(() => {
                    document.body.removeChild(modal);
                }, 200);
            }
        }
        
        // Cerrar modal de movimiento al hacer clic fuera
        if (e.target.id === 'movimientoModal') {
            const modal = document.getElementById('movimientoModal');
            if (modal) {
                modal.classList.remove('show');
                setTimeout(() => {
                    document.body.removeChild(modal);
                }, 200);
            }
        }
        
        // Cerrar modal de recurrente al hacer clic fuera
        if (e.target.id === 'recurrenteModal') {
            const modal = document.getElementById('recurrenteModal');
            if (modal) {
                modal.classList.remove('show');
                setTimeout(() => {
                    document.body.removeChild(modal);
                }, 200);
            }
        }
    });

    // Estilos CSS para los modales
    const styles = document.createElement('style');
    styles.textContent = `
        .modal {
            display: none;
            position: fixed;
            z-index: 10000;
            left: 0;
            top: 0;
            width: 100%;
            height: 100%;
            background-color: rgba(0,0,0,0.5);
            backdrop-filter: blur(4px);
        }
        
        .modal.show {
            display: flex;
            align-items: center;
            justify-content: center;
        }
        
        .modal-content {
            background: var(--bg-primary);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            box-shadow: 0 8px 32px rgba(0,0,0,0.3);
            animation: modalSlideIn 0.3s ease-out;
        }
        
        @keyframes modalSlideIn {
            from {
                opacity: 0;
                transform: translateY(-20px) scale(0.95);
            }
            to {
                opacity: 1;
                transform: translateY(0) scale(1);
            }
        }
        
        .modal-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 1.5rem 1.5rem 0 1.5rem;
            border-bottom: 1px solid var(--border-color);
            margin-bottom: 1rem;
        }
        
        .modal-title {
            margin: 0;
            color: var(--text-primary);
            font-size: 1.2rem;
            font-weight: 600;
        }
        
        .modal-close {
            background: none;
            border: none;
            font-size: 1.5rem;
            color: var(--text-secondary);
            cursor: pointer;
            padding: 0;
            width: 30px;
            height: 30px;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 50%;
            transition: background-color 0.2s;
        }
        
        .modal-close:hover {
            background-color: var(--border-color);
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
        }
        
        .form-group input,
        .form-group select {
            width: 100%;
            padding: 0.75rem;
            border: 1px solid var(--border-color);
            border-radius: 6px;
            background: var(--bg-secondary);
            color: var(--text-primary);
            font-size: 1rem;
        }
        
        .form-group input:focus,
        .form-group select:focus {
            outline: none;
            border-color: #007bff;
            box-shadow: 0 0 0 2px rgba(0,123,255,0.2);
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
            border-radius: 6px;
            font-size: 1rem;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.2s;
        }
        
        .btn-cancel {
            background: var(--border-color);
            color: var(--text-primary);
        }
        
        .btn-save {
            background: #007bff;
            color: white;
        }
        
        .btn-cancel:hover {
            background: var(--bg-secondary);
        }
        
        .btn-save:hover {
            background: #0056b3;
        }
        
        .modal-option:hover {
            background: var(--bg-secondary) !important;
            border-color: var(--text-secondary) !important;
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
            scrollbar-width: none; /* Firefox */
            -ms-overflow-style: none; /* IE 10+ */
        }
        .recurrente-modal-content::-webkit-scrollbar {
            display: none; /* Chrome, Safari y Opera */
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
            padding: 0.28rem 0.5rem;
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
    `;
    document.head.appendChild(styles);
}); 