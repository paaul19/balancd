package com.balancdapp.controller;

import com.balancdapp.model.Movimiento;
import com.balancdapp.model.User;
import com.balancdapp.service.EncryptedMovimientoRecurrenteService;
import com.balancdapp.service.EncryptedMovimientoService;
import com.balancdapp.service.MovimientoService;
import com.balancdapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.ArrayList;

@Controller
public class MovimientoController {
    @Autowired
    private MovimientoService movimientoService;

    @Autowired
    private EncryptedMovimientoService encryptedMovimientoService;

    @Autowired
    private EncryptedMovimientoRecurrenteService encryptedRecurrenteService;

    @Autowired
    private UserService userService;

    @GetMapping("/movimientos")
    public String verMovimientos(@RequestParam(value = "mes", required = false) Integer mes,
                                 @RequestParam(value = "anio", required = false) Integer anio,
                                 @RequestParam(value = "busqueda", required = false) String busqueda,
                                 @RequestParam(value = "tutorialVisto", required = false) Integer tutorialVisto,
                                 Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        // Recuperar el usuario actualizado de la base de datos
        user = userService.getUserById(user.getId()).orElse(user);
        // Si viene el parámetro y el usuario aún no lo tenía marcado, lo marcamos
        if (tutorialVisto != null && tutorialVisto == 1 && !user.isTutorialVisto()) {
            user.setTutorialVisto(true);
            userService.saveUser(user);
            session.setAttribute("user", user);
        }

        // Usar el servicio cifrado para obtener movimientos
        List<EncryptedMovimientoService.MovimientoDTO> todos = encryptedMovimientoService.getMovimientosByUserId(user.getId());

        // Filtrar por búsqueda de asunto si se proporciona
        if (busqueda != null && !busqueda.trim().isEmpty()) {
            String busquedaLower = busqueda.trim().toLowerCase();
            todos = todos.stream()
                    .filter(m -> m.getAsunto() != null && m.getAsunto().toLowerCase().contains(busquedaLower))
                    .collect(java.util.stream.Collectors.toList());
        }

        // Obtener todos los meses/años únicos con movimientos
        List<YearMonth> mesesDisponibles = new ArrayList<>(todos.stream()
                .map(m -> YearMonth.of(m.getAnioAsignado(), m.getMesAsignado()))
                .distinct()
                .collect(java.util.stream.Collectors.toList()));

        // Añadir meses manuales
        Set<YearMonth> mesesManuales = movimientoService.getMesesManuales(user.getId());
        mesesDisponibles.addAll(mesesManuales);

        // Ordenar y quitar duplicados
        mesesDisponibles = new ArrayList<>(mesesDisponibles.stream().distinct().sorted((a, b) -> b.compareTo(a)).toList());

        // Añadir el mes actual si no está
        YearMonth mesActual = YearMonth.now();
        if (!mesesDisponibles.contains(mesActual)) {
            mesesDisponibles.add(0, mesActual);
        }
        mesesDisponibles = new ArrayList<>(mesesDisponibles.stream().distinct().sorted((a, b) -> b.compareTo(a)).toList());

        // Si no se especifica mes/año, usar el actual
        YearMonth actual = YearMonth.now();
        YearMonth seleccionado = (mes != null && anio != null) ? YearMonth.of(anio, mes) : (mesesDisponibles.isEmpty() ? actual : mesesDisponibles.get(0));

        // Filtrar movimientos del mes/año seleccionado (ordenar por fecha descendente y luego por ID descendente para que los más recientes aparezcan primero)
        List<EncryptedMovimientoService.MovimientoDTO> movimientos = todos.stream()
                .filter(m -> m.getMesAsignado() == seleccionado.getMonthValue() && m.getAnioAsignado() == seleccionado.getYear())
                .sorted((a, b) -> {
                    int fechaComparison = b.getFecha().compareTo(a.getFecha());
                    if (fechaComparison != 0) {
                        return fechaComparison;
                    }
                    // Si las fechas son iguales, ordenar por ID descendente (más reciente primero)
                    return Long.compare(b.getId(), a.getId());
                })
                .toList();

        double totalIngresos = movimientos.stream().filter(EncryptedMovimientoService.MovimientoDTO::isIngreso).mapToDouble(EncryptedMovimientoService.MovimientoDTO::getCantidad).sum();
        double totalGastos = movimientos.stream().filter(m -> !m.isIngreso()).mapToDouble(EncryptedMovimientoService.MovimientoDTO::getCantidad).sum();
        double balance = totalIngresos - totalGastos;

        model.addAttribute("movimientos", movimientos);
        model.addAttribute("totalIngresos", totalIngresos);
        model.addAttribute("totalGastos", totalGastos);
        model.addAttribute("balance", balance);
        model.addAttribute("nuevoMovimiento", new Movimiento());
        model.addAttribute("mesesDisponibles", mesesDisponibles);
        model.addAttribute("mesSeleccionado", seleccionado);
        model.addAttribute("mesesManuales", mesesManuales);
        model.addAttribute("busqueda", busqueda);
        model.addAttribute("tutorialVisto", user.isTutorialVisto());
        model.addAttribute("user", user);
        return "movimientos/lista";
    }

