package com.balancdapp.service;

import com.balancdapp.model.User;
import com.balancdapp.service.EncryptedMovimientoRecurrenteService.MovimientoRecurrenteDTO;
import com.balancdapp.service.EncryptedMovimientoService.MovimientoDTO;
import com.balancdapp.service.EncryptedPresupuestoService.PresupuestoDTO;
import com.balancdapp.util.MoneyFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Avisos y consejos para el home ("insights"): reglas sencillas sobre los datos del propio
 * usuario, calculadas en el servidor (los importes están cifrados en BD, así que no se puede
 * agregar con SQL: se parte de los movimientos ya descifrados). Sin servicios externos ni IA:
 * los datos financieros no salen del servidor.
 *
 * Los textos se redactan como observaciones ("llevas X"), no como recomendaciones.
 */
@Service
public class InsightService {

    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    private static final Locale ES = Locale.forLanguageTag("es");
    private static final int MAX_INSIGHTS = 4;

    /** Días mínimos transcurridos del mes para proyectar el cierre (antes hay muy pocos datos). */
    private static final int DIA_MINIMO_PROYECCION = 4;
    /** Cuánto por encima de la media habitual de una categoría se considera atípico. */
    private static final double FACTOR_ATIPICO = 1.30;
    /** Importe mínimo (en la divisa del usuario) para avisar de un gasto atípico: evita ruido. */
    private static final double IMPORTE_MINIMO_ATIPICO = 30.0;

    @Autowired
    private EncryptedMovimientoService movimientoService;
    @Autowired
    private EncryptedMovimientoRecurrenteService recurrenteService;
    @Autowired
    private EncryptedPresupuestoService presupuestoService;

    // ------------------------------------------------------------------ modelo

    /** Un trozo del texto; los importes van marcados para que la web pueda ocultarlos en modo secreto. */
    public static class Parte {
        private final String texto;
        private final boolean money;

        public Parte(String texto, boolean money) {
            this.texto = texto;
            this.money = money;
        }

        public String getTexto() { return texto; }
        public boolean isMoney() { return money; }
    }

    public static class Insight {
        private final String clave;
        private final String tipo;       // proyeccion | cobro | presupuesto | atipico | ingresos
        private final String severidad;  // info | positivo | aviso | importante
        private final int prioridad;
        private final List<Parte> partes;
        private final String validoHasta; // yyyy-MM-dd: hasta cuándo sigue siendo relevante (para descartarlo)

        Insight(String clave, String tipo, String severidad, int prioridad, List<Parte> partes, LocalDate validoHasta) {
            this.clave = clave;
            this.tipo = tipo;
            this.severidad = severidad;
            this.prioridad = prioridad;
            this.partes = partes;
            this.validoHasta = validoHasta.toString();
        }

        public String getClave() { return clave; }
        public String getTipo() { return tipo; }
        public String getSeveridad() { return severidad; }
        public int getPrioridad() { return prioridad; }
        public List<Parte> getPartes() { return partes; }
        public String getValidoHasta() { return validoHasta; }

        /** Texto plano (para la API / apps). */
        public String getTexto() {
            StringBuilder sb = new StringBuilder();
            for (Parte p : partes) sb.append(p.getTexto());
            return sb.toString();
        }
    }

    /** Constructor de textos: alterna fragmentos de texto e importes formateados. */
    private static class Texto {
        private final MoneyFormatter fmt;
        private final List<Parte> partes = new ArrayList<>();

        Texto(MoneyFormatter fmt) { this.fmt = fmt; }

        Texto t(String s) { partes.add(new Parte(s, false)); return this; }
        Texto m(double importe) { partes.add(new Parte(fmt.format(importe), true)); return this; }
        List<Parte> build() { return partes; }
    }

    // ------------------------------------------------------------------ API

    /** Variante para la API: carga los movimientos del usuario por su cuenta. */
    public List<Insight> generar(User user) {
        return generar(user, movimientoService.getMovimientosByUserId(user.getId()), LocalDate.now(ZONA));
    }

    /**
     * @param movimientos todos los movimientos del usuario (sin filtrar por cuenta ni búsqueda),
     *                    ya descifrados; se reutilizan los que el controlador ya cargó.
     */
    public List<Insight> generar(User user, List<MovimientoDTO> movimientos, LocalDate hoy) {
        List<Insight> out = new ArrayList<>();
        try {
            MoneyFormatter fmt = new MoneyFormatter(user.getMoneda());
            YearMonth ym = YearMonth.from(hoy);
            List<MovimientoRecurrenteDTO> recurrentes = recurrenteService.getRecurrentesByUser(user).stream()
                    .filter(MovimientoRecurrenteDTO::isActivo)
                    .toList();

            reglaProximosCobros(out, recurrentes, hoy, fmt);
            reglaPresupuestos(out, user, ym, fmt);
            reglaProyeccionCierre(out, movimientos, recurrentes, hoy, ym, fmt);
            reglaGastoAtipico(out, movimientos, ym, fmt);
            reglaIngresos(out, movimientos, hoy, ym, fmt);
        } catch (Exception e) {
            // Los consejos son accesorios: un fallo aquí nunca debe romper el home.
            return List.of();
        }
        out.sort(Comparator.comparingInt(Insight::getPrioridad).reversed());
        return out.size() > MAX_INSIGHTS ? new ArrayList<>(out.subList(0, MAX_INSIGHTS)) : out;
    }

