/**
 * Spring animator vanilla (sin dependencias externas).
 * Implementa los principios de "Designing Fluid Interfaces" (WWDC 2018):
 * - Parametrizado por damping ratio + response (no duración fija).
 * - Interrumpible: re-apuntar a un nuevo target parte del valor y la
 *   velocidad actuales en pantalla, nunca del valor lógico/objetivo.
 * - Admite velocidad inicial (para el handoff gesto -> animación).
 */
class Spring {
    /**
     * @param {number} value valor inicial
     * @param {{damping?: number, response?: number}} opts damping: 1 = crítico (sin rebote), <1 = rebote. response: segundos aprox. hasta el objetivo.
     */
    constructor(value, opts = {}) {
        this.value = value;
        this.velocity = 0;
        this.target = value;
        this.damping = opts.damping ?? 1;
        this.response = Math.max(opts.response ?? 0.35, 0.01);
        this._raf = null;
        this._onUpdate = null;
        this._onSettle = null;
    }

    // Re-apunta sin cortar la velocidad actual: la animación sigue desde donde está.
    to(target, { velocity, onUpdate, onSettle } = {}) {
        this.target = target;
        if (typeof velocity === 'number') this.velocity = velocity;
        if (onUpdate) this._onUpdate = onUpdate;
        if (onSettle !== undefined) this._onSettle = onSettle;
        this._run();
        return this;
    }

    // Salta al valor sin animar (para inicializar o cancelar).
    set(value) {
        this.value = value;
        this.target = value;
        this.velocity = 0;
        if (this._raf) cancelAnimationFrame(this._raf);
        this._raf = null;
        return this;
    }

    stop() {
        if (this._raf) cancelAnimationFrame(this._raf);
        this._raf = null;
    }

    _run() {
        if (this._raf) return; // ya animando; el nuevo target se recoge en el siguiente frame
        let last = performance.now();
        const zeta = this.damping;
        const omega = (2 * Math.PI) / this.response;

        const step = (now) => {
            const dt = Math.min((now - last) / 1000, 1 / 30);
            last = now;

            const displacement = this.value - this.target;
            // Integración semi-implícita del oscilador amortiguado (estable y barata).
            const springForce = -omega * omega * displacement;
            const dampingForce = -2 * zeta * omega * this.velocity;
            const accel = springForce + dampingForce;

            this.velocity += accel * dt;
            this.value += this.velocity * dt;

            if (this._onUpdate) this._onUpdate(this.value);

            const settled = Math.abs(this.value - this.target) < 0.01 && Math.abs(this.velocity) < 0.01;
            if (settled) {
                this.value = this.target;
                this.velocity = 0;
                if (this._onUpdate) this._onUpdate(this.value);
                this._raf = null;
                if (this._onSettle) this._onSettle();
                return;
            }
            this._raf = requestAnimationFrame(step);
        };
        this._raf = requestAnimationFrame(step);
    }
}

// Resistencia progresiva en el límite (rubber-banding), no un tope duro.
function rubberband(overshoot, dimension, constant = 0.55) {
    return (overshoot * dimension * constant) / (dimension + constant * Math.abs(overshoot));
}

window.Spring = Spring;
window.rubberband = rubberband;
