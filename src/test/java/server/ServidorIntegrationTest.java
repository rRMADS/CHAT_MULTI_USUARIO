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
            duplicado.setSoTimeout(5000); var w = new PrintWriter(new OutputStreamWriter(duplicado.getOutputStream(), StandardCharsets.UTF_8), true); w.println("ana");
            var reader = new BufferedReader(new InputStreamReader(duplicado.getInputStream(), StandardCharsets.UTF_8));
            assertEquals(ERRO, Protocolo.decodificar(reader.readLine()).tipo());
            ana.enviar("Ainda aqui"); assertEquals("Ainda aqui", ana.ate(MENSAGEM).campo(2));
        }
    }
    @Test void leituraLimitadaRecusaLinhaGigante() { assertThrows(IOException.class, () -> ClienteHandler.lerLinhaLimitada(new StringReader("x".repeat(2050)), 2048)); }
    private static final class Cliente implements AutoCloseable {
        final Socket socket; final PrintWriter writer; final BufferedReader reader; final List<Evento> historico = new ArrayList<>();
        Cliente(Servidor s, String nome) throws IOException {
            socket = new Socket("127.0.0.1", s.porta()); socket.setSoTimeout(8000);
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)); writer.println(nome);
            assertEquals(BEM_VINDO, ler().tipo());
            Evento e; do { e = ler(); if (e.tipo() == MENSAGEM) historico.add(e); } while (e.tipo() != HISTORICO_FIM);
        }
        Evento ler() throws IOException { String linha = reader.readLine(); if (linha == null) throw new EOFException(); return Protocolo.decodificar(linha); }
        Evento ate(Evento.Tipo tipo) throws IOException { for (int i = 0; i < 500; i++) { var e = ler(); if (e.tipo() == tipo) return e; } throw new IOException("Evento não recebido: " + tipo); }
        void enviar(String texto) { writer.println(texto); }
        @Override public void close() throws IOException { socket.close(); }
    }
}
