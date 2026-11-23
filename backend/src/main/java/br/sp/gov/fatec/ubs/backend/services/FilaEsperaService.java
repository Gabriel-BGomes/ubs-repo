package br.sp.gov.fatec.ubs.backend.services;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

/**
 * Fila de espera por horário de consulta.
 *
 * Estrutura de dados usada: FILA (Queue, política FIFO - primeiro a entrar, primeiro a sair),
 * implementada com ArrayDeque. Existe uma fila para cada (médico + data/hora): quem entrou
 * primeiro na espera por aquele horário é o primeiro a ser atendido quando o horário liberar.
 * Um Map (HashMap) apenas organiza as várias filas, indexadas pelo horário.
 *
 * Limitações conhecidas (assumidas de propósito, por ser uma atividade didática):
 *  - Os dados ficam só na memória: se o backend reiniciar, as filas são perdidas.
 *  - Os métodos são synchronized porque o service é um singleton compartilhado entre várias
 *    requisições simultâneas e ArrayDeque/HashMap não são thread-safe.
 */
@Service
public class FilaEsperaService {

    /** Chave do mapa: um horário específico de um médico específico (record gera equals/hashCode). */
    public record Horario(Long medicoId, LocalDateTime dataHora) {}

    /** Item da fila: quem está esperando e o que quer marcar. */
    public record EntradaFila(Long pacienteId, String tipoConsulta, String observacoes, LocalDateTime entrouEm) {}

    private final Map<Horario, Queue<EntradaFila>> filas = new HashMap<>();

    /** Coloca o paciente no FIM da fila (enqueue) e devolve a posição dele (1 = próximo a ser chamado). */
    public synchronized int entrar(Long medicoId, LocalDateTime dataHora, Long pacienteId,
                                   String tipoConsulta, String observacoes) {
        Queue<EntradaFila> fila = filas.computeIfAbsent(new Horario(medicoId, dataHora), h -> new ArrayDeque<>());

        boolean jaEstaNaFila = fila.stream().anyMatch(e -> e.pacienteId().equals(pacienteId));
        if (jaEstaNaFila) {
            throw new IllegalArgumentException("Paciente já está na fila de espera deste horário");
        }

        fila.offer(new EntradaFila(pacienteId, tipoConsulta, observacoes, LocalDateTime.now()));
        return fila.size();
    }

    /** Olha quem é o primeiro da fila SEM remover (peek). */
    public synchronized Optional<EntradaFila> espiarProximo(Long medicoId, LocalDateTime dataHora) {
        Queue<EntradaFila> fila = filas.get(new Horario(medicoId, dataHora));
        return fila == null ? Optional.empty() : Optional.ofNullable(fila.peek());
    }

    /** Tira o paciente da fila, esteja ele em qualquer posição. Devolve true se ele estava na fila. */
    public synchronized boolean sair(Long medicoId, LocalDateTime dataHora, Long pacienteId) {
        Horario chave = new Horario(medicoId, dataHora);
        Queue<EntradaFila> fila = filas.get(chave);
        if (fila == null) {
            return false;
        }

        boolean removeu = fila.removeIf(e -> e.pacienteId().equals(pacienteId));

        if (fila.isEmpty()) {
            filas.remove(chave); // evita acumular filas vazias na memória
        }
        return removeu;
    }

    /** Cópia da fila na ordem de chegada (posição 1 = índice 0). */
    public synchronized List<EntradaFila> listar(Long medicoId, LocalDateTime dataHora) {
        Queue<EntradaFila> fila = filas.get(new Horario(medicoId, dataHora));
        return fila == null ? List.of() : new ArrayList<>(fila);
    }
}
