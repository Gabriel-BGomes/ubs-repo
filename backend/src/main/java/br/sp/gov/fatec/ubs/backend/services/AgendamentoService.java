package br.sp.gov.fatec.ubs.backend.services;

import br.sp.gov.fatec.ubs.backend.model.Agendamento;
import br.sp.gov.fatec.ubs.backend.model.Medico;
import br.sp.gov.fatec.ubs.backend.model.Paciente;
import br.sp.gov.fatec.ubs.backend.repositories.AgendamentoRepository;
import br.sp.gov.fatec.ubs.backend.repositories.MedicoRepository;
import br.sp.gov.fatec.ubs.backend.repositories.PacienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import br.sp.gov.fatec.ubs.backend.services.FilaEsperaService.EntradaFila;

@Service
public class AgendamentoService {
    
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    
    @Autowired
    private MedicoRepository medicoRepository;
    
    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private FilaEsperaService filaEsperaService;
    
    // Criar novo agendamento
    public Agendamento criarAgendamento(Long pacienteId, Long medicoId, LocalDateTime dataHora, String tipoConsulta, String observacoes) {
        // Verificar se paciente existe
        Optional<Paciente> paciente = pacienteRepository.findById(pacienteId);
        if (paciente.isEmpty()) {
            throw new IllegalArgumentException("Paciente não encontrado");
        }
        
        // Verificar se médico existe e está ativo
        Optional<Medico> medico = medicoRepository.findById(medicoId);
        if (medico.isEmpty()) {
            throw new IllegalArgumentException("Médico não encontrado");
        }
        
        if (!medico.get().isAtivo()) {
            throw new IllegalArgumentException("Médico não está ativo para agendamentos");
        }
        
        // Verificar se médico tem disponibilidade
        Long conflitos = agendamentoRepository.countByMedicoAndDataHora(medicoId, dataHora);
        if (conflitos > 0) {
            throw new IllegalArgumentException("Médico não disponível neste horário");
        }
        
        // Verificar se o horário não é no passado
        if (dataHora.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Não é possível agendar para horários no passado");
        }
        
        // Criar agendamento
        Agendamento agendamento = new Agendamento(paciente.get(), medico.get(), dataHora, tipoConsulta);
        agendamento.setObservacoes(observacoes);
        
        return agendamentoRepository.save(agendamento);
    }
    
    // Listar todos os agendamentos
    public List<Agendamento> listarTodos() {
        return agendamentoRepository.findAll();
    }
    
    // Buscar agendamento por ID
    public Optional<Agendamento> buscarPorId(Long id) {
        return agendamentoRepository.findById(id);
    }
    
    // Listar agendamentos por paciente
    public List<Agendamento> listarPorPaciente(Long pacienteId) {
        return agendamentoRepository.findByPacienteId(pacienteId);
    }
    
    // Listar agendamentos por médico
    public List<Agendamento> listarPorMedico(Long medicoId) {
        return agendamentoRepository.findByMedicoId(medicoId);
    }
    
    // Listar agendamentos por data
    public List<Agendamento> listarPorData(LocalDateTime data) {
        return agendamentoRepository.findByDataConsulta(data);
    }
    
    // Listar agendamentos entre datas
    public List<Agendamento> listarEntreDatas(LocalDateTime inicio, LocalDateTime fim) {
        return agendamentoRepository.findByDataHoraConsultaBetween(inicio, fim);
    }
    
    // Atualizar status do agendamento
    public Agendamento atualizarStatus(Long id, Agendamento.StatusAgendamento novoStatus) {
        Optional<Agendamento> agendamento = agendamentoRepository.findById(id);
        if (agendamento.isEmpty()) {
            throw new IllegalArgumentException("Agendamento não encontrado");
        }
        
        Agendamento.StatusAgendamento statusAnterior = agendamento.get().getStatus();
        agendamento.get().setStatus(novoStatus);
        Agendamento salvo = agendamentoRepository.save(agendamento.get());
        
        // Se o horário acabou de ser liberado por um cancelamento, chama o próximo da fila de espera
        if (novoStatus == Agendamento.StatusAgendamento.CANCELADO
                && statusAnterior != Agendamento.StatusAgendamento.CANCELADO) {
            promoverProximoDaFila(salvo.getMedico().getId(), salvo.getDataHoraConsulta());
        }
        return salvo;
    }
    
