package service;

import model.Sala;
import protocol.Evento;
import support.FakeConexao;
import support.InMemoryMensagemRepository;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocol.Evento.Tipo.*;

class SalaServiceTest {
    private final Sala estudos = new Sala("estudos", "Estudos");
    private final Map<Sala, InMemoryMensagemRepository> repositories = new LinkedHashMap<>();
    private final SalaService central;
    SalaServiceTest() {
        Map<Sala, ChatService> conversas = new LinkedHashMap<>();
        for (var sala : List.of(Sala.GERAL, estudos)) {
            var repository = new InMemoryMensagemRepository(); repositories.put(sala, repository);
            conversas.put(sala, new ChatService(repository, new ClientRegistry(),
                    Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC), 50, sala));
        }
        central = new SalaService(conversas);
    }
    private FakeConexao cliente(String nome) { var c = new FakeConexao(nome); central.conectar(c); return c; }
    private List<Evento> eventos(FakeConexao c, Evento.Tipo tipo) { return c.recebidos.stream().filter(e -> e.tipo() == tipo).toList(); }
    @Test void entradaPublicaSalasComContagemEIdentificaSalaAtual() {
        var ana = cliente("Ana"); var boasVindas = eventos(ana, BEM_VINDO).get(0);
        assertEquals("geral", boasVindas.campo(1));
        var catalogo = eventos(ana, SALAS).get(0);
        assertEquals(List.of("geral", "geral", "Geral", "1", "estudos", "Estudos", "0"), catalogo.campos());
        assertEquals(5, SalaService.salasPadrao().size());
    }
    @Test void broadcastFicaSomenteNaSalaAtual() {
        var ana = cliente("Ana"); var bruno = cliente("Bruno"); var carla = cliente("Carla");
        central.trocarSala(bruno, "estudos"); ana.limpar(); bruno.limpar(); carla.limpar();
        central.executar(ana, "Somente Geral");
        assertEquals(1, eventos(ana, MENSAGEM).size()); assertEquals(1, eventos(carla, MENSAGEM).size());
        assertTrue(eventos(bruno, MENSAGEM).isEmpty());
    }
    @Test void trocarDeSalaRecuperaSomenteOHistoricoCorrespondente() {
        var ana = cliente("Ana"); central.executar(ana, "Texto da Geral"); central.trocarSala(ana, "estudos");
        central.executar(ana, "Texto dos Estudos"); ana.limpar(); central.trocarSala(ana, "geral");
        assertEquals(List.of("Texto da Geral"), eventos(ana, MENSAGEM).stream().map(e -> e.campo(2)).toList());
        assertEquals(1, repositories.get(estudos).buscarUltimas(50).size());
    }
    @Test void privadaEDigitacaoNaoAtravesamSalas() {
        var ana = cliente("Ana"); var bruno = cliente("Bruno"); var carla = cliente("Carla");
        central.trocarSala(bruno, "estudos"); ana.limpar(); bruno.limpar(); carla.limpar();
        assertThrows(IllegalArgumentException.class, () -> central.executar(ana, "/msg Bruno Segredo"));
        central.executar(ana, "/typing");
        assertTrue(eventos(bruno, PRIVADA).isEmpty()); assertTrue(eventos(bruno, DIGITANDO).isEmpty());
        assertEquals(1, eventos(carla, DIGITANDO).size());
    }
    @Test void salaInexistenteMantemParticipanteNaOrigem() {
        var ana = cliente("Ana"); var bruno = cliente("Bruno"); bruno.limpar();
        assertThrows(IllegalArgumentException.class, () -> central.trocarSala(ana, "inexistente"));
        central.executar(ana, "Continuo na Geral"); assertEquals("Continuo na Geral", eventos(bruno, MENSAGEM).get(0).campo(2));
    }
    @Test void apelidoDuplicadoNoDestinoNaoRetiraDaSalaAtual() {
        var anaEstudos = cliente("Ana Silva"); central.trocarSala(anaEstudos, "estudos");
        var anaGeral = cliente("Ana Silva"); var bruno = cliente("Bruno"); anaEstudos.limpar(); bruno.limpar();
        assertThrows(IllegalArgumentException.class, () -> central.trocarSala(anaGeral, "estudos"));
        central.executar(anaGeral, "Ainda na Geral");
        assertEquals(1, eventos(bruno, MENSAGEM).size()); assertTrue(eventos(anaEstudos, MENSAGEM).isEmpty());
    }
    @Test void sairRemoveParticipanteDeTodasAsPublicacoes() {
        var ana = cliente("Ana"); var bruno = cliente("Bruno"); central.desconectar(ana); ana.limpar();
        central.executar(bruno, "Olá"); central.executar(bruno, "/salas"); assertTrue(ana.recebidos.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> central.executar(ana, "Não conectado"));
    }
    @Test void falhaNoHistoricoDoDestinoPreservaSalaAtual() {
        persistence.MensagemRepository comFalha = new persistence.MensagemRepository() {
            public model.Mensagem salvar(model.Mensagem m) { throw new persistence.PersistenciaException("Indisponível", new RuntimeException()); }
            public List<model.Mensagem> buscarUltimas(int n) { throw new persistence.PersistenciaException("Indisponível", new RuntimeException()); }
            public List<model.Mensagem> buscarPorTexto(String t, int n) { return List.of(); }
        };
        var geral = new ChatService(new InMemoryMensagemRepository(), new ClientRegistry(), Clock.systemUTC(), 50, Sala.GERAL);
        var destino = new ChatService(comFalha, new ClientRegistry(), Clock.systemUTC(), 50, estudos);
        Map<Sala, ChatService> conversas = new LinkedHashMap<>(); conversas.put(Sala.GERAL, geral); conversas.put(estudos, destino);
        var servico = new SalaService(conversas); var ana = new FakeConexao("Ana"); var bruno = new FakeConexao("Bruno");
        servico.conectar(ana); servico.conectar(bruno); ana.limpar(); bruno.limpar();
        assertThrows(persistence.PersistenciaException.class, () -> servico.trocarSala(ana, "estudos"));
        assertTrue(eventos(ana, BEM_VINDO).isEmpty()); servico.executar(ana, "Continuo na Geral");
        assertEquals("Continuo na Geral", eventos(bruno, MENSAGEM).get(0).campo(2)); assertEquals(2, geral.quantidadeUsuarios());
        assertEquals(0, destino.quantidadeUsuarios());
    }
    @Test void comandosListamSalasEMudamSemRepetirEntradaNaMesmaSala() {
        var ana = cliente("Ana"); ana.limpar(); central.executar(ana, "/join estudos");
        assertEquals("estudos", eventos(ana, BEM_VINDO).get(0).campo(1));
        ana.limpar(); central.executar(ana, "/join estudos"); central.executar(ana, "/rooms");
        assertTrue(eventos(ana, BEM_VINDO).isEmpty()); assertEquals(2, eventos(ana, SALAS).size());
        assertThrows(IllegalArgumentException.class, () -> central.executar(ana, "/join"));
    }
    @Test void trocasConcorrentesNaoEntregamMensagensDaSalaErrada() throws Exception {
        var ana = cliente("Ana"); var bruno = cliente("Bruno"); ana.limpar(); bruno.limpar();
        var inicio = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var mover = executor.submit(() -> { aguardar(inicio); for (int i = 0; i < 30; i++) central.trocarSala(ana, i % 2 == 0 ? "estudos" : "geral"); });
            var enviar = executor.submit(() -> { aguardar(inicio); for (int i = 0; i < 60; i++) central.executar(bruno, "Mensagem " + i); });
            inicio.countDown(); mover.get(10, TimeUnit.SECONDS); enviar.get(10, TimeUnit.SECONDS);
            String salaAtual = "geral";
            for (var evento : ana.recebidos) {
                if (evento.tipo() == BEM_VINDO) salaAtual = evento.campo(1);
                if (evento.tipo() == MENSAGEM) assertEquals("geral", salaAtual, "Mensagem da Geral apareceu em outra sala");
            }
            assertEquals(60, eventos(bruno, MENSAGEM).size());
            assertEquals(60, repositories.get(Sala.GERAL).buscarUltimas(100).size());
            assertTrue(repositories.get(estudos).buscarUltimas(100).isEmpty());
        } finally { executor.shutdownNow(); }
    }
    private void aguardar(CountDownLatch inicio) { try { inicio.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); } }
}
