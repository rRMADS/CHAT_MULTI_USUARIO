package server;

import static org.junit.jupiter.api.Assertions.*;

import static protocol.Evento.Tipo.*;

import model.Sala;

import org.junit.jupiter.api.Test;

import persistence.jdbc.*;

import protocol.*;

import service.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class ServidorIntegrationTest {
    private Servidor servidor() throws Exception {
        var r =
                new JdbcMensagemRepository(
                        new ConnectionFactory(
                                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
                                "sa",
                                ""));
        r.inicializar();
        var servidor =
                new Servidor(
                        "127.0.0.1",
                        0,
                        20,
                        new ChatService(r, new ClientRegistry(), Clock.systemUTC(), 50));
        servidor.iniciar();
        return servidor;
    }

    @Test
    void tresClientesRecebemBroadcastEHistoricoAposQueda() throws Exception {
        try (var servidor = servidor();
                var ana = new Cliente(servidor, "Ana");
                var bruno = new Cliente(servidor, "Bruno");
                var carla = new Cliente(servidor, "Carla")) {
            ana.enviar("Olá, três clientes! 🙂");
            var a = ana.ate(MENSAGEM);
            var b = bruno.ate(MENSAGEM);
            var c = carla.ate(MENSAGEM);
            assertEquals(a, b);
            assertEquals(b, c);
            assertEquals("Olá, três clientes! 🙂", a.campo(2));
            carla.close();
            bruno.enviar("Continuamos conectados");
            assertEquals("Continuamos conectados", ana.ate(MENSAGEM).campo(2));
            try (var novo = new Cliente(servidor, "Davi")) {
                assertEquals(
                        List.of("Olá, três clientes! 🙂", "Continuamos conectados"),
                        novo.historico.stream().map(e -> e.campo(2)).toList());
            }
        }
    }

    @Test
    void privadaNaoChegaAoTerceiroNemAoHistorico() throws Exception {
        try (var servidor = servidor();
                var ana = new Cliente(servidor, "Ana");
                var bruno = new Cliente(servidor, "Bruno");
                var carla = new Cliente(servidor, "Carla")) {
            ana.enviar("/msg Bruno segredo");
            assertEquals("segredo", bruno.ate(PRIVADA).campo(2));
            ana.ate(PRIVADA);
            carla.enviar("/ping");
            Evento evento;
            do {
                evento = carla.ler();
                assertNotEquals(PRIVADA, evento.tipo());
            } while (evento.tipo() != PONG);
            try (var novo = new Cliente(servidor, "Davi")) {
                assertTrue(novo.historico.isEmpty());
            }
        }
    }

    @Test
    void apelidoDuplicadoERecusadoSemAfetarClienteOriginal() throws Exception {
        try (var servidor = servidor();
                var ana = new Cliente(servidor, "Ana");
                var duplicado = new Socket("127.0.0.1", servidor.porta())) {
            duplicado.setSoTimeout(5000);
            var w =
                    new PrintWriter(
                            new OutputStreamWriter(
                                    duplicado.getOutputStream(), StandardCharsets.UTF_8),
                            true);
            w.println(Protocolo.apresentar(new model.Usuario("ana")));
            var reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    duplicado.getInputStream(), StandardCharsets.UTF_8));
            assertEquals(ERRO, Protocolo.decodificar(reader.readLine()).tipo());
            ana.enviar("Ainda aqui");
            assertEquals("Ainda aqui", ana.ate(MENSAGEM).campo(2));
        }
    }

    @Test
    void leituraLimitadaRecusaLinhaGigante() {
        assertThrows(
                IOException.class,
                () -> LeitorLinhas.ler(new StringReader("x".repeat(2050)), 2048));
    }

    @Test
    void conexaoRealRecusaEntradaSemEmoji() throws Exception {
        try (var servidor = servidor()) {
            for (String linha :
                    List.of(
                            "Ana",
                            Protocolo.codificar(Evento.de(APRESENTACAO, "Ana", "")),
                            Protocolo.codificar(Evento.de(APRESENTACAO, "Ana", "desconhecido")))) {
                try (var socket = new Socket("127.0.0.1", servidor.porta())) {
                    socket.setSoTimeout(3000);
                    var w =
                            new PrintWriter(
                                    new OutputStreamWriter(
                                            socket.getOutputStream(), StandardCharsets.UTF_8),
                                    true);
                    w.println(linha);
                    var r =
                            new BufferedReader(
                                    new InputStreamReader(
                                            socket.getInputStream(), StandardCharsets.UTF_8));
                    assertEquals(ERRO, Protocolo.decodificar(r.readLine()).tipo());
                    assertNull(r.readLine());
                }
            }
        }
    }

    @Test
    void nomesComEspacosFuncionamNoHandshakeBroadcastPrivadaEHistorico() throws Exception {
        try (var servidor = servidor();
                var joao = new Cliente(servidor, "João Silva");
                var maria = new Cliente(servidor, "Maria Souza");
                var parcial = new Cliente(servidor, "Maria")) {
            joao.enviar("Olá, pessoal!");
            assertEquals("João Silva", joao.ate(MENSAGEM).campo(1));
            assertEquals("João Silva", maria.ate(MENSAGEM).campo(1));
            parcial.ate(MENSAGEM);
            joao.enviar("/msg \"Maria Souza\" Somente para você");
            assertEquals("Somente para você", maria.ate(PRIVADA).campo(2));
            joao.ate(PRIVADA);
            parcial.enviar("/ping");
            Evento evento;
            do {
                evento = parcial.ler();
                assertNotEquals(PRIVADA, evento.tipo());
            } while (evento.tipo() != PONG);
            try (var novo = new Cliente(servidor, "Davi Santos")) {
                assertEquals("João Silva", novo.historico.get(0).campo(1));
                assertEquals("Olá, pessoal!", novo.historico.get(0).campo(2));
            }
        }
    }

    @Test
    void trocaDeSalaNaMesmaConexaoIsolaMensagensEHistorico() throws Exception {
        var factory =
                new ConnectionFactory(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new JdbcMensagemRepository(factory).inicializar();
        Map<Sala, ChatService> salas = new LinkedHashMap<>();
        for (var sala : List.of(Sala.GERAL, new Sala("estudos", "Estudos"))) {
            salas.put(
                    sala,
                    new ChatService(
                            new JdbcMensagemRepository(factory, sala),
                            new ClientRegistry(),
                            Clock.systemUTC(),
                            50,
                            sala));
        }
        try (var servidor = new Servidor("127.0.0.1", 0, 20, new SalaService(salas))) {
            servidor.iniciar();
            try (var ana = new Cliente(servidor, "Ana Silva");
                    var bruno = new Cliente(servidor, "Bruno")) {
                ana.enviar("Mensagem da Geral");
                ana.ate(MENSAGEM);
                bruno.ate(MENSAGEM);
                assertEquals("estudos", ana.entrarSala("estudos").campo(1));
                assertTrue(ana.historico.isEmpty());
                ana.enviar("Mensagem dos Estudos");
                ana.ate(MENSAGEM);
                bruno.enviar("/ping");
                Evento e;
                do {
                    e = bruno.ler();
                    assertNotEquals(MENSAGEM, e.tipo());
                } while (e.tipo() != PONG);
                assertEquals("estudos", bruno.entrarSala("estudos").campo(1));
                assertEquals(
                        List.of("Mensagem dos Estudos"),
                        bruno.historico.stream().map(m -> m.campo(2)).toList());
                bruno.enviar("Agora estamos juntos");
                assertEquals("Agora estamos juntos", ana.ate(MENSAGEM).campo(2));
                bruno.ate(MENSAGEM);
                ana.entrarSala("geral");
                assertEquals(
                        List.of("Mensagem da Geral"),
                        ana.historico.stream().map(m -> m.campo(2)).toList());
            }
        }
    }

    @Test
    void comandosDaAjudaFuncionamPorSocketSemVirarMensagensPublicas() throws Exception {
        var factory =
                new ConnectionFactory(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new JdbcMensagemRepository(factory).inicializar();
        Map<Sala, ChatService> salas = new LinkedHashMap<>();
        for (var sala : List.of(Sala.GERAL, new Sala("cafe", "Café")))
            salas.put(
                    sala,
                    new ChatService(
                            new JdbcMensagemRepository(factory, sala),
                            new ClientRegistry(),
                            Clock.systemUTC(),
                            50,
                            sala));
        try (var servidor = new Servidor("127.0.0.1", 0, 20, new SalaService(salas))) {
            servidor.iniciar();
            try (var ana = new Cliente(servidor, "Ana Silva");
                    var bruno = new Cliente(servidor, "Bruno")) {
                ana.enviar("/HELP");
                assertTrue(ana.ateResposta("Comandos disponíveis").campo(0).contains("/join cafe"));
                ana.enviar("/users");
                assertTrue(ana.ateResposta("Participantes da sala").campo(0).contains("Bruno"));
                ana.enviar("/salas");
                assertTrue(ana.ateResposta("Salas disponíveis").campo(0).contains("Café"));
                ana.enviar("/join inexistente");
                assertTrue(ana.ate(ERRO).campo(0).contains("/join cafe"));
                assertEquals("cafe", ana.entrarSala("Café").campo(1));
                bruno.enviar("/ping");
                Evento e;
                do {
                    e = bruno.ler();
                    assertNotEquals(MENSAGEM, e.tipo());
                    if (e.tipo() == SISTEMA)
                        assertEquals(
                                1,
                                e.campos().size(),
                                "Resposta de comando vazou para outra pessoa");
                } while (e.tipo() != PONG);
                ana.enviar("/historico 5");
                assertEquals("0", ana.ate(HISTORICO_FIM).campo(0));
                ana.enviar("/buscar texto");
                assertEquals("0", ana.ate(HISTORICO_FIM).campo(0));
                bruno.entrarSala("cafe");
                ana.enviar("/msg Bruno Olá, em privado");
                assertEquals("Olá, em privado", ana.ate(PRIVADA).campo(2));
                assertEquals("Olá, em privado", bruno.ate(PRIVADA).campo(2));
                ana.enviar("/sair");
                boolean terminou = false;
                for (int i = 0; i < 100; i++) {
                    if (ana.reader.readLine() == null) {
                        terminou = true;
                        break;
                    }
                }
                assertTrue(terminou, "O comando de saída não encerrou a conexão");
                bruno.enviar("/ajuda");
                assertTrue(bruno.ateResposta("Comandos disponíveis").campo(0).contains("/quit"));
            }
        }
    }

    @Test
    void podePararERetomarDigitacaoImediatamenteSemEnviarRascunho() throws Exception {
        try (var servidor = servidor();
                var ana = new Cliente(servidor, "Ana Silva");
                var bruno = new Cliente(servidor, "Bruno")) {
            ana.enviar("/typing");
            assertEquals(List.of("Ana Silva"), bruno.ate(DIGITANDO).campos());
            ana.enviar("/typing stop");
            assertEquals(List.of("Ana Silva", "stop"), bruno.ate(DIGITANDO).campos());
            ana.enviar("/typing");
            assertEquals(List.of("Ana Silva"), bruno.ate(DIGITANDO).campos());
            ana.enviar("Mensagem enviada");
            assertEquals("Mensagem enviada", bruno.ate(MENSAGEM).campo(2));
            try (var novo = new Cliente(servidor, "Carla")) {
                assertEquals(
                        List.of("Mensagem enviada"),
                        novo.historico.stream().map(e -> e.campo(2)).toList());
            }
        }
    }

    @Test
    void limpezaConfirmadaPorSocketAvisaSalaIsolaOutrasEPersisteSemHistoricoAntigo()
            throws Exception {
        var factory =
                new ConnectionFactory(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new JdbcMensagemRepository(factory).inicializar();
        Map<Sala, ChatService> conversas = new LinkedHashMap<>();
        for (var sala : List.of(Sala.GERAL, new Sala("estudos", "Estudos")))
            conversas.put(
                    sala,
                    new ChatService(
                            new JdbcMensagemRepository(factory, sala),
                            new ClientRegistry(),
                            Clock.systemUTC(),
                            50,
                            sala));
        try (var servidor = new Servidor("127.0.0.1", 0, 20, new SalaService(conversas))) {
            servidor.iniciar();
            try (var ana = new Cliente(servidor, "Ana");
                    var bruno = new Cliente(servidor, "Bruno");
                    var carla = new Cliente(servidor, "Carla")) {
                carla.entrarSala("estudos");
                carla.enviar("Preservada nos Estudos");
                carla.ate(MENSAGEM);
                ana.enviar("Mensagem antiga");
                var antiga = ana.ate(MENSAGEM);
                bruno.ate(MENSAGEM);
                ana.enviar("/limpar confirmar");
                assertTrue(ana.ate(ERRO).campo(0).contains("/limpar"));
                ana.enviar("/LIMPARHISTORICO");
                assertTrue(
                        ana.ateResposta("Confirmar limpeza do histórico")
                                .campo(0)
                                .contains("Geral"));
                bruno.enviar("/ping");
                Evento e;
                do {
                    e = bruno.ler();
                    assertNotEquals(HISTORICO_LIMPO, e.tipo());
                } while (e.tipo() != PONG);
                ana.enviar("/CLEAR CONFIRMAR");
                var limpeza = ana.ate(HISTORICO_LIMPO);
                assertEquals(List.of("geral", "Geral", "Ana", "1"), limpeza.campos());
                assertEquals(limpeza, bruno.ate(HISTORICO_LIMPO));
                carla.enviar("/ping");
                do {
                    e = carla.ler();
                    assertNotEquals(HISTORICO_LIMPO, e.tipo());
                } while (e.tipo() != PONG);
                try (var novo = new Cliente(servidor, "Davi")) {
                    assertTrue(novo.historico.isEmpty());
                    novo.entrarSala("estudos");
                    assertEquals("Preservada nos Estudos", novo.historico.get(0).campo(2));
                }
                ana.enviar("Depois da limpeza");
                var nova = ana.ate(MENSAGEM);
                assertTrue(Long.parseLong(nova.campo(0)) > Long.parseLong(antiga.campo(0)));
            }
        }
    }

    @Test
    void respostaIaMultilinhaAtravessaSocketEmUmEventoEReapareceNoHistorico() throws Exception {
        String texto =
                "## Receita\n\n1. **Leve à geladeira**\n2. Sirva.\n\n| Sugestão | Como fazer |\n| --- | --- |\n| Chocolate | Derreta e espalhe. |\n\n```java\nSystem.out.println(\"Olá\");\n```\n\n"
                        + "Detalhes. ".repeat(110).stripTrailing();
        var r =
                new JdbcMensagemRepository(
                        new ConnectionFactory(
                                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
                                "sa",
                                ""));
        r.inicializar();
        var salas =
                new SalaService(
                        Map.of(
                                Sala.GERAL,
                                new ChatService(
                                        r,
                                        new ClientRegistry(),
                                        Clock.fixed(
                                                java.time.Instant.parse("2026-11-07T19:23:00Z"),
                                                java.time.ZoneOffset.UTC),
                                        50)),
                        new ia.AssistenteIA(p -> texto));
        try (var servidor = new Servidor("127.0.0.1", 0, 20, salas)) {
            servidor.iniciar();
            try (var ana = new Cliente(servidor, "Ana");
                    var bruno = new Cliente(servidor, "Bruno")) {
                ana.enviar("/ia sala Receita");
                ana.ate(MENSAGEM);
                bruno.ate(MENSAGEM);
                var resposta = ana.ate(MENSAGEM);
                assertEquals(resposta, bruno.ate(MENSAGEM));
                assertEquals(texto, resposta.campo(2));
                assertEquals("MARKDOWN_IA", resposta.campo(5));
                assertEquals(texto, ana.ate(IA_RESPOSTA).campo(3));
                try (var novo = new Cliente(servidor, "Carla")) {
                    assertEquals(2, novo.historico.size());
                    assertEquals(resposta, novo.historico.get(1));
                }
            }
        }
    }

    @Test
    void citacoesReacoesMencoesPerfilEFixadasAtravessamSocket() throws Exception {
        try (var s = servidor();
                var ana = new Cliente(s, "Ana Silva");
                var bruno = new Cliente(s, "Bruno")) {
            ana.enviar("/ping");
            ana.ate(PONG);
            bruno.enviar("/ping");
            bruno.ate(PONG);
            ana.enviar("Original");
            var original = ana.ate(MENSAGEM);
            bruno.ate(MENSAGEM);
            String id = original.campo(0);
            bruno.enviar("/responder " + id + " Olá @\"Ana Silva\"");
            var resposta = ana.ate(MENSAGEM);
            bruno.ate(MENSAGEM);
            assertEquals(id, resposta.campo(6));
            assertEquals("Ana Silva", resposta.campo(7));
            assertEquals("Original", resposta.campo(8));
            assertEquals(resposta.campo(0), ana.ate(MENCAO).campo(0));
            bruno.enviar("/reagir " + id + " 💜");
            assertEquals("true", bruno.ate(REACOES).campo(3));
            assertEquals("false", ana.ate(REACOES).campo(3));
            ana.enviar("/fixar " + id);
            assertEquals(id, bruno.ate(FIXADAS).campo(0));
            ana.ate(FIXADAS);
            ana.enviar("/perfil party Ana Nova");
            assertEquals("Ana Nova", bruno.ate(PERFIL_ALTERADO).campo(1));
            ana.ate(PERFIL_ALTERADO);
            ana.enviar("Nova identidade");
            assertEquals("party", bruno.ate(MENSAGEM).campo(4));
            ana.ate(MENSAGEM);
            try (var novo = new Cliente(s, "Carla")) {
                assertEquals(id, novo.historico.get(1).campo(6));
                assertEquals(id, novo.ate(FIXADAS).campo(0));
            }
        }
    }

    @Test
    void mesmaSessaoRetomaConexaoAntigaSemPermitirApelidoDuplicado() throws Exception {
        String token = UUID.randomUUID().toString();
        try (var s = servidor();
                var original = new Cliente(s, "Ana", token, "geral")) {
            original.enviar("/perfil dragon Ana Nova");
            original.ate(PERFIL_ALTERADO);
            original.enviar("Original");
            String id = original.ate(MENSAGEM).campo(0);
            original.enviar("/reagir " + id + " 👍");
            assertEquals("1", original.ate(REACOES).campo(2));
            try (var retomada = new Cliente(s, "Ana Antiga", token, "geral")) {
                assertEquals("Ana Nova", retomada.entrada.campo(0));
                assertEquals("dragon", retomada.entrada.campo(3));
                retomada.enviar("/ping");
                retomada.ate(PONG);
                retomada.enviar("/reagir " + id + " 👍");
                assertEquals(1, retomada.ate(REACOES).campos().size());
                retomada.enviar("Continuamos");
                assertEquals("Continuamos", retomada.ate(MENSAGEM).campo(2));
            }
        }
    }

    private static final class Cliente implements AutoCloseable {
        final Evento entrada;
        final Socket socket;
        final PrintWriter writer;
        final BufferedReader reader;
        final List<Evento> historico = new ArrayList<>();

        Cliente(Servidor s, String nome) throws IOException {
            this(s, nome, "", "geral");
        }

        Cliente(Servidor s, String nome, String token, String sala) throws IOException {
            socket = new Socket("127.0.0.1", s.porta());
            socket.setSoTimeout(8000);
            writer =
                    new PrintWriter(
                            new OutputStreamWriter(
                                    socket.getOutputStream(), StandardCharsets.UTF_8),
                            true);
            reader =
                    new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer.println(
                    token.isEmpty()
                            ? Protocolo.apresentar(new model.Usuario(nome))
                            : Protocolo.apresentar(new model.Usuario(nome), token, sala));
            entrada = ler();
            assertEquals(BEM_VINDO, entrada.tipo());
            Evento e;
            do {
                e = ler();
                if (e.tipo() == MENSAGEM) historico.add(e);
            } while (e.tipo() != HISTORICO_FIM);
        }

        Evento ler() throws IOException {
            String linha = reader.readLine();
            if (linha == null) throw new EOFException();
            return Protocolo.decodificar(linha);
        }

        Evento ate(Evento.Tipo tipo) throws IOException {
            for (int i = 0; i < 500; i++) {
                var e = ler();
                if (e.tipo() == tipo) return e;
            }
            throw new IOException("Evento não recebido: " + tipo);
        }

        Evento ateResposta(String titulo) throws IOException {
            for (int i = 0; i < 500; i++) {
                var e = ler();
                if (e.tipo() == SISTEMA && e.campos().size() >= 2 && e.campo(1).equals(titulo))
                    return e;
            }
            throw new IOException("Resposta não recebida: " + titulo);
        }

        void enviar(String texto) {
            writer.println(texto);
        }

        Evento entrarSala(String id) throws IOException {
            enviar("/join " + id);
            var entrada = ate(BEM_VINDO);
            historico.clear();
            Evento e;
            do {
                e = ler();
                if (e.tipo() == MENSAGEM) historico.add(e);
            } while (e.tipo() != HISTORICO_FIM);
            return entrada;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
