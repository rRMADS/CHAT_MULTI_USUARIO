package client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

class FilaInterfaceTest {
    @Test
    void rajadaMantemOrdemSemAgendarUmaAtualizacaoPorEvento() {
        var agendadas = new ArrayDeque<Runnable>();
        var recebidas = new ArrayList<Integer>();
        try (var fila = new FilaInterface(agendadas::add)) {
            for (int i = 0; i < 200; i++) {
                int n = i;
                fila.enfileirar(() -> recebidas.add(n));
            }
            assertEquals(1, agendadas.size());
            agendadas.remove().run();
            assertEquals(FilaInterface.TAMANHO_LOTE, recebidas.size());
            assertEquals(1, agendadas.size());
            while (!agendadas.isEmpty()) agendadas.remove().run();
            assertEquals(java.util.stream.IntStream.range(0, 200).boxed().toList(), recebidas);
        }
    }

    @Test
    void fecharDescartaCallbacksPendentesETardios() {
        var agendadas = new ArrayDeque<Runnable>();
        var recebidas = new ArrayList<Integer>();
        var fila = new FilaInterface(agendadas::add);
        fila.enfileirar(() -> recebidas.add(1));
        fila.close();
        fila.enfileirar(() -> recebidas.add(2));
        while (!agendadas.isEmpty()) agendadas.remove().run();
        assertTrue(recebidas.isEmpty());
    }

    @Test
    void falhaDeUmCallbackNaoPrendeOsDemais() {
        var agendadas = new ArrayDeque<Runnable>();
        var recebidas = new ArrayList<Integer>();
        try (var fila = new FilaInterface(agendadas::add)) {
            fila.enfileirar(() -> {
                throw new IllegalStateException("Falha simulada");
            });
            fila.enfileirar(() -> recebidas.add(2));
            assertThrows(IllegalStateException.class, () -> agendadas.remove().run());
            agendadas.remove().run();
            assertEquals(List.of(2), recebidas);
        }
    }

    @Test
    void produtoresConcorrentesNaoPerdemDuplicamOuInvertemEventosDaMesmaOrigem() throws Exception {
        var agendadas = new ConcurrentLinkedQueue<Runnable>();
        var recebidas = new ArrayList<Integer>();
        var executor = Executors.newFixedThreadPool(4);
        try (var fila = new FilaInterface(agendadas::add)) {
            var tarefas = new ArrayList<Future<?>>();
            for (int p = 0; p < 4; p++) {
                int produtor = p;
                tarefas.add(
                        executor.submit(() -> {
                            for (int i = 0; i < 200; i++) {
                                int id = produtor * 1000 + i;
                                fila.enfileirar(() -> recebidas.add(id));
                            }
                        }));
            }
            for (var t : tarefas) t.get(5, TimeUnit.SECONDS);
            Runnable tarefa;
            while ((tarefa = agendadas.poll()) != null) tarefa.run();
            assertEquals(800, recebidas.size());
            assertEquals(800, new HashSet<>(recebidas).size());
            for (int p = 0; p < 4; p++) {
                int produtor = p;
                assertEquals(
                        java.util.stream.IntStream.range(0, 200).boxed().toList(),
                        recebidas.stream()
                                .filter(n -> n / 1000 == produtor)
                                .map(n -> n % 1000)
                                .toList());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recusaDoAgendadorPermiteNovaTentativaSemPerderOrdem() {
        var falhar = new AtomicBoolean(true);
        var agendadas = new ArrayDeque<Runnable>();
        var recebidas = new ArrayList<Integer>();
        try (var fila =
                new FilaInterface(t -> {
                    if (falhar.getAndSet(false)) throw new RejectedExecutionException();
                    agendadas.add(t);
                })) {
            assertThrows(
                    RejectedExecutionException.class,
                    () -> fila.enfileirar(() -> recebidas.add(1)));
            fila.enfileirar(() -> recebidas.add(2));
            agendadas.remove().run();
            assertEquals(List.of(1, 2), recebidas);
        }
    }
}
