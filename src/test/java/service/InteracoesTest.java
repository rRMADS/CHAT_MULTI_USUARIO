package service;

import static org.junit.jupiter.api.Assertions.*;

import static protocol.Evento.Tipo.*;

import model.*;

import org.junit.jupiter.api.*;

import persistence.jdbc.*;

import protocol.Evento;

import support.FakeConexao;

import java.time.*;
import java.util.*;

class InteracoesTest {
    private final ConnectionFactory factory =
            new ConnectionFactory(
                    "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    private final JdbcMensagemRepository geral = new JdbcMensagemRepository(factory);
    private final Sala estudos = new Sala("estudos", "Estudos");
    private final JdbcMensagemRepository outra = new JdbcMensagemRepository(factory, estudos);
    private final SalaService salas;
    private final FakeConexao ana = new FakeConexao("Ana Silva", "dragon"),
            bruno = new FakeConexao("Bruno", "heart");

    InteracoesTest() {
        geral.inicializar();
        var mapa = new LinkedHashMap<Sala, ChatService>();
        mapa.put(Sala.GERAL, new ChatService(geral, new ClientRegistry(), Clock.systemUTC(), 50));
        mapa.put(
                estudos,
                new ChatService(outra, new ClientRegistry(), Clock.systemUTC(), 50, estudos));
        salas = new SalaService(mapa);
        salas.conectar(ana);
        salas.conectar(bruno);
        ana.limpar();
        bruno.limpar();
    }

    @AfterEach
    void fechar() {
        salas.close();
    }

    private long publicar(String texto) {
        salas.executar(ana, texto);
        return geral.buscarUltimas(1).get(0).id();
    }

    private Evento ultimo(FakeConexao c, Evento.Tipo tipo) {
        return c.recebidos.stream().filter(e -> e.tipo() == tipo).reduce((a, b) -> b).orElseThrow();
    }

    @Test
    void respostaCitaOriginalENaoMudaQuandoPerfilEAlterado() {
        long id = publicar("Original de Ana");
        salas.executar(ana, "/perfil cat Ana Souza");
        salas.executar(bruno, "/responder " + id + " Minha resposta");
        var m = geral.buscarUltimas(1).get(0);
        assertEquals("Ana Silva", m.citacao().autor());
        assertEquals("Original de Ana", m.citacao().texto());
        assertEquals(id, m.citacao().id());
        assertEquals("Bruno", m.remetente().nome());
        var e = ultimo(ana, MENSAGEM);
        assertEquals(Long.toString(id), e.campo(6));
        assertEquals("Ana Silva", e.campo(7));
        var novo = new FakeConexao("Carla");
        salas.conectar(novo);
        assertEquals(e.campos().subList(6, 9), ultimo(novo, MENSAGEM).campos().subList(6, 9));
    }

    @Test
    void reacoesSaoPorSessaoAlternamESobrevivemATrocaDePerfil() {
        long id = publicar("Reaja aqui");
        salas.executar(ana, "/reagir " + id + " 💜");
        assertEquals(List.of(Long.toString(id), "💜", "1", "true"), ultimo(ana, REACOES).campos());
        assertEquals("false", ultimo(bruno, REACOES).campo(3));
        salas.executar(bruno, "/reagir " + id + " 💜");
        assertEquals("2", ultimo(ana, REACOES).campo(2));
        salas.executar(ana, "/perfil party Ana Nova");
        salas.executar(ana, "/reagir " + id + " 💜");
        assertEquals("1", ultimo(ana, REACOES).campo(2));
        assertEquals("false", ultimo(ana, REACOES).campo(3));
        assertEquals("true", ultimo(bruno, REACOES).campo(3));
        assertThrows(
                IllegalArgumentException.class,
                () -> salas.executar(ana, "/reagir " + id + " <script>"));
    }

    @Test
    void acoesRecusamIdsDeOutraSalaEIdsApagados() {
        long id = publicar("Somente na Geral");
        salas.trocarSala(bruno, "estudos");
        ana.limpar();
        bruno.limpar();
        for (String comando :
                List.of(
                        "/responder " + id + " texto",
                        "/reagir " + id + " 👍",
                        "/fixar " + id,
                        "/desfixar " + id))
            assertThrows(IllegalArgumentException.class, () -> salas.executar(bruno, comando));
        assertTrue(ana.recebidos.isEmpty());
        assertTrue(outra.buscarUltimas(10).isEmpty());
        salas.executar(ana, "/limpar");
        salas.executar(ana, "/limpar confirmar");
        assertThrows(
                IllegalArgumentException.class,
                () -> salas.executar(ana, "/responder " + id + " texto"));
    }

    @Test
    void fixadasTemLimiteSaoCompartilhadasELimpezaRemoveAvisosEReacoes() {
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 6; i++) ids.add(publicar("Aviso " + i));
        for (int i = 0; i < 5; i++) salas.executar(ana, "/fixar " + ids.get(i));
        assertEquals(30, ultimo(bruno, FIXADAS).campos().size());
        assertEquals(5, geral.fixadas().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> salas.executar(bruno, "/fixar " + ids.get(5)));
        salas.executar(bruno, "/fixar " + ids.get(0)); // Idempotente, sem consumir outra vaga.
        salas.executar(bruno, "/desfixar " + ids.get(0));
        salas.executar(bruno, "/fixar " + ids.get(5));
        var novo = new FakeConexao("Carla");
        salas.conectar(novo);
        assertEquals(ultimo(ana, FIXADAS), ultimo(novo, FIXADAS));
        salas.executar(ana, "/reagir " + ids.get(0) + " 👍");
        salas.executar(ana, "/limpar");
        salas.executar(ana, "/limpar confirmar");
        assertTrue(ultimo(bruno, FIXADAS).campos().isEmpty());
        assertTrue(geral.reacoes(ids.get(0), ana.sessao()).isEmpty());
        assertTrue(geral.fixadas().isEmpty());
    }

