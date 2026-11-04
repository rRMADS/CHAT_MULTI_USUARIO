package service;
import model.Mensagem;
import persistence.MensagemRepository;
import persistence.PersistenciaException;
import protocol.Evento;
import support.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static protocol.Evento.Tipo.*;

class ChatServiceTest {
    private final InMemoryMensagemRepository repository = new InMemoryMensagemRepository();
    private final ClientRegistry registry = new ClientRegistry();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T18:30:00Z"), ZoneOffset.UTC);
    private final ChatService chat = new ChatService(repository, registry, clock, 50);
    @Test void salvaAntesDeEntregarATodosInclusiveRemetente() {
        var ana = cliente("Ana"); var bruno = cliente("Bruno"); ana.limpar(); bruno.limpar();
        var salva = chat.enviarMensagem(ana, "Olá");
        assertEquals(1L, salva.id()); assertEquals(LocalDateTime.of(2026, 11, 4, 18, 30), salva.horario());
        assertEquals("Olá", repository.buscarUltimas(1).get(0).conteudo());
        assertEquals(ana.recebidos, bruno.recebidos); assertEquals(MENSAGEM, ana.recebidos.get(0).tipo());
    }
    @Test void removidoNaoRecebeMensagem() { var ana = cliente("Ana"); var bruno = cliente("Bruno"); chat.desconectar(bruno); bruno.limpar(); chat.enviarMensagem(ana, "oi"); assertTrue(bruno.recebidos.isEmpty()); }
    @Test void novoClienteRecebeHistoricoEmOrdem() {
        var ana = cliente("Ana"); chat.enviarMensagem(ana, "primeira"); chat.enviarMensagem(ana, "segunda");
        var bruno = cliente("Bruno");
        assertEquals(List.of("primeira", "segunda"), bruno.recebidos.stream().filter(e -> e.tipo() == MENSAGEM).map(e -> e.campo(2)).toList());
    }
    @Test void falhaNoBancoNaoPublicaMensagem() {
        MensagemRepository comFalha = new MensagemRepository() {
            public Mensagem salvar(Mensagem m) { throw new PersistenciaException("Falha simulada", new RuntimeException()); }
            public List<Mensagem> buscarUltimas(int n) { return List.of(); }
            public List<Mensagem> buscarPorTexto(String t, int n) { return List.of(); }
        };
        var servico = new ChatService(comFalha, new ClientRegistry(), clock, 50); var ana = new FakeConexao("Ana"); servico.conectar(ana); ana.limpar();
        assertThrows(PersistenciaException.class, () -> servico.enviarMensagem(ana, "oi")); assertTrue(ana.recebidos.isEmpty());
    }
    @Test void clienteComFalhaNaoInterrompeOsDemais() {
        var ana = cliente("Ana"); var bruno = cliente("Bruno"); var carla = cliente("Carla"); bruno.falhar = true; carla.limpar();
        chat.enviarMensagem(ana, "oi"); assertTrue(bruno.fechada); assertEquals(2, registry.quantidade());
        assertTrue(carla.recebidos.stream().anyMatch(e -> e.tipo() == MENSAGEM));
    }
    @Test void privadaVaiSomenteParaOsDoisENaoEntraNoHistorico() {
        var ana = cliente("Ana"); var bruno = cliente("Bruno"); var carla = cliente("Carla"); ana.limpar(); bruno.limpar(); carla.limpar();
        chat.enviarPrivada(ana, "bruno", "Segredo"); assertEquals(ana.recebidos, bruno.recebidos); assertEquals(PRIVADA, bruno.recebidos.get(0).tipo());
        assertTrue(carla.recebidos.isEmpty()); assertTrue(repository.buscarUltimas(50).isEmpty());
    }
    @Test void privadaRecusaDestinoInexistenteOuProprioUsuario() { var ana = cliente("Ana"); assertThrows(IllegalArgumentException.class, () -> chat.enviarPrivada(ana, "Offline", "oi")); assertThrows(IllegalArgumentException.class, () -> chat.enviarPrivada(ana, "ana", "oi")); }
    @Test void historicoRecusaQuantidadesInvalidas() { var ana = cliente("Ana"); assertThrows(IllegalArgumentException.class, () -> chat.buscarHistorico(ana, 201)); assertThrows(IllegalArgumentException.class, () -> chat.buscarHistorico(ana, 0)); }
    @Test void apelidoUnicoEAtomicoEntreThreads() throws Exception {
        var inicio = new CountDownLatch(1); var sucessos = new AtomicInteger(); var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> acao = () -> { inicio.await(); try { registry.adicionar(new FakeConexao("Ana")); sucessos.incrementAndGet(); } catch (IllegalArgumentException esperado) { } return null; };
            var a = executor.submit(acao); var b = executor.submit(acao); inicio.countDown(); a.get(5, TimeUnit.SECONDS); b.get(5, TimeUnit.SECONDS);
            assertEquals(1, sucessos.get()); assertEquals(1, registry.quantidade());
        } finally { executor.shutdownNow(); }
    }
    @Test void enviosConcorrentesPreservamOrdemEQuantidade() throws Exception {
        var ana = cliente("Ana"); var bruno = cliente("Bruno"); ana.limpar(); bruno.limpar();
        var inicio = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(6);
        try {
            var tarefas = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 60; i++) { int n = i; tarefas.add(executor.submit(() -> { try { inicio.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); } chat.enviarMensagem(ana, "Mensagem " + n); })); }
            inicio.countDown(); for (var t : tarefas) t.get(10, TimeUnit.SECONDS);
            assertEquals(60, ana.recebidos.size()); assertEquals(ana.recebidos, bruno.recebidos);
            assertEquals(repository.buscarUltimas(100).stream().map(m -> m.id().toString()).toList(), bruno.recebidos.stream().map(e -> e.campo(0)).toList());
        } finally { executor.shutdownNow(); }
    }
    @Test void comandoDesconhecidoNaoViraMensagemPublica() { var ana = cliente("Ana"); var comandos = new CommandService(chat); assertThrows(IllegalArgumentException.class, () -> comandos.executar(ana, "/naoexiste")); assertTrue(repository.buscarUltimas(50).isEmpty()); }
    @Test void comandoPrivadoReconheceNomeCompletoSemEnviarAoNomeParcial() {
        var origem = cliente("Maria Souza"); var ana = cliente("Ana"); var anaSilva = cliente("Ana Silva");
        origem.limpar(); ana.limpar(); anaSilva.limpar();
        new CommandService(chat).executar(origem, "/msg \"Ana Silva\" Olá, Ana!");
        assertEquals("Ana Silva", anaSilva.recebidos.get(0).campo(1));
        assertEquals("Olá, Ana!", anaSilva.recebidos.get(0).campo(2));
        assertEquals(origem.recebidos, anaSilva.recebidos); assertTrue(ana.recebidos.isEmpty());
    }
    @Test void comandoPrivadoMantemCompatibilidadeComApelidoSemEspaco() {
        var origem = cliente("Maria Souza"); var ana = cliente("Ana"); ana.limpar();
        new CommandService(chat).executar(origem, "/msg Ana Olá!");
        assertEquals(PRIVADA, ana.recebidos.get(0).tipo()); assertEquals("Olá!", ana.recebidos.get(0).campo(2));
    }
    @Test void comandoPrivadoRecusaAspasIncompletasOuAusenciaDeMensagem() {
        var origem = cliente("Maria Souza"); var ana = cliente("Ana Silva"); ana.limpar();
        var comandos = new CommandService(chat);
        assertThrows(IllegalArgumentException.class, () -> comandos.executar(origem, "/msg \"Ana Silva Olá"));
        assertThrows(IllegalArgumentException.class, () -> comandos.executar(origem, "/msg \"Ana Silva\""));
        assertThrows(IllegalArgumentException.class, () -> comandos.executar(origem, "/msg \"Ana Silva\"Olá"));
        assertTrue(ana.recebidos.isEmpty());
    }
    @Test void nomesComEspacosNormalizadosNaoPermitemDuplicatas() {
        cliente("Ana Silva");
        assertThrows(IllegalArgumentException.class, () -> cliente("  ANA   SILVA  "));
        assertEquals(1, registry.quantidade());
    }
    private FakeConexao cliente(String nome) { var c = new FakeConexao(nome); chat.conectar(c); return c; }
}