    @PostMapping("/movimientos/add")
    public String addMovimiento(@RequestParam Double cantidad,
                                @RequestParam(required = false) String asunto,
                                @RequestParam Boolean ingreso,
                                @RequestParam String fecha,
                                @RequestParam(value = "mes", required = false) Integer mes,
                                @RequestParam(value = "anio", required = false) Integer anio,
                                @RequestParam(value = "categoria", required = false) String categoria,
                                HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        LocalDate fechaMovimiento = LocalDate.parse(fecha);
        int mesAsignado = (mes != null) ? mes : fechaMovimiento.getMonthValue();
        int anioAsignado = (anio != null) ? anio : fechaMovimiento.getYear();
        encryptedMovimientoService.createMovimiento(user, cantidad, ingreso, asunto != null ? asunto.trim() : "", fechaMovimiento, mesAsignado, anioAsignado, categoria);
        // Actualizar user en sesión
        User actualizado = userService.getUserById(user.getId()).orElse(user);
        session.setAttribute("user", actualizado);
        return "redirect:/movimientos?mes=" + mesAsignado + "&anio=" + anioAsignado;
    }

    @PostMapping("/movimientos/delete/{id}")
    public String deleteMovimiento(@PathVariable Long id, @RequestParam(value = "mes", required = false) Integer mes,
                                   @RequestParam(value = "anio", required = false) Integer anio, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        movimientoService.deleteMovimiento(id, user.getId());
        // Actualizar user en sesión
        User actualizado = userService.getUserById(user.getId()).orElse(user);
        session.setAttribute("user", actualizado);
        if (mes != null && anio != null) {
            return "redirect:/movimientos?mes=" + mes + "&anio=" + anio;
        } else {
            return "redirect:/movimientos";
        }
    }

    @PostMapping("/movimientos/edit/{id}")
    public String editMovimiento(@PathVariable Long id,
                                 @RequestParam Double cantidad,
                                 @RequestParam(required = false) String asunto,
                                 @RequestParam Boolean ingreso,
                                 @RequestParam String fecha,
                                 @RequestParam(value = "categoria", required = false) String categoria,
                                 HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        Movimiento movimiento = movimientoService.getMovimientoById(id);
        if (movimiento == null || !movimiento.getUser().getId().equals(user.getId())) {
            return "redirect:/movimientos";
        }
        LocalDate fechaMovimiento = LocalDate.parse(fecha);
        encryptedMovimientoService.updateMovimiento(movimiento, cantidad, asunto != null ? asunto.trim() : "", ingreso, fechaMovimiento, categoria);
        // Actualizar user en sesión
        User actualizado = userService.getUserById(user.getId()).orElse(user);
        session.setAttribute("user", actualizado);
        return "redirect:/movimientos";
    }

    @PostMapping("/movimientos/crear-mes")
    public String crearMesManual(@RequestParam int referenciaMes,
                                 @RequestParam int referenciaAnio,
                                 @RequestParam String direccion,
                                 HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        int nuevoMes, nuevoAnio;
        if ("anterior".equals(direccion)) {
            if (referenciaMes == 1) {
                nuevoMes = 12;
                nuevoAnio = referenciaAnio - 1;
            } else {
                nuevoMes = referenciaMes - 1;
                nuevoAnio = referenciaAnio;
            }
        } else {
            if (referenciaMes == 12) {
                nuevoMes = 1;
                nuevoAnio = referenciaAnio + 1;
            } else {
                nuevoMes = referenciaMes + 1;
                nuevoAnio = referenciaAnio;
            }
        }
        // Guardar el mes manualmente
        movimientoService.crearMesManual(user.getId(), nuevoMes, nuevoAnio);
        return "redirect:/movimientos?mes=" + nuevoMes + "&anio=" + nuevoAnio;
    }