    @Test
    void perfilRecusaDuplicadoReservadoEInvalidoSemAlterarSessao() {
        publicar("Antes");
        for (String cmd :
                List.of(
                        "/perfil cat Bruno",
                        "/perfil cat Orkut IA",
                        "/perfil inexistente Ana",
                        "/perfil cat @na"))
            assertThrows(IllegalArgumentException.class, () -> salas.executar(ana, cmd));
        assertEquals("Ana Silva", ana.getUsuario().nome());
        salas.executar(ana, "/perfil party Ana   Nova");
        assertEquals("Ana Nova", ana.getUsuario().nome());
        assertEquals(
                List.of("Ana Silva", "Ana Nova", "party"), ultimo(bruno, PERFIL_ALTERADO).campos());
        salas.executar(ana, "Depois");
        var historico = geral.buscarUltimas(10);
        assertEquals("dragon", historico.get(0).remetente().emoji().id());
        assertEquals("party", historico.get(1).remetente().emoji().id());
        salas.executar(bruno, "/msg \"Ana Nova\" Privada");
        assertEquals("Privada", ultimo(ana, PRIVADA).campo(2));
    }

    @Test
    void mencaoNotificaSomenteDestinoExatoENaoReapareceNoHistorico() {
        var parcial = new FakeConexao("Ana");
        salas.conectar(parcial);
        parcial.limpar();
        ana.limpar();
        bruno.limpar();
        salas.executar(bruno, "Olá @\"ana silva\"!");
        assertEquals("Bruno", ultimo(ana, MENCAO).campo(1));
        assertTrue(parcial.recebidos.stream().noneMatch(e -> e.tipo() == MENCAO));
        assertTrue(bruno.recebidos.stream().noneMatch(e -> e.tipo() == MENCAO));
        ana.limpar();
        salas.executar(ana, "/history 50");
        assertTrue(ana.recebidos.stream().noneMatch(e -> e.tipo() == MENCAO));
        assertFalse(Mencoes.contem("email@Ana", "Ana"));
        assertFalse(Mencoes.contem("@AnaMaria", "Ana"));
        assertTrue(Mencoes.contem("Oi @Ana!", "Ana"));
    }
}