    // ------------------------------------------------------------------ reglas

    /** Cobros e ingresos recurrentes de los próximos 1-3 días. */
    private void reglaProximosCobros(List<Insight> out, List<MovimientoRecurrenteDTO> recurrentes, LocalDate hoy, MoneyFormatter fmt) {
        for (int offset = 1; offset <= 3; offset++) {
            LocalDate dia = hoy.plusDays(offset);
            List<MovimientoRecurrenteDTO> gastos = new ArrayList<>();
            List<MovimientoRecurrenteDTO> ingresos = new ArrayList<>();
            for (MovimientoRecurrenteDTO rec : recurrentes) {
                if (!ocurreEn(rec, dia)) continue;
                (rec.isIngreso() ? ingresos : gastos).add(rec);
            }
            String cuando = offset == 1 ? "Mañana" : offset == 2 ? "Pasado mañana" : "En 3 días";
            int prioridadBase = offset == 1 ? 90 : offset == 2 ? 80 : 70;

            if (!gastos.isEmpty()) {
                Texto t = new Texto(fmt).t(cuando + (gastos.size() == 1 ? " se cobra " : " se cobran "));
                listar(t, gastos);
                out.add(new Insight("cobro-" + dia, "cobro", "info", prioridadBase, t.t(".").build(), dia));
            }
            if (!ingresos.isEmpty()) {
                Texto t = new Texto(fmt).t(cuando + " te ingresan ");
                listar(t, ingresos);
                out.add(new Insight("ingreso-prox-" + dia, "cobro", "positivo", prioridadBase - 20, t.t(".").build(), dia));
            }
        }
    }

    /** "Netflix (12,99 €)", "A (1 €) y B (2 €)", "A (1 €), B (2 €) y 1 más". */
    private void listar(Texto t, List<MovimientoRecurrenteDTO> recs) {
        int mostrar = Math.min(recs.size(), 2);
        for (int i = 0; i < mostrar; i++) {
            if (i > 0) t.t(i == mostrar - 1 && recs.size() == mostrar ? " y " : ", ");
            MovimientoRecurrenteDTO r = recs.get(i);
            t.t(nombre(r)).t(" (").m(r.getCantidad()).t(")");
        }
        if (recs.size() > mostrar) {
            int resto = recs.size() - mostrar;
            t.t(" y " + resto + " más");
        }
    }

    private String nombre(MovimientoRecurrenteDTO r) {
        String a = r.getAsunto();
        return (a == null || a.isBlank()) ? "un movimiento recurrente" : a.trim();
    }

    /** Presupuestos del mes: superados o a punto de agotarse. */
    private void reglaPresupuestos(List<Insight> out, User user, YearMonth ym, MoneyFormatter fmt) {
        List<PresupuestoDTO> presupuestos = presupuestoService.getPresupuestosConProgreso(user, ym.getMonthValue(), ym.getYear());
        LocalDate finMes = ym.atEndOfMonth();
        int emitidos = 0;
        for (PresupuestoDTO p : presupuestos.stream()
                .filter(x -> x.getLimite() > 0)
                .sorted(Comparator.comparingDouble((PresupuestoDTO x) -> x.getGastado() / x.getLimite()).reversed())
                .toList()) {
            if (emitidos >= 2) break;
            double ratio = p.getGastado() / p.getLimite();
            String etiqueta = p.getEtiqueta() == null || p.getEtiqueta().isBlank() ? "tu presupuesto" : p.getEtiqueta();
            if (ratio > 1.0) {
                Texto t = new Texto(fmt).t("Has superado tu presupuesto de " + etiqueta + ": ")
                        .m(p.getGastado()).t(" de ").m(p.getLimite()).t(".");
                out.add(new Insight("presupuesto-excedido-" + p.getId() + "-" + ym, "presupuesto", "importante", 100, t.build(), finMes));
                emitidos++;
            } else if (ratio >= 0.80) {
                long pct = Math.round(ratio * 100);
                Texto t = new Texto(fmt).t("Llevas el " + pct + " % de tu presupuesto de " + etiqueta + " (")
                        .m(p.getGastado()).t(" de ").m(p.getLimite()).t(").");
                out.add(new Insight("presupuesto-80-" + p.getId() + "-" + ym, "presupuesto", "aviso", 75, t.build(), finMes));
                emitidos++;
            }
        }
    }

