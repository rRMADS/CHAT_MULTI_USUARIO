package service;

import static org.junit.jupiter.api.Assertions.*;

import static protocol.Evento.Tipo.*;

import model.*;

import org.junit.jupiter.api.*;

import persistence.jdbc.*;

import protocol.*;

import support.FakeConexao;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class NovosRecursosTest {
    final ConnectionFactory f =
            new ConnectionFactory(
                    "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    final JdbcMensagemRepository geral = new JdbcMensagemRepository(f),
            estudos = new JdbcMensagemRepository(f, new Sala("estudos", "Estudos"));
    final FakeConexao ana = new FakeConexao("Ana Silva"),
            bruno = new FakeConexao("Bruno"),
            carla = new FakeConexao("Carla");
    final AtomicReference<String> prompt = new AtomicReference<>();
    final SalaService salas;

    NovosRecursosTest() {
        geral.inicializar();
        var mapa = new LinkedHashMap<Sala, ChatService>();
        mapa.put(Sala.GERAL, new ChatService(geral, new ClientRegistry(), Clock.systemUTC(), 50));
        mapa.put(
                new Sala("estudos", "Estudos"),
                new ChatService(
                        estudos,
                        new ClientRegistry(),
                        Clock.systemUTC(),
                        50,
                        new Sala("estudos", "Estudos")));
        salas =
                new SalaService(
                        mapa,
                        new ia.AssistenteIA(p -> {
                            prompt.set(p);
                            return "Resumo controlado";
                        }));
        salas.conectar(ana);
        salas.conectar(bruno);
        salas.conectar(carla);
        salas.trocarSala(carla, "estudos");
        ana.limpar();
        bruno.limpar();
        carla.limpar();
    }

    @AfterEach
    void fechar() {
        salas.close();
    }

    void evento(FakeConexao c, Evento.Tipo t, String... campos) {
        salas.executar(c, Protocolo.PREFIXO_PEDIDO + Protocolo.codificar(Evento.de(t, campos)));
    }

    void aguardar(FakeConexao c, Evento.Tipo t) throws Exception {
        long fim = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < fim) {
            if (c.recebidos.stream().anyMatch(e -> e.tipo() == t)) return;
            Thread.sleep(5);
        }
        fail("Evento não recebido: " + t);
    }

    String iniciar(String nome, byte[] dados) {
        String id = UUID.randomUUID().toString();
        evento(
                ana,
                ARQUIVO_INICIO,
                id,
                nome,
                Integer.toString(dados.length),
                Anexo.tipoDe(nome),
                "1");
        return id;
    }

    Mensagem arquivo(String nome, byte[] dados) {
        String id = iniciar(nome, dados);
        for (int i = 0, n = 0; i < dados.length; i += 768, n++)
            evento(
                    ana,
                    ARQUIVO_PARTE,
                    id,
                    Integer.toString(n),
                    Base64.getEncoder()
                            .encodeToString(
                                    Arrays.copyOfRange(dados, i, Math.min(i + 768, dados.length))));
        evento(ana, ARQUIVO_FIM, id);
        return geral.buscarUltimas(1).get(0);
    }

    @Test
    void arquivoPersistidoEhPublicadoUmaVezEBaixadoSemVazarParaOutraSala() throws Exception {
        byte[] dados =
                "Material da faculdade. "
                        .repeat(120)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var m = arquivo("aula.txt", dados);
        assertNotNull(m.anexo());
        assertArrayEquals(dados, geral.lerAnexo(m.anexo().id()).orElseThrow());
        assertEquals(1, bruno.recebidos.stream().filter(e -> e.tipo() == MENSAGEM).count());
        assertTrue(carla.recebidos.stream().allMatch(e -> e.tipo() == ATIVIDADE_SALA));
        var recebido =
                bruno.recebidos.stream()
                        .filter(e -> e.tipo() == MENSAGEM)
                        .findFirst()
                        .orElseThrow();
        assertEquals(m.anexo(), MensagensProtocolo.ler(recebido).anexo());
        evento(bruno, ARQUIVO_PEDIR, m.anexo().id());
        aguardar(bruno, DOWNLOAD_FIM);
        var bytes = new java.io.ByteArrayOutputStream();
        bruno.recebidos.stream()
                .filter(e -> e.tipo() == DOWNLOAD_PARTE)
                .forEach(e -> bytes.writeBytes(Base64.getDecoder().decode(e.campo(2))));
        assertArrayEquals(dados, bytes.toByteArray());
        evento(carla, ARQUIVO_PEDIR, m.anexo().id());
        assertTrue(carla.recebidos.stream().anyMatch(e -> e.tipo() == ARQUIVO_ERRO));
        assertTrue(carla.recebidos.stream().noneMatch(e -> e.tipo() == DOWNLOAD_PARTE));
    }

    @Test
    void envioIncompletoForaDeOrdemOuGrandeNaoCriaMensagens() {
        byte[] dados = new byte[4];
        String id = iniciar("a.txt", dados);
        evento(ana, ARQUIVO_FIM, id);
        assertTrue(geral.buscarUltimas(10).isEmpty());
        id = iniciar("a.txt", dados);
        evento(ana, ARQUIVO_PARTE, id, "1", Base64.getEncoder().encodeToString(dados));
        evento(ana, ARQUIVO_FIM, id);
        assertTrue(geral.buscarUltimas(10).isEmpty());
        evento(
                ana,
                ARQUIVO_INICIO,
                UUID.randomUUID().toString(),
                "a.txt",
                Integer.toString(Anexo.LIMITE + 1),
                "text/plain",
                "1");
        assertTrue(geral.buscarUltimas(10).isEmpty());
        assertTrue(ana.recebidos.stream().filter(e -> e.tipo() == ARQUIVO_ERRO).count() >= 3);
    }

    @Test
    void trocarSalaECancelarDescartamEnvioParcial() {
        String id = iniciar("a.txt", new byte[5]);
        salas.trocarSala(ana, "estudos");
        evento(ana, ARQUIVO_FIM, id);
        assertTrue(geral.buscarUltimas(10).isEmpty());
        assertTrue(estudos.buscarUltimas(10).isEmpty());
        salas.trocarSala(ana, "geral");
        id = iniciar("b.txt", new byte[4]);
        evento(ana, ARQUIVO_CANCELAR, id);
        evento(ana, ARQUIVO_FIM, id);
        assertTrue(geral.buscarUltimas(10).isEmpty());
    }

    @Test
    void limpezaApagaBlobEEvitaPublicarUploadAnterior() {
        var m = arquivo("a.txt", new byte[] {1, 2, 3});
        String pendente = iniciar("b.txt", new byte[4]);
        salas.executar(ana, "/limpar");
        salas.executar(ana, "/limpar confirmar");
        evento(ana, ARQUIVO_PARTE, pendente, "0", Base64.getEncoder().encodeToString(new byte[4]));
        evento(ana, ARQUIVO_FIM, pendente);
        assertTrue(geral.lerAnexo(m.anexo().id()).isEmpty());
        assertTrue(geral.buscarUltimas(10).isEmpty());
    }

    @Test
    void filtrosCombinamAutorTextoEDatasInclusiveSemExporResultadoAOutros() {
        geral.salvar(
                new Mensagem(
                        null,
                        new Usuario("Ana Silva"),
                        "Prova de redes",
                        LocalDateTime.of(2026, 11, 7, 23, 59)));
        geral.salvar(
                new Mensagem(
                        null,
                        new Usuario("Bruno"),
                        "Prova de Java",
                        LocalDateTime.of(2026, 11, 7, 12, 0)));
        geral.salvar(
                new Mensagem(
                        null,
                        new Usuario("Ana Silva"),
                        "Outra prova",
                        LocalDateTime.of(2026, 11, 8, 0, 0)));
        evento(ana, BUSCA_FILTROS, "prova", "ana silva", "2026-11-07", "2026-11-07");
        var resultados = ana.recebidos.stream().filter(e -> e.tipo() == MENSAGEM).toList();
        assertEquals(1, resultados.size());
        assertEquals("Prova de redes", resultados.get(0).campo(2));
        assertTrue(bruno.recebidos.isEmpty());
        evento(ana, BUSCA_FILTROS, "", "", "2026-11-08", "2026-11-07");
        assertTrue(ana.recebidos.stream().anyMatch(e -> e.tipo() == ERRO));
    }

    @Test
    void resumoPrivadoEnviaSomenteSelecaoENaoPersiste() throws Exception {
        salas.executar(ana, "Trecho selecionado");
        long id = geral.buscarUltimas(1).get(0).id();
        salas.executar(ana, "Mensagem que não deve ir à IA");
        ana.limpar();
        bruno.limpar();
        carla.limpar();
        evento(ana, RESUMIR, "privado", Long.toString(id));
        aguardar(ana, IA_RESPOSTA);
        assertTrue(prompt.get().contains("Trecho selecionado"));
        assertFalse(prompt.get().contains("Mensagem que não deve ir à IA"));
        assertTrue(bruno.recebidos.isEmpty());
        assertTrue(carla.recebidos.isEmpty());
        assertEquals(2, geral.buscarUltimas(50).size());
    }

    @Test
    void resumoPublicoCompartilhaResultadoMasNaoOTextoDoPrompt() throws Exception {
        salas.executar(ana, "Decisão selecionada");
        long id = geral.buscarUltimas(1).get(0).id();
        ana.limpar();
        bruno.limpar();
        evento(ana, RESUMIR, "sala", Long.toString(id));
        aguardar(ana, IA_RESPOSTA);
        var m = geral.buscarUltimas(2);
        assertTrue(m.get(0).conteudo().contains("Resumo de 1"));
        assertEquals("Resumo controlado", m.get(1).conteudo());
        assertEquals("Orkut IA", m.get(1).remetente().nome());
        assertEquals(2, bruno.recebidos.stream().filter(e -> e.tipo() == MENSAGEM).count());
    }

    @Test
    void resumoRecusaOutraSalaDuplicatasEListaExcessiva() {
        salas.executar(carla, "Segredo da outra sala");
        long id = estudos.buscarUltimas(1).get(0).id();
        evento(ana, RESUMIR, "privado", Long.toString(id));
        salas.executar(ana, "Minha sala");
        long meu = geral.buscarUltimas(1).get(0).id();
        evento(ana, RESUMIR, "privado", meu + "," + meu);
        evento(
                ana,
                RESUMIR,
                "privado",
                String.join(",", Collections.nCopies(11, Long.toString(meu))));
        assertNull(prompt.get());
        assertEquals(3, ana.recebidos.stream().filter(e -> e.tipo() == IA_ERRO).count());
    }
}
