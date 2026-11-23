package br.sp.gov.fatec.ubs.backend;

import br.sp.gov.fatec.ubs.backend.services.FilaEsperaService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class FilaEsperaServiceTest {

    private final LocalDateTime horario = LocalDateTime.of(2026, 12, 1, 10, 0);

    @Test
    void respeitaOrdemDeChegada() {
        FilaEsperaService fila = new FilaEsperaService();
        assertEquals(1, fila.entrar(1L, horario, 10L, "Clínico", ""));
        assertEquals(2, fila.entrar(1L, horario, 20L, "Clínico", ""));
        assertEquals(10L, fila.espiarProximo(1L, horario).orElseThrow().pacienteId());
    }

    @Test
    void cadaMedicoEHorarioTemFilaPropria() {
        FilaEsperaService fila = new FilaEsperaService();
        fila.entrar(1L, horario, 10L, "Clínico", "");
        assertEquals(1, fila.entrar(1L, horario.plusHours(1), 20L, "Clínico", ""));
        assertEquals(1, fila.entrar(2L, horario, 30L, "Clínico", ""));
    }

    @Test
    void naoPermitePacienteDuplicado() {
        FilaEsperaService fila = new FilaEsperaService();
        fila.entrar(1L, horario, 10L, "Clínico", "");
        assertThrows(IllegalArgumentException.class, () -> fila.entrar(1L, horario, 10L, "Clínico", ""));
    }

    @Test
    void sairRemoveDaFilaEPromoveOProximo() {
        FilaEsperaService fila = new FilaEsperaService();
        fila.entrar(1L, horario, 10L, "Clínico", "");
        fila.entrar(1L, horario, 20L, "Clínico", "");
        assertTrue(fila.sair(1L, horario, 10L));
        assertEquals(20L, fila.espiarProximo(1L, horario).orElseThrow().pacienteId());
        assertFalse(fila.sair(1L, horario, 999L));
    }
}