    /**
     * "Al ritmo que llevas, cerrarás el mes con X de balance."
     *
     * Estimación: ingresos = lo ingresado + los recurrentes que aún tocan este mes (no se
     * extrapola ningún ingreso más). Gastos = lo gastado + los recurrentes pendientes + el gasto
     * "variable" (lo gastado sin contar recurrentes) al mismo ritmo diario que hasta hoy.
     */
    private void reglaProyeccionCierre(List<Insight> out, List<MovimientoDTO> movs, List<MovimientoRecurrenteDTO> recurrentes,
                                       LocalDate hoy, YearMonth ym, MoneyFormatter fmt) {
        int dia = hoy.getDayOfMonth();
        if (dia < DIA_MINIMO_PROYECCION) return;

        double ingresosHoy = 0, gastosHoy = 0;
        for (MovimientoDTO m : movs) {
            if (!delMes(m, ym)) continue;
            if (m.isIngreso()) ingresosHoy += m.getCantidad(); else gastosHoy += m.getCantidad();
        }
        if (ingresosHoy == 0 && gastosHoy == 0) return;

        LocalDate inicioMes = ym.atDay(1);
        LocalDate finMes = ym.atEndOfMonth();
        double recGastoYaCobrado = sumaRecurrentes(recurrentes, false, inicioMes, hoy);
        double recGastoPendiente = sumaRecurrentes(recurrentes, false, hoy.plusDays(1), finMes);
        double recIngresoPendiente = sumaRecurrentes(recurrentes, true, hoy.plusDays(1), finMes);

        int diasRestantes = ym.lengthOfMonth() - dia;
        double variableHoy = Math.max(0, gastosHoy - recGastoYaCobrado);
        double ritmoDiario = variableHoy / dia;

        double gastosProyectados = gastosHoy + ritmoDiario * diasRestantes + recGastoPendiente;
        double ingresosProyectados = ingresosHoy + recIngresoPendiente;
        double balance = ingresosProyectados - gastosProyectados;

        String mes = ym.getMonth().getDisplayName(TextStyle.FULL, ES);
        Texto t = new Texto(fmt)
                .t(diasRestantes == 0 ? "Este mes lo cerrarás con " : "Al ritmo que llevas, cerrarás " + mes + " con ")
                .m(balance)
                .t(" de balance.");
        out.add(new Insight("proyeccion-" + ym, "proyeccion", balance >= 0 ? "positivo" : "aviso",
                balance >= 0 ? 50 : 85, t.build(), finMes));
    }

    /** Categoría de gasto muy por encima de su media de los 3 meses anteriores. */
    private void reglaGastoAtipico(List<Insight> out, List<MovimientoDTO> movs, YearMonth ym, MoneyFormatter fmt) {
        Map<String, Double> actual = new HashMap<>();
        Map<String, Double> previos = new HashMap<>();
        Map<String, Integer> mesesConGasto = new HashMap<>();
        Map<String, java.util.Set<YearMonth>> mesesVistos = new HashMap<>();

        for (MovimientoDTO m : movs) {
            if (m.isIngreso() || m.getCategoriaNombre() == null || m.getCategoriaNombre().isBlank()) continue;
            YearMonth mm = YearMonth.of(m.getAnioAsignado(), m.getMesAsignado());
            String cat = m.getCategoriaNombre();
            if (mm.equals(ym)) {
                actual.merge(cat, m.getCantidad(), Double::sum);
            } else if (!mm.isAfter(ym.minusMonths(1)) && !mm.isBefore(ym.minusMonths(3))) {
                previos.merge(cat, m.getCantidad(), Double::sum);
                mesesVistos.computeIfAbsent(cat, k -> new java.util.HashSet<>()).add(mm);
            }
        }
        record Cand(String cat, double actual, double media) {}
        List<Cand> candidatos = new ArrayList<>();
        for (Map.Entry<String, Double> e : actual.entrySet()) {
            String cat = e.getKey();
            int meses = mesesVistos.getOrDefault(cat, java.util.Set.of()).size();
            if (meses < 2) continue; // sin historial suficiente no hay "lo habitual"
            double media = previos.getOrDefault(cat, 0.0) / 3.0;
            if (media <= 0) continue;
            if (e.getValue() >= IMPORTE_MINIMO_ATIPICO && e.getValue() > media * FACTOR_ATIPICO) {
                candidatos.add(new Cand(cat, e.getValue(), media));
            }
        }
        candidatos.sort(Comparator.comparingDouble((Cand c) -> c.actual() - c.media()).reversed());
        for (Cand c : candidatos.stream().limit(1).toList()) {
            long pct = Math.round((c.actual() / c.media() - 1) * 100);
            Texto t = new Texto(fmt).t("En " + c.cat() + " llevas ").m(c.actual())
                    .t(" este mes, un " + pct + " % más que tu media de los últimos 3 meses (").m(c.media()).t(").");
            out.add(new Insight("atipico-" + c.cat().toLowerCase(ES).replaceAll("[^a-z0-9áéíóúñ]+", "-") + "-" + ym,
                    "atipico", "aviso", 60, t.build(), ym.atEndOfMonth()));
        }
    }

