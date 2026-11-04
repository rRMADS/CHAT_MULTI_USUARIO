package client;

import protocol.Evento;
import protocol.Protocolo;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ConexaoChat implements AutoCloseable {
    private final Consumer<Evento> receber;
    private final Consumer<String> encerrar;
    private final AtomicBoolean fechada = new AtomicBoolean();
    private final BlockingQueue<String> saida = new ArrayBlockingQueue<>(128);
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cliente-heartbeat"); t.setDaemon(true); return t;
    });
    private volatile Socket socket;
    public ConexaoChat(Consumer<Evento> receber, Consumer<String> encerrar) { this.receber = receber; this.encerrar = encerrar; }
    public void conectar(String host, int porta, String nome) {
        Thread rede = new Thread(() -> {
            try {
                socket = new Socket();
                if (fechada.get()) { socket.close(); return; }
                socket.connect(new InetSocketAddress(host, porta), 5000); socket.setTcpNoDelay(true); socket.setSoTimeout(90_000);
                var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                writer.write(nome); writer.newLine(); writer.flush();
                Thread envio = new Thread(() -> escrever(writer), "cliente-envio"); envio.setDaemon(true); envio.start();
                heartbeat.scheduleAtFixedRate(() -> enviar("/ping"), 25, 25, TimeUnit.SECONDS);
                try (var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                    String linha;
                    while (!fechada.get() && (linha = reader.readLine()) != null) receber.accept(Protocolo.decodificar(linha));
                }
                terminar("A conexão foi encerrada. Conecte-se novamente para recuperar o histórico.");
            } catch (ConnectException e) { terminar("Não foi possível conectar. Confira se o servidor está iniciado e se o IP e a porta estão corretos."); }
            catch (UnknownHostException e) { terminar("Endereço não encontrado. Confira o IP ou nome do servidor."); }
            catch (IOException | RuntimeException e) { terminar("Conexão interrompida. Confira o servidor e sua rede."); }
        }, "cliente-rede"); rede.setDaemon(true); rede.start();
    }
    private void escrever(BufferedWriter writer) {
        try {
            while (!fechada.get()) {
                String texto = saida.poll(1, TimeUnit.SECONDS);
                if (texto != null) { writer.write(texto); writer.newLine(); writer.flush(); }
            }
        } catch (IOException e) { terminar("Não foi possível enviar. Conecte-se novamente."); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    public void enviar(String texto) {
        if (fechada.get()) return;
        if (texto.indexOf('\n') >= 0 || texto.indexOf('\r') >= 0 || texto.length() > 2048)
            throw new IllegalArgumentException("Envie uma mensagem por vez, em uma linha.");
        if (!saida.offer(texto)) terminar("Muitas mensagens pendentes. Conecte-se novamente.");
    }
    private void terminar(String motivo) {
        if (!fechada.compareAndSet(false, true)) return;
        heartbeat.shutdownNow();
        if (socket != null) try { socket.close(); } catch (IOException e) { System.err.println(e.getMessage()); }
        encerrar.accept(motivo);
    }
    @Override public void close() { terminar("Você saiu do chat."); }
}
