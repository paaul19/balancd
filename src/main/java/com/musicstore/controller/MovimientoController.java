package com.musicstore.controller;

import com.musicstore.model.Movimiento;
import com.musicstore.model.User;
import com.musicstore.service.MovimientoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;

@Controller
public class MovimientoController {
    @Autowired
    private MovimientoService movimientoService;

    @GetMapping("/movimientos")
    public String verMovimientos(@RequestParam(value = "mes", required = false) Integer mes,
                                 @RequestParam(value = "anio", required = false) Integer anio,
                                 Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        List<Movimiento> todos = movimientoService.getMovimientosByUserId(user.getId());
        // Obtener todos los meses/años únicos con movimientos
        List<YearMonth> mesesDisponibles = todos.stream()
            .map(m -> YearMonth.from(m.getFecha() != null ? m.getFecha() : LocalDate.now()))
            .distinct()
            .sorted((a, b) -> b.compareTo(a)) // Descendente: el más reciente primero
            .toList();
        // Si no se especifica mes/año, usar el actual
        YearMonth actual = YearMonth.now();
        YearMonth seleccionado = (mes != null && anio != null) ? YearMonth.of(anio, mes) : (mesesDisponibles.isEmpty() ? actual : mesesDisponibles.get(0));
        // Filtrar movimientos del mes/año seleccionado (ordenar por id descendente)
        List<Movimiento> movimientos = todos.stream()
            .filter(m -> YearMonth.from(m.getFecha() != null ? m.getFecha() : LocalDate.now()).equals(seleccionado))
            .sorted((a, b) -> b.getId().compareTo(a.getId()))
            .toList();
        double totalIngresos = movimientos.stream().filter(Movimiento::isIngreso).mapToDouble(Movimiento::getCantidad).sum();
        double totalGastos = movimientos.stream().filter(m -> !m.isIngreso()).mapToDouble(Movimiento::getCantidad).sum();
        double balance = totalIngresos - totalGastos;
        model.addAttribute("movimientos", movimientos);
        model.addAttribute("totalIngresos", totalIngresos);
        model.addAttribute("totalGastos", totalGastos);
        model.addAttribute("balance", balance);
        model.addAttribute("nuevoMovimiento", new Movimiento());
        model.addAttribute("mesesDisponibles", mesesDisponibles);
        model.addAttribute("mesSeleccionado", seleccionado);
        return "movimientos/lista";
    }

    @PostMapping("/movimientos/add")
    public String addMovimiento(@ModelAttribute Movimiento nuevoMovimiento, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        nuevoMovimiento.setUserId(user.getId());
        nuevoMovimiento.setFecha(LocalDate.now());
        movimientoService.addMovimiento(nuevoMovimiento);
        return "redirect:/movimientos";
    }

    @PostMapping("/movimientos/delete/{id}")
    public String deleteMovimiento(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        movimientoService.deleteMovimiento(id, user.getId());
        return "redirect:/movimientos";
    }

    @PostMapping("/movimientos/edit/{id}")
    public String editMovimiento(@PathVariable Long id, 
                                @RequestParam Double cantidad,
                                @RequestParam(required = false) String asunto,
                                @RequestParam Boolean ingreso,
                                HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        
        Movimiento movimiento = movimientoService.getMovimientoById(id);
        if (movimiento == null || !movimiento.getUserId().equals(user.getId())) {
            return "redirect:/movimientos";
        }
        
        movimiento.setCantidad(cantidad);
        movimiento.setAsunto(asunto != null ? asunto.trim() : "");
        movimiento.setIngreso(ingreso);
        
        movimientoService.updateMovimiento(movimiento);
        return "redirect:/movimientos";
    }
}