    /** Lo ingresado este mes, o aviso si aún no ha entrado nada y es lo habitual. */
    private void reglaIngresos(List<Insight> out, List<MovimientoDTO> movs, LocalDate hoy, YearMonth ym, MoneyFormatter fmt) {
        double ingresosMes = 0;
        double ingresosPrevios = 0;
        int mesesConIngresos = 0;
        Map<YearMonth, Double> porMes = new HashMap<>();
        for (MovimientoDTO m : movs) {
            if (!m.isIngreso()) continue;
            YearMonth mm = YearMonth.of(m.getAnioAsignado(), m.getMesAsignado());
            if (mm.equals(ym)) {
                ingresosMes += m.getCantidad();
            } else if (!mm.isAfter(ym.minusMonths(1)) && !mm.isBefore(ym.minusMonths(3))) {
                porMes.merge(mm, m.getCantidad(), Double::sum);
            }
        }
        for (double v : porMes.values()) {
            ingresosPrevios += v;
            mesesConIngresos++;
        }
        double media = mesesConIngresos > 0 ? ingresosPrevios / mesesConIngresos : 0;
        LocalDate finMes = ym.atEndOfMonth();

        if (ingresosMes > 0) {
            Texto t = new Texto(fmt).t("Llevas ").m(ingresosMes).t(" ingresados este mes");
            if (media > 0) t.t(" (lo habitual: ").m(media).t(")");
            out.add(new Insight("ingresos-" + ym, "ingresos", "positivo", 40, t.t(".").build(), finMes));
        } else if (media > 0 && hoy.getDayOfMonth() >= 5) {
            Texto t = new Texto(fmt).t("Aún no has registrado ingresos este mes; lo habitual son unos ").m(media).t(".");
            out.add(new Insight("sin-ingresos-" + ym, "ingresos", "aviso", 55, t.build(), finMes));
        }
    }

    // ------------------------------------------------------------------ utilidades

    private boolean delMes(MovimientoDTO m, YearMonth ym) {
        return m.getAnioAsignado() == ym.getYear() && m.getMesAsignado() == ym.getMonthValue();
    }

    /** Suma de importes de las ocurrencias de recurrentes (ingreso o gasto) entre dos fechas, ambas incluidas. */
    private double sumaRecurrentes(List<MovimientoRecurrenteDTO> recs, boolean ingreso, LocalDate desde, LocalDate hasta) {
        double total = 0;
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            for (MovimientoRecurrenteDTO r : recs) {
                if (r.isIngreso() == ingreso && ocurreEn(r, d)) total += r.getCantidad();
            }
        }
        return total;
    }

    /**
     * ¿Genera este recurrente un movimiento en esa fecha? Replica la regla del planificador
     * (EncryptedMovimientoService.debeEjecutarseHoy) para que lo que se avisa coincida con lo
     * que realmente se cobrará, incluida su limitación de "mismo día del mes".
     */
    private boolean ocurreEn(MovimientoRecurrenteDTO rec, LocalDate d) {
        LocalDate inicio = rec.getFechaInicio();
        if (inicio == null || d.isBefore(inicio)) return false;
        if (rec.getFechaFin() != null && d.isAfter(rec.getFechaFin())) return false;
        String f = rec.getFrecuencia();
        if (f == null) return false;
        switch (f) {
            case "semana":
                return ChronoUnit.DAYS.between(inicio, d) % 7 == 0;
            case "dos_semanas":
                return ChronoUnit.DAYS.between(inicio, d) % 14 == 0;
            case "mes":
                return d.getDayOfMonth() == inicio.getDayOfMonth();
            case "dos_meses":
                return d.getDayOfMonth() == inicio.getDayOfMonth()
                        && ChronoUnit.MONTHS.between(inicio.withDayOfMonth(1), d.withDayOfMonth(1)) % 2 == 0;
            case "anio":
                return d.getDayOfMonth() == inicio.getDayOfMonth() && d.getMonthValue() == inicio.getMonthValue();
            default:
                return false;
        }
    }
}
