package com.balancdapp.controller;

import com.balancdapp.model.User;
import com.balancdapp.service.EncryptedCuentaService;
import com.balancdapp.service.EncryptedMovimientoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** gráfico de gastos por categoría de un mes, navegable mes a mes y filtrable por cuenta. */
@Controller
public class EstadisticasController {

    /** colores en orden, se asignan a las categorías con más gasto */
    private static final String[] PALETA = {
            "#32d399", "#9b8cff", "#ff6f66", "#f5c400", "#4fb3e8",
            "#e8798f", "#7fd67a", "#c98bf5", "#e8a13b", "#5fd4c4"
    };
    private static final String COLOR_SIN_CATEGORIA = "#8a8a92";

    @Autowired
    private EncryptedMovimientoService encryptedMovimientoService;

    @Autowired
    private EncryptedCuentaService encryptedCuentaService;

    @GetMapping("/estadisticas")
    public String estadisticas(@RequestParam(required = false) Integer mes,
                                @RequestParam(required = false) Integer anio,
                                @RequestParam(value = "cuenta", required = false) Long cuentaId,
                                HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        YearMonth seleccionado = (mes != null && anio != null) ? YearMonth.of(anio, mes) : YearMonth.now();

        // incluye las desactivadas: sus gastos siguen contando en meses pasados
        List<EncryptedCuentaService.CuentaDTO> cuentas = encryptedCuentaService.getCuentasByUser(user);
        // id ajeno o inexistente -> todas las cuentas
        final Long cuentaFiltro = (cuentaId != null && cuentas.stream().anyMatch(c -> c.getId().equals(cuentaId)))
                ? cuentaId : null;

        List<EncryptedMovimientoService.MovimientoDTO> gastosDelMes = encryptedMovimientoService.getMovimientosByUserId(user.getId()).stream()
                .filter(m -> !m.isIngreso())
                .filter(m -> m.getMesAsignado() == seleccionado.getMonthValue() && m.getAnioAsignado() == seleccionado.getYear())
                .filter(m -> cuentaFiltro == null || cuentaFiltro.equals(m.getCuentaId()))
                .toList();

        // icono/color van por categoría, no por movimiento, para que leyenda y porciones cuadren
        Map<String, Double> gastoPorCategoria = new LinkedHashMap<>();
        Map<String, String> iconoPorCategoria = new LinkedHashMap<>();
        for (var m : gastosDelMes) {
            String nombre = (m.getCategoriaNombre() != null && !m.getCategoriaNombre().isBlank())
                    ? m.getCategoriaNombre() : "Sin categoría";
            gastoPorCategoria.merge(nombre, m.getCantidad(), Double::sum);
            iconoPorCategoria.putIfAbsent(nombre, m.getCategoriaIcono());
        }

        double totalGastado = gastoPorCategoria.values().stream().mapToDouble(Double::doubleValue).sum();

        List<Map<String, Object>> segmentos = new ArrayList<>();
        List<String> nombresOrdenados = gastoPorCategoria.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        StringBuilder gradiente = new StringBuilder();
        double acumulado = 0;
        int colorIdx = 0;
        for (String nombre : nombresOrdenados) {
            double monto = gastoPorCategoria.get(nombre);
            double porcentaje = totalGastado > 0 ? (monto / totalGastado) * 100.0 : 0;
            String color = "Sin categoría".equals(nombre) ? COLOR_SIN_CATEGORIA : PALETA[colorIdx % PALETA.length];
            if (!"Sin categoría".equals(nombre)) colorIdx++;

            double inicio = acumulado;
            acumulado += porcentaje;

            if (gradiente.length() > 0) gradiente.append(", ");
            gradiente.append(color).append(' ').append(String.format(java.util.Locale.ROOT, "%.4f", inicio)).append('%')
                    .append(' ').append(String.format(java.util.Locale.ROOT, "%.4f", acumulado)).append('%');

            Map<String, Object> segmento = new LinkedHashMap<>();
            segmento.put("nombre", nombre);
            segmento.put("icono", iconoPorCategoria.get(nombre));
            segmento.put("monto", monto);
            segmento.put("porcentaje", porcentaje);
            segmento.put("color", color);
            segmentos.add(segmento);
        }

        model.addAttribute("resumenMes", resumenDelMes(gastosDelMes, seleccionado, totalGastado));

        model.addAttribute("cuentas", cuentas);
        model.addAttribute("cuentaSeleccionada", cuentaFiltro);
        model.addAttribute("mesSeleccionado", seleccionado);
        model.addAttribute("mesAnterior", seleccionado.minusMonths(1));
        model.addAttribute("mesSiguiente", seleccionado.plusMonths(1));
        model.addAttribute("segmentos", segmentos);
        model.addAttribute("totalGastado", totalGastado);
        model.addAttribute("gradiente", gradiente.toString());
        return "estadisticas";
    }

    /**
     * Datos de la tarjeta "Gasto del mes": total, media diaria, mayor gasto, nº de movimientos y
     * el gasto de cada día (para las barras). Los días que aún no han llegado (mes en curso) y los
     * días sin gasto se pintan como una raya, no como una barra.
     */
    private Map<String, Object> resumenDelMes(List<EncryptedMovimientoService.MovimientoDTO> gastos, YearMonth ym, double total) {
        int diasMes = ym.lengthOfMonth();
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("Europe/Madrid"));
        YearMonth actual = YearMonth.from(hoy);
        int diasTranscurridos = ym.isBefore(actual) ? diasMes : (ym.equals(actual) ? hoy.getDayOfMonth() : 0);

        double[] porDia = new double[diasMes];
        double mayor = 0;
        for (var m : gastos) {
            int dia = Math.max(1, Math.min(m.getFecha().getDayOfMonth(), diasMes));
            porDia[dia - 1] += m.getCantidad();
            mayor = Math.max(mayor, m.getCantidad());
        }
        double maxDia = 0;
        for (double v : porDia) maxDia = Math.max(maxDia, v);

        List<Map<String, Object>> barras = new ArrayList<>();
        for (int d = 1; d <= diasMes; d++) {
            double v = porDia[d - 1];
            boolean futuro = d > diasTranscurridos;
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("dia", d);
            b.put("raya", futuro || v <= 0);
            // altura mínima para que un gasto pequeño siga viéndose como barra
            b.put("altura", maxDia > 0 && !futuro && v > 0 ? Math.max(6.0, v / maxDia * 100.0) : 0.0);
            barras.add(b);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", total);
        r.put("mediaDiaria", diasTranscurridos > 0 ? total / diasTranscurridos : 0.0);
        r.put("mayorGasto", mayor);
        r.put("movimientos", gastos.size());
        r.put("barras", barras);
        return r;
    }
}
