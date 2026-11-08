package client;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Preserva a ordem da rede e limita o trabalho executado de uma vez na interface. */
final class FilaInterface implements AutoCloseable {
    static final int TAMANHO_LOTE = 32;
    private final Queue<Runnable> tarefas = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean agendada = new AtomicBoolean();
    private final Executor agendar;
    private volatile boolean fechada;

    FilaInterface(Executor agendar) {
        this.agendar = agendar;
    }

    void enfileirar(Runnable tarefa) {
        if (fechada) return;
        tarefas.add(tarefa);
        if (fechada) {
            tarefas.clear();
            return;
        }
        agendarLote();
    }

    private void agendarLote() {
        if (agendada.compareAndSet(false, true)) {
            try {
                agendar.execute(this::processar);
            } catch (RuntimeException e) {
                agendada.set(false);
                throw e;
            }
        }
    }

    private void processar() {
        try {
            for (int i = 0; i < TAMANHO_LOTE && !fechada; i++) {
                Runnable tarefa = tarefas.poll();
                if (tarefa == null) break;
                tarefa.run();
            }
        } finally {
            agendada.set(false);
            if (!fechada && !tarefas.isEmpty()) agendarLote();
        }
    }

    @Override
    public void close() {
        fechada = true;
        tarefas.clear();
    }
}
