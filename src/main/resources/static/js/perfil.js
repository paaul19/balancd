// perfil.js

document.addEventListener('DOMContentLoaded', function () {
    if (typeof window.gastosPorCategoriaPorMes !== 'object') {
        console.warn('gastosPorCategoriaPorMes no es un objeto:', window.gastosPorCategoriaPorMes);
        return;
    }
    // Obtener y ordenar los meses disponibles (formato 'YYYY-M')
    const meses = Object.keys(window.gastosPorCategoriaPorMes).sort((a, b) => {
        // Orden descendente (más reciente primero)
        const [ay, am] = a.split('-').map(Number);
        const [by, bm] = b.split('-').map(Number);
        if (ay !== by) return by - ay;
        return bm - am;
    });
    if (meses.length === 0) return;
    let indiceActual = 0;
    const titulo = document.getElementById('titulo-mes-grafico');
    const canvas = document.getElementById('grafico-categorias-carrusel');
    const btnAnt = document.getElementById('btnMesAnterior');
    const btnSig = document.getElementById('btnMesSiguiente');
    const indicadores = document.getElementById('carrusel-indicadores');
    let chart = null;

    function nombreMes(mes, anio) {
        const nombres = ['enero','febrero','marzo','abril','mayo','junio','julio','agosto','septiembre','octubre','noviembre','diciembre'];
        return nombres[mes-1] + ' ' + anio;
    }

    function renderIndicadores(idx) {
        indicadores.innerHTML = '';
        meses.forEach((_, i) => {
            const punto = document.createElement('div');
            punto.className = 'carrusel-punto' + (i === idx ? ' activo' : '');
            indicadores.appendChild(punto);
        });
    }

    function animarCambio() {
        const contenedor = document.getElementById('grafico-mes-contenedor');
        contenedor.style.opacity = 0;
        setTimeout(() => { contenedor.style.opacity = 1; }, 180);
    }

    function renderGrafico(idx) {
        animarCambio();
        renderIndicadores(idx);
        const mesKey = meses[idx];
        const [anio, mes] = mesKey.split('-').map(Number);
        const data = window.gastosPorCategoriaPorMes[mesKey];
        // Título bonito
        titulo.textContent = nombreMes(mes, anio);
        // Limpiar canvas
        if (chart) { chart.destroy(); chart = null; }
        const ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        if (!data || data.length === 0) {
            ctx.font = '16px sans-serif';
            ctx.fillStyle = '#888';
            ctx.textAlign = 'center';
            ctx.fillText('Sin datos', canvas.width / 2, canvas.height / 2);
            return;
        }
        const categorias = data.map(e => e.categoriaBonita);
        const valores = data.map(e => e.total);
        const colores = [
            '#1976d2', '#e53935', '#43a047', '#fbc02d', '#8e24aa', '#00897b', '#f57c00', '#6d4c41', '#c2185b', '#7e57c2'
        ];
        chart = new Chart(canvas, {
            type: 'doughnut',
            data: {
                labels: categorias,
                datasets: [{
                    data: valores,
                    backgroundColor: colores,
                }]
            },
            options: {
                plugins: {
                    legend: { position: 'bottom' }
                },
                cutout: '70%',
                responsive: false
            }
        });
    }

    btnAnt.addEventListener('click', function() {
        indiceActual = (indiceActual - 1 + meses.length) % meses.length;
        renderGrafico(indiceActual);
    });
    btnSig.addEventListener('click', function() {
        indiceActual = (indiceActual + 1) % meses.length;
        renderGrafico(indiceActual);
    });

    // Ocultar flechas si solo hay un mes
    if (meses.length === 1) {
        btnAnt.style.display = 'none';
        btnSig.style.display = 'none';
    }

    // Inicial
    renderGrafico(indiceActual);
}); 