    // Cancelar agendamento
    public Agendamento cancelarAgendamento(Long id) {
        return atualizarStatus(id, Agendamento.StatusAgendamento.CANCELADO);
    }
    
    // Confirmar agendamento
    public Agendamento confirmarAgendamento(Long id) {
        return atualizarStatus(id, Agendamento.StatusAgendamento.CONFIRMADO);
    }
    
    // Marcar como realizado
    public Agendamento marcarComoRealizado(Long id) {
        return atualizarStatus(id, Agendamento.StatusAgendamento.REALIZADO);
    }
    
    // Marcar falta
    public Agendamento marcarFalta(Long id) {
        return atualizarStatus(id, Agendamento.StatusAgendamento.FALTOU);
    }
    
    // Atualizar observações
    public Agendamento atualizarObservacoes(Long id, String observacoes) {
        Optional<Agendamento> agendamento = agendamentoRepository.findById(id);
        if (agendamento.isEmpty()) {
            throw new IllegalArgumentException("Agendamento não encontrado");
        }
        
        agendamento.get().setObservacoes(observacoes);
        return agendamentoRepository.save(agendamento.get());
    }
    
    // Buscar próximos agendamentos do paciente
    public List<Agendamento> buscarProximosAgendamentos(Long pacienteId) {
        return agendamentoRepository.findProximosAgendamentosPaciente(pacienteId, LocalDateTime.now());
    }
    
    // Buscar próximos agendamentos do médico
    public List<Agendamento> buscarProximosAgendamentosMedico(Long medicoId) {
        return agendamentoRepository.findProximosAgendamentosMedico(medicoId, LocalDateTime.now());
    }
    
    // Verificar disponibilidade do médico
    public boolean verificarDisponibilidade(Long medicoId, LocalDateTime dataHora) {
        Long conflitos = agendamentoRepository.countByMedicoAndDataHora(medicoId, dataHora);
        return conflitos == 0;
    }
    
    // Reagendar consulta
    public Agendamento reagendar(Long id, LocalDateTime novaDataHora) {
        Optional<Agendamento> agendamentoOpt = agendamentoRepository.findById(id);
        if (agendamentoOpt.isEmpty()) {
            throw new IllegalArgumentException("Agendamento não encontrado");
        }
        
        Agendamento agendamento = agendamentoOpt.get();
        
        // Verificar se o novo horário está disponível
        if (!verificarDisponibilidade(agendamento.getMedico().getId(), novaDataHora)) {
            throw new IllegalArgumentException("Médico não disponível no novo horário");
        }
        
        // Verificar se o novo horário não é no passado
        if (novaDataHora.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Não é possível reagendar para horários no passado");
        }
        
        LocalDateTime horarioAntigo = agendamento.getDataHoraConsulta();
        agendamento.setDataHoraConsulta(novaDataHora);
        agendamento.setStatus(Agendamento.StatusAgendamento.AGENDADO);
        
        Agendamento salvo = agendamentoRepository.save(agendamento);
        
        // O horário antigo ficou livre: chama o próximo da fila de espera dele
        promoverProximoDaFila(salvo.getMedico().getId(), horarioAntigo);
        return salvo;
    }
    
    // Deletar agendamento
    public void deletarAgendamento(Long id) {
        Optional<Agendamento> agendamento = agendamentoRepository.findById(id);
        if (agendamento.isEmpty()) {
            throw new IllegalArgumentException("Agendamento não encontrado");
        }
        
        Long medicoId = agendamento.get().getMedico().getId();
        LocalDateTime dataHora = agendamento.get().getDataHoraConsulta();
        boolean estavaCancelado = agendamento.get().getStatus() == Agendamento.StatusAgendamento.CANCELADO;
        
        agendamentoRepository.deleteById(id);
        
        // Se o agendamento ainda ocupava o horário, ele acabou de ser liberado
        if (!estavaCancelado) {
            promoverProximoDaFila(medicoId, dataHora);
        }
    }
    
