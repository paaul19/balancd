package com.balancdapp.service;

import com.balancdapp.model.User;
import com.balancdapp.service.EncryptedMovimientoRecurrenteService.MovimientoRecurrenteDTO;
import com.balancdapp.service.EncryptedMovimientoService.MovimientoDTO;
import com.balancdapp.service.EncryptedPresupuestoService.PresupuestoDTO;
import com.balancdapp.service.InsightService.Insight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock private EncryptedMovimientoService movimientoService;
    @Mock private EncryptedMovimientoRecurrenteService recurrenteService;
    @Mock private EncryptedPresupuestoService presupuestoService;
    @InjectMocks private InsightService service;

    private final LocalDate hoy = LocalDate.of(2026, 10, 10); // octubre: 31 días, día 10
    private User user;
    private final List<MovimientoRecurrenteDTO> recurrentes = new ArrayList<>();
    private final List<PresupuestoDTO> presupuestos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        user = new User();
        user.setMoneda("EUR");
        lenient().when(recurrenteService.getRecurrentesByUser(any())).thenAnswer(i -> recurrentes);
        lenient().when(presupuestoService.getPresupuestosConProgreso(any(), anyInt(), anyInt())).thenAnswer(i -> presupuestos);
    }

    private MovimientoDTO mov(boolean ingreso, double cantidad, YearMonth ym, String categoria) {
        MovimientoDTO m = new MovimientoDTO();
        m.setIngreso(ingreso);
        m.setCantidad(cantidad);
        m.setMesAsignado(ym.getMonthValue());
        m.setAnioAsignado(ym.getYear());
        m.setFecha(ym.atDay(1));
        m.setCategoriaNombre(categoria);
        return m;
    }

    private MovimientoRecurrenteDTO rec(String asunto, double cantidad, boolean ingreso, LocalDate inicio, String frecuencia) {
        MovimientoRecurrenteDTO r = new MovimientoRecurrenteDTO();
        r.setAsunto(asunto);
        r.setCantidad(cantidad);
        r.setIngreso(ingreso);
        r.setFechaInicio(inicio);
        r.setFrecuencia(frecuencia);
        r.setActivo(true);
        return r;
    }

    private Insight tipo(List<Insight> l, String tipo) {
        return l.stream().filter(i -> i.getTipo().equals(tipo)).findFirst().orElse(null);
    }

    @Test
    void proyeccionDeCierre_usaRitmoVariableMasRecurrentesPendientes() {
        YearMonth oct = YearMonth.of(2026, 10);
        // Hasta hoy (día 10): ingresado 1000, gastado 100 sin recurrentes -> ritmo 10 €/día, quedan 21 días
        List<MovimientoDTO> movs = List.of(mov(true, 1000, oct, null), mov(false, 100, oct, "Ocio"));
        // Un gasto recurrente mensual el día 20 (10 €) aún por cobrar este mes
        recurrentes.add(rec("Gimnasio", 10, false, LocalDate.of(2026, 1, 20), "mes"));

        Insight p = tipo(service.generar(user, movs, hoy), "proyeccion");

        assertNotNull(p);
        // gastos = 100 + 10*21 + 10 = 320 ; balance = 1000 - 320 = 680
        assertTrue(p.getTexto().contains("680,00 €"), p.getTexto());
        assertTrue(p.getTexto().contains("octubre"), p.getTexto());
        assertEquals("positivo", p.getSeveridad());
        assertTrue(p.getPartes().stream().anyMatch(x -> x.isMoney() && x.getTexto().equals("680,00 €")));
    }

    @Test
    void proyeccionDeCierre_noRestaDosVecesLosRecurrentesYaCobrados() {
        YearMonth oct = YearMonth.of(2026, 10);
        // Gastado 100 en total, de los cuales 40 son un recurrente ya cobrado el día 5 -> variable = 60
        List<MovimientoDTO> movs = List.of(mov(true, 500, oct, null), mov(false, 100, oct, null));
        recurrentes.add(rec("Seguro", 40, false, LocalDate.of(2026, 1, 5), "mes"));

        Insight p = tipo(service.generar(user, movs, hoy), "proyeccion");

        // ritmo = 60/10 = 6 €/día * 21 = 126 ; gastos = 100 + 126 = 226 ; balance = 500 - 226 = 274
        assertNotNull(p);
        assertTrue(p.getTexto().contains("274,00 €"), p.getTexto());
    }

    @Test
    void proyeccionDeCierre_negativaSeAvisa() {
        YearMonth oct = YearMonth.of(2026, 10);
        List<MovimientoDTO> movs = List.of(mov(false, 300, oct, null)); // 30 €/día durante 31 días
        Insight p = tipo(service.generar(user, movs, hoy), "proyeccion");
        assertNotNull(p);
        assertEquals("aviso", p.getSeveridad());
        assertTrue(p.getTexto().contains("-"), p.getTexto());
    }

    @Test
    void proyeccionDeCierre_noSeMuestraLosPrimerosDiasNiSinDatos() {
        YearMonth oct = YearMonth.of(2026, 10);
        List<MovimientoDTO> movs = List.of(mov(false, 20, oct, null));
        assertNull(tipo(service.generar(user, movs, LocalDate.of(2026, 10, 2)), "proyeccion"));
        assertNull(tipo(service.generar(user, List.of(), hoy), "proyeccion"));
    }

    @Test
    void proximosCobros_avisaMananaYPasadoManana() {
        recurrentes.add(rec("Netflix", 12.99, false, LocalDate.of(2026, 1, 11), "mes")); // mañana
        recurrentes.add(rec("iCloud", 2.99, false, LocalDate.of(2026, 1, 12), "mes"));   // pasado mañana

        List<Insight> r = service.generar(user, List.of(), hoy);

        List<String> textos = r.stream().filter(i -> i.getTipo().equals("cobro")).map(Insight::getTexto).toList();
        assertTrue(textos.contains("Mañana se cobra Netflix (12,99 €)."), textos.toString());
        assertTrue(textos.contains("Pasado mañana se cobra iCloud (2,99 €)."), textos.toString());
    }

    @Test
    void proximosCobros_ignoraTerminadosYFuturos() {
        MovimientoRecurrenteDTO terminado = rec("Viejo", 5, false, LocalDate.of(2026, 1, 11), "mes");
        terminado.setFechaFin(LocalDate.of(2026, 9, 1));
        recurrentes.add(terminado);
        recurrentes.add(rec("Futuro", 5, false, LocalDate.of(2026, 12, 11), "mes"));
        assertNull(tipo(service.generar(user, List.of(), hoy), "cobro"));
    }

    @Test
    void presupuestos_excedidoYAlOchentaPorCiento() {
        PresupuestoDTO excedido = new PresupuestoDTO();
        excedido.setId(1L); excedido.setEtiqueta("Ocio"); excedido.setLimite(100); excedido.setGastado(130);
        PresupuestoDTO casi = new PresupuestoDTO();
        casi.setId(2L); casi.setEtiqueta("Comida"); casi.setLimite(200); casi.setGastado(170);
        presupuestos.add(excedido);
        presupuestos.add(casi);

        List<Insight> r = service.generar(user, List.of(), hoy);
        List<String> textos = r.stream().filter(i -> i.getTipo().equals("presupuesto")).map(Insight::getTexto).toList();

        assertEquals(2, textos.size());
        assertTrue(textos.get(0).startsWith("Has superado tu presupuesto de Ocio"), textos.toString());
        assertTrue(textos.get(1).startsWith("Llevas el 85 % de tu presupuesto de Comida"), textos.toString());
        assertEquals("importante", r.get(0).getSeveridad()); // el más urgente va primero
    }

    @Test
    void gastoAtipico_comparaConLaMediaDeTresMeses() {
        YearMonth oct = YearMonth.of(2026, 10);
        List<MovimientoDTO> movs = new ArrayList<>();
        for (int i = 1; i <= 3; i++) movs.add(mov(false, 100, oct.minusMonths(i), "Delivery"));
        movs.add(mov(false, 150, oct, "Delivery"));

        Insight a = tipo(service.generar(user, movs, hoy), "atipico");

        assertNotNull(a);
        assertTrue(a.getTexto().contains("En Delivery llevas"), a.getTexto());
        assertTrue(a.getTexto().contains("50 % más"), a.getTexto());
    }

    @Test
    void gastoAtipico_sinHistorialSuficienteNoAvisa() {
        YearMonth oct = YearMonth.of(2026, 10);
        List<MovimientoDTO> movs = List.of(mov(false, 100, oct.minusMonths(1), "Viajes"), mov(false, 500, oct, "Viajes"));
        assertNull(tipo(service.generar(user, movs, hoy), "atipico"));
    }

    @Test
    void ingresos_avisaSiNoHayYEsLoHabitual() {
        YearMonth oct = YearMonth.of(2026, 10);
        List<MovimientoDTO> movs = List.of(mov(true, 1200, oct.minusMonths(1), null), mov(true, 1200, oct.minusMonths(2), null));
        Insight i = tipo(service.generar(user, movs, hoy), "ingresos");
        assertNotNull(i);
        assertTrue(i.getTexto().startsWith("Aún no has registrado ingresos"), i.getTexto());
    }

    @Test
    void devuelveComoMaximoCuatroOrdenadosPorPrioridad() {
        for (int d = 11; d <= 13; d++) recurrentes.add(rec("R" + d, 1, false, LocalDate.of(2026, 1, d), "mes"));
        PresupuestoDTO p = new PresupuestoDTO();
        p.setId(1L); p.setEtiqueta("X"); p.setLimite(10); p.setGastado(20);
        presupuestos.add(p);
        YearMonth oct = YearMonth.of(2026, 10);
        List<Insight> r = service.generar(user, List.of(mov(true, 1000, oct, null), mov(false, 50, oct, null)), hoy);

        assertTrue(r.size() <= 4);
        for (int i = 1; i < r.size(); i++) {
            assertTrue(r.get(i - 1).getPrioridad() >= r.get(i).getPrioridad());
        }
    }

    @Test
    void siFallaUnaDependenciaNoRompeElHome() {
        lenient().when(recurrenteService.getRecurrentesByUser(any())).thenThrow(new RuntimeException("boom"));
        assertTrue(service.generar(user, List.of(), hoy).isEmpty());
    }
}
