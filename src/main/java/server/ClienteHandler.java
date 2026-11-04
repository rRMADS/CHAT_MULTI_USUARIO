package server;

import model.Usuario;
import service.*;
import persistence.PersistenciaException;
import protocol.Evento;
import protocol.Protocolo;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import static protocol.Evento.Tipo.*;

public final class ClienteHandler implements Runnable, ConexaoCliente {
    private final Socket socket;
    private final ChatService chat;
    private final CommandService comandos;
    private final ArrayBlockingQueue<Evento> saida = new ArrayBlockingQueue<>(256);
    private volatile boolean ativo = true;
    private Usuario usuario;
    private volatile boolean encerrarAposErro;
    private final CountDownLatch erroEntregue = new CountDownLatch(1);
    public ClienteHandler(Socket socket, ChatService chat, CommandService comandos) {
        this.socket = socket; this.chat = chat; this.comandos = comandos;
    }
    @Override public Usuario getUsuario() { return usuario; }
    @Override public void run() {
        Thread writer = new Thread(this::escrever, "chat-saida-" + socket.getPort()); writer.setDaemon(true); writer.start();
        try {
            // O socket é o dono dos streams e será fechado no finally, depois de enviar eventuais erros.
            var leitor = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            socket.setSoTimeout(10_000);
            String nome = lerLinhaLimitada(leitor, 30);
            if (nome == null) return;
            usuario = new Usuario(nome); chat.conectar(this);
            socket.setSoTimeout(90_000);
            long ultimaDigitacao = 0;
            while (ativo) {
                String linha = lerLinhaLimitada(leitor, 2048); if (linha == null) break;
                if (linha.equals("/typing")) {
                    long agora = System.nanoTime();
                    if (agora - ultimaDigitacao < 1_000_000_000L) continue;
                    ultimaDigitacao = agora;
                }
                try { comandos.executar(this, linha); }
                catch (IllegalArgumentException | PersistenciaException e) { enviar(Evento.de(ERRO, e.getMessage())); }
            }
        } catch (IllegalArgumentException | PersistenciaException e) {
            // Permite que o writer entregue a recusa do handshake antes de fechar o socket.
            enviarErroFinal(e.getMessage());
        } catch (IOException | RuntimeException e) {
            if (ativo) System.err.println("Conexão encerrada (" + (usuario == null ? "sem apelido" : usuario) + "): " + e.getMessage());
        } finally {
            chat.desconectar(this); fechar(); writer.interrupt();
        }
    }
    public static String lerLinhaLimitada(Reader leitor, int limite) throws IOException {
        StringBuilder texto = new StringBuilder();
        int c;
        while ((c = leitor.read()) != -1) {
            if (c == '\n') return texto.toString();
            if (c == '\r') continue;
            if (texto.length() >= limite) throw new IOException("Linha excede o limite do protocolo.");
            texto.append((char)c);
        }
        return texto.isEmpty() ? null : texto.toString();
    }
    @Override public synchronized void enviar(Evento evento) {
        if (!ativo || !saida.offer(evento)) { fechar(); throw new IllegalStateException("Cliente desconectado ou lento demais."); }
    }
    private void escrever() {
        try (var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            while (ativo) {
                Evento evento = saida.poll(1, TimeUnit.SECONDS);
                if (evento == null) continue;
                synchronized (writer) { writer.write(Protocolo.codificar(evento)); writer.newLine(); writer.flush(); }
                if (evento.tipo() == ERRO && encerrarAposErro) { erroEntregue.countDown(); break; }
            }
        } catch (IOException | InterruptedException e) { if (e instanceof InterruptedException) Thread.currentThread().interrupt(); }
        finally { fechar(); }
    }
    private void enviarErroFinal(String texto) {
        try {
            encerrarAposErro = true;
            enviar(Evento.de(ERRO, texto));
            erroEntregue.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (RuntimeException e) { System.err.println("Não foi possível enviar a recusa: " + e.getMessage()); }
    }
    @Override public void fechar() {
        ativo = false;
        try { socket.close(); } catch (IOException e) { System.err.println("Erro ao fechar conexão: " + e.getMessage()); }
    }
}