    @PostMapping("/movimientos/eliminar-mes")
    public String eliminarMesManual(@RequestParam int mes, @RequestParam int anio, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        // Eliminar todos los movimientos de ese mes
        List<Movimiento> movimientos = movimientoService.getMovimientosByUserId(user.getId());
        movimientos.stream()
                .filter(m -> m.getMesAsignado() == mes && m.getAnioAsignado() == anio)
                .forEach(m -> movimientoService.deleteMovimiento(m.getId(), user.getId()));
        // Borra el mes manual si existe
        movimientoService.eliminarMesManual(user.getId(), mes, anio);
        return "redirect:/movimientos";
    }

    @GetMapping("/movimientos/busqueda-avanzada")
    public String busquedaAvanzada(@RequestParam(value = "busqueda", required = false) String busqueda,
                                   @RequestParam(value = "tipo", required = false, defaultValue = "todos") String tipo,
                                   @RequestParam(value = "periodo", required = false, defaultValue = "12") Integer periodo,
                                   Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        List<EncryptedMovimientoService.MovimientoDTO> todos = encryptedMovimientoService.getMovimientosByUserId(user.getId());
        // Filtrar por asunto
        if (busqueda != null && !busqueda.trim().isEmpty()) {
            String busquedaLower = busqueda.trim().toLowerCase();
            todos = todos.stream()
                    .filter(m -> m.getAsunto() != null && m.getAsunto().toLowerCase().contains(busquedaLower))
                    .toList();
        }
        // Filtrar por periodo
        LocalDate hoy = LocalDate.now();
        LocalDate desde;
        if (periodo == 3) {
            desde = hoy.minusMonths(3).withDayOfMonth(1);
        } else if (periodo == 6) {
            desde = hoy.minusMonths(6).withDayOfMonth(1);
        } else if (periodo == 12) {
            desde = hoy.minusMonths(12).withDayOfMonth(1);
        } else if (periodo == 0) { // año actual
            desde = hoy.withDayOfYear(1);
        } else {
            desde = hoy.minusMonths(12).withDayOfMonth(1);
        }
        todos = todos.stream()
                .filter(m -> m.getFecha().isAfter(desde.minusDays(1)))
                .toList();
        // Agrupar por mes/año
        var resumenPorMes = new java.util.TreeMap<java.time.YearMonth, java.util.List<EncryptedMovimientoService.MovimientoDTO>>();
        for (var mov : todos) {
            var ym = java.time.YearMonth.of(mov.getAnioAsignado(), mov.getMesAsignado());
            resumenPorMes.computeIfAbsent(ym, k -> new java.util.ArrayList<>()).add(mov);
        }
        // Calcular totales por mes
        var resumen = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (var entry : resumenPorMes.entrySet()) {
            var ym = entry.getKey();
            var lista = entry.getValue();
            double ingresos = lista.stream().filter(EncryptedMovimientoService.MovimientoDTO::isIngreso).mapToDouble(EncryptedMovimientoService.MovimientoDTO::getCantidad).sum();
            double gastos = lista.stream().filter(m -> !m.isIngreso()).mapToDouble(EncryptedMovimientoService.MovimientoDTO::getCantidad).sum();
            double balance = ingresos - gastos;
            // Filtro por tipo
            boolean mostrar = switch (tipo) {
                case "ingresos" -> ingresos > 0;
                case "gastos" -> gastos > 0;
                case "balance" -> balance != 0;
                default -> true;
            };
            if (mostrar) {
                var map = new java.util.HashMap<String, Object>();
                map.put("mes", ym);
                map.put("ingresos", ingresos);
                map.put("gastos", gastos);
                map.put("balance", balance);
                map.put("movimientos", lista);
                resumen.add(map);
            }
        }
        resumen.sort((a, b) -> ((java.time.YearMonth) b.get("mes")).compareTo((java.time.YearMonth) a.get("mes")));
        model.addAttribute("resumen", resumen);
        model.addAttribute("busqueda", busqueda);
        model.addAttribute("tipo", tipo);
        model.addAttribute("periodo", periodo);
        // Calcular totales globales
        double totalIngresos = todos.stream().filter(EncryptedMovimientoService.MovimientoDTO::isIngreso).mapToDouble(EncryptedMovimientoService.MovimientoDTO::getCantidad).sum();
        double totalGastos = todos.stream().filter(m -> !m.isIngreso()).mapToDouble(EncryptedMovimientoService.MovimientoDTO::getCantidad).sum();
        double totalBalance = totalIngresos - totalGastos;
        model.addAttribute("totalIngresos", totalIngresos);
        model.addAttribute("totalGastos", totalGastos);
        model.addAttribute("totalBalance", totalBalance);
        return "movimientos/busqueda-avanzada";
    }

