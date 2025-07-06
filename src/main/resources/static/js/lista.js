 document.addEventListener('DOMContentLoaded', function() {


    // --- Menú tres puntitos: abrir/cerrar ---
    document.querySelectorAll('.menu-trigger').forEach(function(btn) {
    btn.addEventListener('click', function(e) {
    e.stopPropagation();
    // Cierra otros menús abiertos
    document.querySelectorAll('.menu-dropdown').forEach(function(menu) {
    if (menu !== btn.nextElementSibling) menu.classList.remove('show');
});
    // Toggle menú actual
    btn.nextElementSibling.classList.toggle('show');
});
});
    // Cerrar menú al hacer clic fuera
    document.addEventListener('click', function() {
    document.querySelectorAll('.menu-dropdown').forEach(function(menu) {
    menu.classList.remove('show');
});
});

    // --- Delegación de eventos para opciones del menú (modificar/eliminar) ---
    let movimientoAEliminar = null;
    document.getElementById('movimientos-list').addEventListener('click', function(e) {
    // Eliminar movimiento
    if (e.target.classList.contains('delete')) {
    e.preventDefault();
    e.stopPropagation();
    const li = e.target.closest('li');
    movimientoAEliminar = li.getAttribute('data-id');
    document.getElementById('modalConfirmarEliminar').classList.add('show');
    return;
}
    // Modificar movimiento
    if (e.target.classList.contains('menu-item') && !e.target.classList.contains('delete')) {
    e.preventDefault();
    e.stopPropagation();
    const li = e.target.closest('li');
    const id = li.getAttribute('data-id');
    // Obtener cantidad
    const cantidadSpan = li.querySelector('.cantidad-ingreso, .cantidad-gasto span');
    let cantidad = '';
    if (cantidadSpan) {
    cantidad = cantidadSpan.innerText.replace(' €','').replace(',','.');
}
    // Obtener asunto
    let asunto = '';
    const asuntoElem = li.querySelector('span[style*="margin-left:0.5rem"] span');
    if (asuntoElem) {
    asunto = asuntoElem.innerText;
}
    // Saber si es ingreso
    const ingreso = li.querySelector('.cantidad-ingreso') !== null;
    // Rellenar modal
    document.getElementById('editCantidad').value = cantidad;
    document.getElementById('editAsunto').value = asunto;
    document.getElementById('editTipo').value = ingreso ? 'true' : 'false';
    // Guardar id en el form
    document.getElementById('editForm').action = '/movimientos/edit/' + id;
    // Mostrar modal
    document.getElementById('editModal').classList.add('show');
}
});
    // Botón cancelar del modal
    document.getElementById('btnCancelar').addEventListener('click', function() {
    document.getElementById('editModal').classList.remove('show');
});
    document.getElementById('cancelarEliminarMov').onclick = function() {
    document.getElementById('modalConfirmarEliminar').classList.remove('show');
    movimientoAEliminar = null;
};
    document.getElementById('confirmarEliminarMov').onclick = function() {
    if (movimientoAEliminar) {
    // Crear y enviar formulario oculto
    const form = document.createElement('form');
    form.method = 'post';
    form.action = '/movimientos/delete/' + movimientoAEliminar;
    form.style.display = 'none';
    document.body.appendChild(form);
    form.submit();
}
};

    // --- Carrusel/paginador de movimientos ---
    const movimientosList = document.getElementById('movimientos-list');
    const items = movimientosList.querySelectorAll('.movimiento-item');
    const controls = document.getElementById('carousel-controls');
    let start = 0;
    const pageSize = 5;
    function renderPage() {
    let total = items.length;
    // Animación fade: ocultar los actuales con fade-out
    const visibles = [];
    items.forEach((li, idx) => {
    if (li.style.display !== 'none') visibles.push(li);
});
    visibles.forEach(li => {
    li.classList.remove('fading-in');
    li.classList.add('fading-out');
});
    // Después de la animación, mostrar los nuevos
    setTimeout(() => {
    items.forEach((li, idx) => {
    if (idx >= start && idx < start + pageSize) {
    li.style.display = '';
    li.classList.remove('fading-out');
    li.classList.add('fading-in');
} else {
    li.style.display = 'none';
    li.classList.remove('fading-in');
    li.classList.remove('fading-out');
}
});
}, visibles.length ? 220 : 0); // Si hay visibles, espera el fade-out
    // Controles
    controls.innerHTML = '';
    controls.style.display = 'grid';
    controls.style.gridTemplateColumns = '1fr auto 1fr';
    controls.style.justifyItems = 'center';
    controls.style.alignItems = 'center';
    controls.style.gap = '0.7rem';
    controls.style.width = '100%';
    controls.style.minHeight = '28px';
    if (total > pageSize) {
    // Flecha izquierda
    const leftCell = document.createElement('div');
    leftCell.style.justifySelf = 'end';
    if (start > 0) {
    const prev = document.createElement('button');
    prev.innerHTML = '⟨';
    prev.title = 'Anterior';
    prev.style.fontSize = '1rem';
    prev.style.padding = '0.1em 0.5em';
    prev.style.borderRadius = '5px';
    prev.style.background = 'var(--border-color)';
    prev.style.color = 'var(--text-primary)';
    prev.style.border = 'none';
    prev.style.cursor = 'pointer';
    prev.onclick = function() {
    start = Math.max(0, start - pageSize);
    renderPage();
};
    leftCell.appendChild(prev);
}
    controls.appendChild(leftCell);
    // Número de página
    const pageInfo = document.createElement('span');
    pageInfo.textContent = `${Math.floor(start/pageSize)+1} / ${Math.ceil(total/pageSize)}`;
    pageInfo.style.fontSize = '0.95rem';
    pageInfo.style.padding = '0.1em 0.7em';
    pageInfo.style.background = 'none';
    pageInfo.style.color = 'var(--text-secondary)';
    pageInfo.style.fontWeight = '500';
    controls.appendChild(pageInfo);
    // Flecha derecha
    const rightCell = document.createElement('div');
    rightCell.style.justifySelf = 'start';
    if (start + pageSize < total) {
    const next = document.createElement('button');
    next.innerHTML = '⟩';
    next.title = 'Siguiente';
    next.style.fontSize = '1rem';
    next.style.padding = '0.1em 0.5em';
    next.style.borderRadius = '5px';
    next.style.background = 'var(--border-color)';
    next.style.color = 'var(--text-primary)';
    next.style.border = 'none';
    next.style.cursor = 'pointer';
    next.onclick = function() {
    start = Math.min(total - (total % pageSize === 0 ? pageSize : total % pageSize), start + pageSize);
    renderPage();
};
    rightCell.appendChild(next);
}
    controls.appendChild(rightCell);
}
}
    renderPage();

    // --- Botón y modal de recurrente ---
    const recurrenteModal = document.getElementById('recurrenteModal');
    const btnCancelarRecurrente = document.getElementById('btnCancelarRecurrente');
    btnRecurrente.addEventListener('click', function() {
    recurrenteModal.classList.add('show');
});
    btnCancelarRecurrente.addEventListener('click', function() {
    recurrenteModal.classList.remove('show');
});
    // Validación: fecha no pasada
    document.getElementById('recFecha').min = new Date().toISOString().split('T')[0];
    // Envío del formulario recurrente
    document.getElementById('recurrenteForm').onsubmit = function(e) {
    const cantidad = document.getElementById('recCantidad').value;
    const asunto = document.getElementById('recAsunto').value;
    const fecha = document.getElementById('recFecha').value;
    if (cantidad <= 0) {
    alert('La cantidad debe ser mayor que 0');
    e.preventDefault();
    return false;
}
    if (!asunto.trim()) {
    alert('El asunto es obligatorio');
    e.preventDefault();
    return false;
}
    if (!fecha) {
    alert('Debes seleccionar una fecha');
    e.preventDefault();
    return false;
}
    // Submit normal (POST) al backend
};
});