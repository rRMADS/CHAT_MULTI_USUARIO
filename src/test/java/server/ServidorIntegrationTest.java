package server;
import org.junit.jupiter.api.Test;
import persistence.jdbc.*;
import service.*;
import protocol.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import model.Sala;
import static org.junit.jupiter.api.Assertions.*;
import static protocol.Evento.Tipo.*;

class ServidorIntegrationTest {
    private Servidor servidor() throws Exception {
        var r = new JdbcMensagemRepository(new ConnectionFactory("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "")); r.inicializar();
        var servidor = new Servidor("127.0.0.1", 0, 20, new ChatService(r, new ClientRegistry(), Clock.systemUTC(), 50)); servidor.iniciar(); return servidor;
    }
    @Test void tresClientesRecebemBroadcastEHistoricoAposQueda() throws Exception {
        try (var servidor = servidor(); var ana = new Cliente(servidor, "Ana"); var bruno = new Cliente(servidor, "Bruno"); var carla = new Cliente(servidor, "Carla")) {
            ana.enviar("Olá, três clientes! 🙂");
            var a = ana.ate(MENSAGEM); var b = bruno.ate(MENSAGEM); var c = carla.ate(MENSAGEM);
            assertEquals(a, b); assertEquals(b, c); assertEquals("Olá, três clientes! 🙂", a.campo(2));
            carla.close(); bruno.enviar("Continuamos conectados");
            assertEquals("Continuamos conectados", ana.ate(MENSAGEM).campo(2));
            try (var novo = new Cliente(servidor, "Davi")) {
                assertEquals(List.of("Olá, três clientes! 🙂", "Continuamos conectados"), novo.historico.stream().map(e -> e.campo(2)).toList());
            }
        }
    }
    @Test void privadaNaoChegaAoTerceiroNemAoHistorico() throws Exception {
        try (var servidor = servidor(); var ana = new Cliente(servidor, "Ana"); var bruno = new Cliente(servidor, "Bruno"); var carla = new Cliente(servidor, "Carla")) {
            ana.enviar("/msg Bruno segredo"); assertEquals("segredo", bruno.ate(PRIVADA).campo(2)); ana.ate(PRIVADA);
            carla.enviar("/ping"); Evento evento; do { evento = carla.ler(); assertNotEquals(PRIVADA, evento.tipo()); } while (evento.tipo() != PONG);
            try (var novo = new Cliente(servidor, "Davi")) { assertTrue(novo.historico.isEmpty()); }
        }
    }
    @Test void apelidoDuplicadoERecusadoSemAfetarClienteOriginal() throws Exception {
        try (var servidor = servidor(); var ana = new Cliente(servidor, "Ana"); var duplicado = new Socket("127.0.0.1", servidor.porta())) {
            duplicado.setSoTimeout(5000); var w = new PrintWriter(new OutputStreamWriter(duplicado.getOutputStream(), StandardCharsets.UTF_8), true); w.println(Protocolo.apresentar(new model.Usuario("ana")));
            var reader = new BufferedReader(new InputStreamReader(duplicado.getInputStream(), StandardCharsets.UTF_8));
            assertEquals(ERRO, Protocolo.decodificar(reader.readLine()).tipo());
            ana.enviar("Ainda aqui"); assertEquals("Ainda aqui", ana.ate(MENSAGEM).campo(2));
        }
    }
    @Test void leituraLimitadaRecusaLinhaGigante() { assertThrows(IOException.class, () -> ClienteHandler.lerLinhaLimitada(new StringReader("x".repeat(2050)), 2048)); }
    @Test void conexaoRealRecusaEntradaSemEmoji() throws Exception {
        try(var servidor=servidor()) {
            for(String linha:List.of("Ana",Protocolo.codificar(Evento.de(APRESENTACAO,"Ana","")),Protocolo.codificar(Evento.de(APRESENTACAO,"Ana","desconhecido")))) {
                try(var socket=new Socket("127.0.0.1",servidor.porta())) {
                    socket.setSoTimeout(3000); var w=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true); w.println(linha);
                    var r=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    assertEquals(ERRO,Protocolo.decodificar(r.readLine()).tipo()); assertNull(r.readLine());
                }
            }
        }
    }
    @Test void nomesComEspacosFuncionamNoHandshakeBroadcastPrivadaEHistorico() throws Exception {
        try (var servidor = servidor(); var joao = new Cliente(servidor, "João Silva");
             var maria = new Cliente(servidor, "Maria Souza"); var parcial = new Cliente(servidor, "Maria")) {
            joao.enviar("Olá, pessoal!");
            assertEquals("João Silva", joao.ate(MENSAGEM).campo(1));
            assertEquals("João Silva", maria.ate(MENSAGEM).campo(1)); parcial.ate(MENSAGEM);
            joao.enviar("/msg \"Maria Souza\" Somente para você");
            assertEquals("Somente para você", maria.ate(PRIVADA).campo(2)); joao.ate(PRIVADA);
            parcial.enviar("/ping"); Evento evento;
            do { evento = parcial.ler(); assertNotEquals(PRIVADA, evento.tipo()); } while (evento.tipo() != PONG);
            try (var novo = new Cliente(servidor, "Davi Santos")) {
                assertEquals("João Silva", novo.historico.get(0).campo(1));
                assertEquals("Olá, pessoal!", novo.historico.get(0).campo(2));
            }
        }
    }
    @Test void trocaDeSalaNaMesmaConexaoIsolaMensagensEHistorico() throws Exception {
        var factory = new ConnectionFactory("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new JdbcMensagemRepository(factory).inicializar(); Map<Sala, ChatService> salas = new LinkedHashMap<>();
        for (var sala : List.of(Sala.GERAL, new Sala("estudos", "Estudos"))) {
            salas.put(sala, new ChatService(new JdbcMensagemRepository(factory, sala), new ClientRegistry(), Clock.systemUTC(), 50, sala));
        }
        try (var servidor = new Servidor("127.0.0.1", 0, 20, new SalaService(salas))) {
            servidor.iniciar();
            try (var ana = new Cliente(servidor, "Ana Silva"); var bruno = new Cliente(servidor, "Bruno")) {
                ana.enviar("Mensagem da Geral"); ana.ate(MENSAGEM); bruno.ate(MENSAGEM);
                assertEquals("estudos", ana.entrarSala("estudos").campo(1)); assertTrue(ana.historico.isEmpty());
                ana.enviar("Mensagem dos Estudos"); ana.ate(MENSAGEM);
                bruno.enviar("/ping"); Evento e;
                do { e = bruno.ler(); assertNotEquals(MENSAGEM, e.tipo()); } while (e.tipo() != PONG);
                assertEquals("estudos", bruno.entrarSala("estudos").campo(1));
                assertEquals(List.of("Mensagem dos Estudos"), bruno.historico.stream().map(m -> m.campo(2)).toList());
                bruno.enviar("Agora estamos juntos"); assertEquals("Agora estamos juntos", ana.ate(MENSAGEM).campo(2)); bruno.ate(MENSAGEM);
                ana.entrarSala("geral"); assertEquals(List.of("Mensagem da Geral"), ana.historico.stream().map(m -> m.campo(2)).toList());
            }
        }
    }
    @Test void comandosDaAjudaFuncionamPorSocketSemVirarMensagensPublicas() throws Exception {
        var factory = new ConnectionFactory("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new JdbcMensagemRepository(factory).inicializar(); Map<Sala, ChatService> salas = new LinkedHashMap<>();
        for (var sala : List.of(Sala.GERAL, new Sala("cafe", "Café")))
            salas.put(sala, new ChatService(new JdbcMensagemRepository(factory, sala), new ClientRegistry(), Clock.systemUTC(), 50, sala));
        try (var servidor = new Servidor("127.0.0.1", 0, 20, new SalaService(salas))) {
            servidor.iniciar();
            try (var ana = new Cliente(servidor, "Ana Silva"); var bruno = new Cliente(servidor, "Bruno")) {
                ana.enviar("/HELP"); assertTrue(ana.ateResposta("Comandos disponíveis").campo(0).contains("/join cafe"));
                ana.enviar("/users"); assertTrue(ana.ateResposta("Participantes da sala").campo(0).contains("Bruno"));
                ana.enviar("/salas"); assertTrue(ana.ateResposta("Salas disponíveis").campo(0).contains("Café"));
                ana.enviar("/join inexistente"); assertTrue(ana.ate(ERRO).campo(0).contains("/join cafe"));
                assertEquals("cafe", ana.entrarSala("Café").campo(1));
                bruno.enviar("/ping"); Evento e;
                do { e = bruno.ler(); assertNotEquals(MENSAGEM, e.tipo()); if (e.tipo() == SISTEMA) assertEquals(1, e.campos().size(), "Resposta de comando vazou para outra pessoa"); } while (e.tipo() != PONG);
                ana.enviar("/historico 5"); assertEquals("0", ana.ate(HISTORICO_FIM).campo(0));
                ana.enviar("/buscar texto"); assertEquals("0", ana.ate(HISTORICO_FIM).campo(0));
                bruno.entrarSala("cafe"); ana.enviar("/msg Bruno Olá, em privado");
                assertEquals("Olá, em privado", ana.ate(PRIVADA).campo(2)); assertEquals("Olá, em privado", bruno.ate(PRIVADA).campo(2));
                ana.enviar("/sair"); boolean terminou = false;
                for (int i = 0; i < 100; i++) { if (ana.reader.readLine() == null) { terminou = true; break; } }
                assertTrue(terminou, "O comando de saída não encerrou a conexão");
                bruno.enviar("/ajuda"); assertTrue(bruno.ateResposta("Comandos disponíveis").campo(0).contains("/quit"));
            }
        }
    }
    @Test void podePararERetomarDigitacaoImediatamenteSemEnviarRascunho() throws Exception {
        try (var servidor = servidor(); var ana = new Cliente(servidor, "Ana Silva"); var bruno = new Cliente(servidor, "Bruno")) {
            ana.enviar("/typing"); assertEquals(List.of("Ana Silva"), bruno.ate(DIGITANDO).campos());
            ana.enviar("/typing stop"); assertEquals(List.of("Ana Silva", "stop"), bruno.ate(DIGITANDO).campos());
            ana.enviar("/typing"); assertEquals(List.of("Ana Silva"), bruno.ate(DIGITANDO).campos());
            ana.enviar("Mensagem enviada"); assertEquals("Mensagem enviada", bruno.ate(MENSAGEM).campo(2));
            try (var novo = new Cliente(servidor, "Carla")) {
                assertEquals(List.of("Mensagem enviada"), novo.historico.stream().map(e -> e.campo(2)).toList());
            }
        }
    }
    @Test void limpezaConfirmadaPorSocketAvisaSalaIsolaOutrasEPersisteSemHistoricoAntigo() throws Exception {
        var factory = new ConnectionFactory("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        new JdbcMensagemRepository(factory).inicializar(); Map<Sala,ChatService> conversas = new LinkedHashMap<>();
        for(var sala:List.of(Sala.GERAL,new Sala("estudos","Estudos")))
            conversas.put(sala,new ChatService(new JdbcMensagemRepository(factory,sala),new ClientRegistry(),Clock.systemUTC(),50,sala));
        try(var servidor=new Servidor("127.0.0.1",0,20,new SalaService(conversas))) {
            servidor.iniciar();
            try(var ana=new Cliente(servidor,"Ana"); var bruno=new Cliente(servidor,"Bruno"); var carla=new Cliente(servidor,"Carla")) {
                carla.entrarSala("estudos"); carla.enviar("Preservada nos Estudos"); carla.ate(MENSAGEM);
                ana.enviar("Mensagem antiga"); var antiga=ana.ate(MENSAGEM); bruno.ate(MENSAGEM);
                ana.enviar("/limpar confirmar"); assertTrue(ana.ate(ERRO).campo(0).contains("/limpar"));
                ana.enviar("/LIMPARHISTORICO"); assertTrue(ana.ateResposta("Confirmar limpeza do histórico").campo(0).contains("Geral"));
                bruno.enviar("/ping"); Evento e;
                do { e=bruno.ler(); assertNotEquals(HISTORICO_LIMPO,e.tipo()); } while(e.tipo()!=PONG);
                ana.enviar("/CLEAR CONFIRMAR");
                var limpeza=ana.ate(HISTORICO_LIMPO); assertEquals(List.of("geral","Geral","Ana","1"),limpeza.campos());
                assertEquals(limpeza,bruno.ate(HISTORICO_LIMPO));
                carla.enviar("/ping"); do { e=carla.ler(); assertNotEquals(HISTORICO_LIMPO,e.tipo()); } while(e.tipo()!=PONG);
                try(var novo=new Cliente(servidor,"Davi")) {
                    assertTrue(novo.historico.isEmpty()); novo.entrarSala("estudos");
                    assertEquals("Preservada nos Estudos",novo.historico.get(0).campo(2));
                }
                ana.enviar("Depois da limpeza"); var nova=ana.ate(MENSAGEM);
                assertTrue(Long.parseLong(nova.campo(0))>Long.parseLong(antiga.campo(0)));
            }
        }
    }
    private static final class Cliente implements AutoCloseable {
        final Socket socket; final PrintWriter writer; final BufferedReader reader; final List<Evento> historico = new ArrayList<>();
        Cliente(Servidor s, String nome) throws IOException {
            socket = new Socket("127.0.0.1", s.porta()); socket.setSoTimeout(8000);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)); writer.println(Protocolo.apresentar(new model.Usuario(nome)));
            assertEquals(BEM_VINDO, ler().tipo());
            Evento e; do { e = ler(); if (e.tipo() == MENSAGEM) historico.add(e); } while (e.tipo() != HISTORICO_FIM);
        }
        Evento ler() throws IOException { String linha = reader.readLine(); if (linha == null) throw new EOFException(); return Protocolo.decodificar(linha); }
        Evento ate(Evento.Tipo tipo) throws IOException { for (int i = 0; i < 500; i++) { var e = ler(); if (e.tipo() == tipo) return e; } throw new IOException("Evento não recebido: " + tipo); }
        Evento ateResposta(String titulo) throws IOException { for (int i = 0; i < 500; i++) { var e = ler(); if (e.tipo() == SISTEMA && e.campos().size() >= 2 && e.campo(1).equals(titulo)) return e; } throw new IOException("Resposta não recebida: " + titulo); }
        void enviar(String texto) { writer.println(texto); }
        Evento entrarSala(String id) throws IOException {
            enviar("/join " + id); var entrada = ate(BEM_VINDO); historico.clear();
            Evento e; do { e = ler(); if (e.tipo() == MENSAGEM) historico.add(e); } while (e.tipo() != HISTORICO_FIM);
            return entrada;
        }
        @Override public void close() throws IOException { socket.close(); }
    }
}