    @PostMapping("/movimientos/recurrente")
    public String addMovimientoRecurrente(@RequestParam Double cantidad,
                                          @RequestParam String asunto,
                                          @RequestParam Boolean ingreso,
                                          @RequestParam String fecha,
                                          @RequestParam String frecuencia,
                                          @RequestParam(value = "categoria", required = false) String categoria,
                                          @RequestParam(required = false) Integer repeticiones,
                                          @RequestParam(required = false) String fechaFin,
                                          HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        LocalDate fechaInicio = LocalDate.parse(fecha);
        LocalDate fechaFinParsed = (fechaFin != null && !fechaFin.isEmpty()) ? LocalDate.parse(fechaFin) : null;
        encryptedMovimientoService.crearMovimientoRecurrente(user, cantidad, ingreso, asunto != null ? asunto.trim() : "", fechaInicio, frecuencia, repeticiones, fechaFinParsed, categoria);
        return "redirect:/movimientos";
    }

    @GetMapping("/movimientos/recurrentes")
    public String listarRecurrentes(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        List<EncryptedMovimientoRecurrenteService.MovimientoRecurrenteDTO> recurrentes = encryptedRecurrenteService.getRecurrentesByUser(user);
        model.addAttribute("recurrentes", recurrentes);
        return "movimientos/recurrentes";
    }

    @GetMapping("/movimientos/recurrentes/terminar/{id}")
    public String terminarRecurrente(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        encryptedMovimientoService.terminarMovimientoRecurrente(id, user);
        return "redirect:/movimientos/recurrentes";
    }

    @GetMapping("/movimientos/recurrentes/borrar/{id}")
    public String borrarRecurrente(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        encryptedMovimientoService.borrarMovimientoRecurrente(id, user);
        return "redirect:/movimientos/recurrentes";
    }

    @PostMapping("/movimientos/recurrentes/modificar/{id}")
    public String modificarRecurrente(@PathVariable Long id,
                                      @RequestParam Double cantidad,
                                      @RequestParam String asunto,
                                      @RequestParam Boolean ingreso,
                                      @RequestParam String fechaInicio,
                                      @RequestParam String frecuencia,
                                      @RequestParam(value = "categoria", required = false) String categoria,
                                      HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        LocalDate fechaInicioParsed = LocalDate.parse(fechaInicio);
        encryptedMovimientoService.modificarMovimientoRecurrente(id, user, cantidad, asunto, ingreso, fechaInicioParsed, frecuencia, categoria);

        return "redirect:/movimientos/recurrentes";
    }

    @GetMapping("/debug/fecha")
    public String debugFecha(Model model) {
        LocalDate hoy = LocalDate.now();
        model.addAttribute("fecha", hoy);
        model.addAttribute("zonaHoraria", java.time.ZoneId.systemDefault());
        model.addAttribute("timestamp", java.time.LocalDateTime.now());
        return "debug/fecha";
    }

    @GetMapping("/debug/procesar-recurrentes")
    public String procesarRecurrentesManualmente(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        // Ejecutar el scheduler manualmente
        encryptedMovimientoService.procesarMovimientosRecurrentes();

        model.addAttribute("mensaje", "Scheduler ejecutado manualmente");
        model.addAttribute("fecha", LocalDate.now());
        return "debug/fecha";
    }
}