    // ==========================
    // FILA DE ESPERA (estrutura de dados: Fila / Queue)
    // ==========================
    
    // Paciente entra na fila de espera de um horário que já está ocupado
    public int entrarNaFilaDeEspera(Long pacienteId, Long medicoId, LocalDateTime dataHora,
                                    String tipoConsulta, String observacoes) {
        if (pacienteRepository.findById(pacienteId).isEmpty()) {
            throw new IllegalArgumentException("Paciente não encontrado");
        }
        
        Optional<Medico> medico = medicoRepository.findById(medicoId);
        if (medico.isEmpty()) {
            throw new IllegalArgumentException("Médico não encontrado");
        }
        if (!medico.get().isAtivo()) {
            throw new IllegalArgumentException("Médico não está ativo para agendamentos");
        }
        
        if (dataHora.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Não é possível entrar na fila de um horário no passado");
        }
        
        // Só faz sentido esperar por um horário que está ocupado
        if (verificarDisponibilidade(medicoId, dataHora)) {
            throw new IllegalArgumentException("Horário está livre: faça o agendamento diretamente");
        }
        
        // O paciente não pode esperar por um horário que ele mesmo já ocupa
        boolean ehDonoDoHorario = agendamentoRepository.findByPacienteId(pacienteId).stream()
                .anyMatch(a -> a.getMedico().getId().equals(medicoId)
                        && a.getDataHoraConsulta().equals(dataHora)
                        && a.getStatus() != Agendamento.StatusAgendamento.CANCELADO);
        if (ehDonoDoHorario) {
            throw new IllegalArgumentException("Paciente já possui agendamento neste horário");
        }
        
        return filaEsperaService.entrar(medicoId, dataHora, pacienteId, tipoConsulta, observacoes);
    }
    
    // Paciente desiste da espera
    public boolean sairDaFilaDeEspera(Long pacienteId, Long medicoId, LocalDateTime dataHora) {
        return filaEsperaService.sair(medicoId, dataHora, pacienteId);
    }
    
    // Consulta a fila de um horário (posição 1 = índice 0)
    public List<EntradaFila> listarFilaDeEspera(Long medicoId, LocalDateTime dataHora) {
        return filaEsperaService.listar(medicoId, dataHora);
    }
    
    // Quando um horário libera, agenda automaticamente o primeiro da fila (FIFO).
    // synchronized: evita que duas liberações simultâneas do mesmo horário chamem a fila ao mesmo tempo.
    private synchronized void promoverProximoDaFila(Long medicoId, LocalDateTime dataHora) {
        while (true) {
            // Se outra pessoa já ocupou o horário nesse meio tempo, a fila continua intacta
            if (!verificarDisponibilidade(medicoId, dataHora)) {
                return;
            }
            
            Optional<EntradaFila> proximo = filaEsperaService.espiarProximo(medicoId, dataHora);
            if (proximo.isEmpty()) {
                return;
            }
            
            EntradaFila entrada = proximo.get();
            try {
                String obs = (entrada.observacoes() == null || entrada.observacoes().isBlank())
                        ? "Agendado automaticamente pela fila de espera"
                        : entrada.observacoes() + " (agendado automaticamente pela fila de espera)";
                criarAgendamento(entrada.pacienteId(), medicoId, dataHora, entrada.tipoConsulta(), obs);
                filaEsperaService.sair(medicoId, dataHora, entrada.pacienteId());
                return;
            } catch (IllegalArgumentException e) {
                // Horário foi ocupado por outra pessoa durante a tentativa: mantém a fila
                if (!verificarDisponibilidade(medicoId, dataHora)) {
                    return;
                }
                // Senão a entrada ficou inválida (paciente removido, médico inativo, horário já passou...):
                // descarta e tenta o próximo
                filaEsperaService.sair(medicoId, dataHora, entrada.pacienteId());
            }
        }
    }
}