package client;

import static org.junit.jupiter.api.Assertions.*;

import static protocol.Evento.Tipo.*;

import model.*;

import org.junit.jupiter.api.Test;

import persistence.jdbc.*;

import protocol.Evento;

import server.Servidor;

import service.*;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class ReconexaoTest {
    private final ConnectionFactory f =
            new ConnectionFactory(
                    "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");

    private Servidor servidor(int porta) throws Exception {
        var r = new JdbcMensagemRepository(f);
        r.inicializar();
        var salas = new LinkedHashMap<Sala, ChatService>();
        for (var sala : List.of(Sala.GERAL, new Sala("estudos", "Estudos")))
            salas.put(
                    sala,
                    new ChatService(
                            new JdbcMensagemRepository(f, sala),
                            new ClientRegistry(),
                            Clock.systemUTC(),
                            50,
                            sala));
        var s = new Servidor("127.0.0.1", porta, 20, new SalaService(salas));
        s.iniciar();
        return s;
    }

    private Evento ate(BlockingQueue<Evento> fila, Evento.Tipo tipo) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < limite) {
            var e = fila.poll(1, TimeUnit.SECONDS);
            if (e != null && e.tipo() == tipo) return e;
        }
        fail("Evento não recebido: " + tipo);
        return null;
    }

    @Test
    void reinicioDoServidorRetomaSalaPerfilEHistoricoSemReenviarPendencias() throws Exception {
        Servidor s = servidor(0);
        int porta = s.porta();
        var fila = new LinkedBlockingQueue<Evento>();
        var caiu = new CountDownLatch(1);
        var finais = new AtomicInteger();
        try (var c =
                new ConexaoChat(fila::add, m -> finais.incrementAndGet(), m -> caiu.countDown())) {
            c.conectar("127.0.0.1", porta, new Usuario("Ana", "dragon"));
            ate(fila, BEM_VINDO);
            ate(fila, HISTORICO_FIM);
            c.enviar("/join estudos");
            assertEquals("estudos", ate(fila, BEM_VINDO).campo(1));
            ate(fila, HISTORICO_FIM);
            c.enviar("/perfil party Ana Nova");
            ate(fila, PERFIL_ALTERADO);
            c.enviar("Publicada uma vez");
            ate(fila, MENSAGEM);
            s.close();
            assertTrue(caiu.await(5, TimeUnit.SECONDS));
            c.enviar("Não deve ser reenviada");
            s = servidor(porta);
            fila.clear();
            var entrada = ate(fila, BEM_VINDO);
            assertEquals("estudos", entrada.campo(1));
            assertEquals("Ana Nova", entrada.campo(0));
            assertEquals("party", entrada.campo(3));
            assertEquals("Publicada uma vez", ate(fila, MENSAGEM).campo(2));
            ate(fila, HISTORICO_FIM);
            var r = new JdbcMensagemRepository(f, new Sala("estudos", "Estudos"));
            assertEquals(1, r.buscarUltimas(200).size());
            assertEquals(0, finais.get());
            c.enviar("Depois da volta");
            assertEquals("Depois da volta", ate(fila, MENSAGEM).campo(2));
        } finally {
            s.close();
        }
        assertEquals(1, finais.get());
    }

    @Test
    void sairIntencionalmenteNaoTentaReconectar() throws Exception {
        try (var s = servidor(0)) {
            var fila = new LinkedBlockingQueue<Evento>();
            var fim = new CountDownLatch(1);
            var tentativas = new AtomicInteger();
            try (var c =
                    new ConexaoChat(
                            fila::add, m -> fim.countDown(), m -> tentativas.incrementAndGet())) {
                c.conectar("127.0.0.1", s.porta(), new Usuario("Ana"));
                ate(fila, BEM_VINDO);
                c.enviar("/quit");
                assertTrue(fim.await(5, TimeUnit.SECONDS));
                assertEquals(0, tentativas.get());
            }
        }
    }

    @Test
    void arquivoGrandeUsaFilaComEsperaERetornaBytesInteiros() throws Exception {
        try (var s = servidor(0)) {
            var fila = new LinkedBlockingQueue<Evento>();
            try (var c = new ConexaoChat(fila::add, m -> {}, m -> {})) {
                c.conectar("127.0.0.1", s.porta(), new Usuario("Ana"));
                ate(fila, BEM_VINDO);
                ate(fila, HISTORICO_FIM);
                byte[] dados = new byte[1800000];
                new Random(42).nextBytes(dados);
                String id = UUID.randomUUID().toString();
                assertTrue(
                        c.enviarAguardando(
                                Evento.de(
                                        ARQUIVO_INICIO,
                                        id,
                                        "material.zip",
                                        Integer.toString(dados.length),
                                        "application/zip",
                                        "1")));
                for (int n = 0, pos = 0; pos < dados.length; pos += 768, n++)
                    assertTrue(
                            c.enviarAguardando(
                                    Evento.de(
                                            ARQUIVO_PARTE,
                                            id,
                                            Integer.toString(n),
                                            Base64.getEncoder()
                                                    .encodeToString(
                                                            Arrays.copyOfRange(
                                                                    dados,
                                                                    pos,
                                                                    Math.min(
                                                                            dados.length,
                                                                            pos + 768))))));
                assertTrue(c.enviarAguardando(Evento.de(ARQUIVO_FIM, id)));
                var mensagem = ate(fila, MENSAGEM);
                String anexo = mensagem.campo(9);
                ate(fila, ARQUIVO_PRONTO);
                c.enviarEvento(Evento.de(ARQUIVO_PEDIR, anexo));
                ate(fila, DOWNLOAD_INICIO);
                var saida = new java.io.ByteArrayOutputStream();
                long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                boolean fim = false;
                while (System.nanoTime() < limite && !fim) {
                    var e = fila.poll(1, TimeUnit.SECONDS);
                    if (e == null) continue;
                    if (e.tipo() == DOWNLOAD_PARTE)
                        saida.writeBytes(Base64.getDecoder().decode(e.campo(2)));
                    if (e.tipo() == DOWNLOAD_FIM) fim = true;
                }
                assertTrue(fim);
                assertArrayEquals(dados, saida.toByteArray());
            }
        }
    }
}
