package com.balancdapp.controller;

import com.balancdapp.model.User;
import com.balancdapp.service.EncryptedMovimientoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gráfico circular de gastos por categoría de un mes, navegable mes a mes. Enlazado desde
 * /perfil (vivía antes ahí mismo, como resumen embebido; ahora es su propia página).
 */
@Controller
public class EstadisticasController {

    /** Paleta fija: colores en el orden en que se van asignando a las categorías con más gasto. */
    private static final String[] PALETA = {
            "#32d399", "#9b8cff", "#ff6f66", "#f5c400", "#4fb3e8",
            "#e8798f", "#7fd67a", "#c98bf5", "#e8a13b", "#5fd4c4"
    };
    private static final String COLOR_SIN_CATEGORIA = "#8a8a92";

    @Autowired
    private EncryptedMovimientoService encryptedMovimientoService;

    @GetMapping("/estadisticas")
    public String estadisticas(@RequestParam(required = false) Integer mes,
                                @RequestParam(required = false) Integer anio,
                                HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        YearMonth seleccionado = (mes != null && anio != null) ? YearMonth.of(anio, mes) : YearMonth.now();

        List<EncryptedMovimientoService.MovimientoDTO> gastosDelMes = encryptedMovimientoService.getMovimientosByUserId(user.getId()).stream()
                .filter(m -> !m.isIngreso())
                .filter(m -> m.getMesAsignado() == seleccionado.getMonthValue() && m.getAnioAsignado() == seleccionado.getYear())
                .toList();

        // Agrupa por categoría preservando el orden de aparición; icono/color se asignan por
        // categoría (no por movimiento) para que la leyenda y las porciones del gráfico coincidan.
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

        model.addAttribute("mesSeleccionado", seleccionado);
        model.addAttribute("mesAnterior", seleccionado.minusMonths(1));
        model.addAttribute("mesSiguiente", seleccionado.plusMonths(1));
        model.addAttribute("segmentos", segmentos);
        model.addAttribute("totalGastado", totalGastado);
        model.addAttribute("gradiente", gradiente.toString());
        return "estadisticas";
    }
}
