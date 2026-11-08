package client;

import model.Usuario;

import protocol.Evento;
import protocol.LeitorLinhas;
import protocol.Protocolo;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Uma sessão mantém a identidade nas reconexões, mas nunca repete os envios pendentes. */
public final class ConexaoChat implements AutoCloseable {
    private final Consumer<Evento> receber;
    private final Consumer<String> encerrar, reconectando;
    private final AtomicBoolean fechada = new AtomicBoolean(), iniciada = new AtomicBoolean();
    private final String sessao = UUID.randomUUID().toString();
    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cliente-heartbeat");
                t.setDaemon(true);
                return t;
            });
    private volatile Canal canal;
    private volatile Usuario usuario;
    private volatile String sala = "geral";
    private volatile boolean pronta, saidaSolicitada;
    private Thread rede;

    private static final class Canal implements AutoCloseable {
        final Socket socket = new Socket();
        final BlockingQueue<String> saida = new ArrayBlockingQueue<>(128);
        Thread envio;

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            if (envio != null) envio.interrupt();
            saida.clear();
        }
    }

    public ConexaoChat(Consumer<Evento> receber, Consumer<String> encerrar) {
        this(receber, encerrar, m -> {});
    }

    public ConexaoChat(
            Consumer<Evento> receber, Consumer<String> encerrar, Consumer<String> reconectando) {
        this.receber = receber;
        this.encerrar = encerrar;
        this.reconectando = reconectando;
    }

    public void atualizarIdentidade(Usuario usuario) {
        this.usuario = usuario;
    }

    public void conectar(String host, int porta, Usuario usuario) {
        if (!iniciada.compareAndSet(false, true))
            throw new IllegalStateException("Sessão já iniciada.");
        this.usuario = usuario;
        heartbeat.scheduleAtFixedRate(() -> enviar("/ping"), 25, 25, TimeUnit.SECONDS);
        rede = new Thread(() -> executar(host, porta), "cliente-rede");
        rede.setDaemon(true);
        rede.start();
    }

    private void executar(String host, int porta) {
        boolean autenticada = false;
        int espera = 1;
        while (!fechada.get()) {
            Canal atual = new Canal();
            canal = atual;
            String motivo = "Conexão interrompida. Confira o servidor e sua rede.";
            boolean recusada = false;
            try {
                if (fechada.get()) return;
                atual.socket.connect(new InetSocketAddress(host, porta), 5000);
                atual.socket.setTcpNoDelay(true);
                atual.socket.setSoTimeout(90_000);
                var writer =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        atual.socket.getOutputStream(), StandardCharsets.UTF_8));
                writer.write(Protocolo.apresentar(this.usuario, sessao, sala));
                writer.newLine();
                writer.flush();
                atual.envio = new Thread(() -> escrever(atual, writer), "cliente-envio");
                atual.envio.setDaemon(true);
                atual.envio.start();
                var reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        atual.socket.getInputStream(), StandardCharsets.UTF_8));
                String linha;
                boolean admitida = false;
                while (!fechada.get()
                        && (linha = LeitorLinhas.ler(reader, LeitorLinhas.LIMITE_EVENTO)) != null) {
                    Evento evento = Protocolo.decodificar(linha);
                    if (evento.tipo() == Evento.Tipo.BEM_VINDO) {
                        admitida = true;
                        autenticada = true;
                        pronta = true;
                        espera = 1;
                        sala = evento.campo(1);
                        this.usuario = new Usuario(evento.campo(0), evento.campo(3));
                    }
                    if (evento.tipo() == Evento.Tipo.ERRO && !admitida) {
                        recusada = true;
                        motivo = evento.campo(0);
                    }
                    if (evento.tipo() == Evento.Tipo.PERFIL_ALTERADO
                            && this.usuario.nome().equalsIgnoreCase(evento.campo(0)))
                        this.usuario = new Usuario(evento.campo(1), evento.campo(2));
                    receber.accept(evento);
                    if (recusada) break;
                }
            } catch (ConnectException e) {
                motivo =
                        "Não foi possível conectar. Confira se o servidor está iniciado e se o IP e a porta estão corretos.";
            } catch (UnknownHostException e) {
                motivo = "Endereço não encontrado. Confira o IP ou nome do servidor.";
            } catch (IOException | RuntimeException e) {
                /* A sessão já admitida tentará novamente. */
            } finally {
                pronta = false;
                atual.close();
                if (canal == atual) canal = null;
            }
            if (fechada.get()) return;
            if (!autenticada || recusada || saidaSolicitada) {
                terminar(saidaSolicitada ? "Você saiu do chat." : motivo);
                return;
            }
            reconectando.accept(
                    "Conexão perdida. Tentando voltar à sala em "
                            + espera
                            + " s… Seu rascunho será preservado.");
            try {
                Thread.sleep(espera * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            espera = Math.min(30, espera * 2);
        }
    }

    private void escrever(Canal atual, BufferedWriter writer) {
        try {
            while (!fechada.get() && !atual.socket.isClosed()) {
                String texto = atual.saida.poll(1, TimeUnit.SECONDS);
                if (texto != null) {
                    if (protocol.ComandoChat.ler(texto)
                            .map(c -> c.nome().equals("/quit"))
                            .orElse(false)) saidaSolicitada = true;
                    writer.write(texto);
                    writer.newLine();
                    writer.flush();
                }
            }
        } catch (IOException e) {
            atual.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void enviar(String texto) {
        if (texto.indexOf('\n') >= 0
                || texto.indexOf('\r') >= 0
                || texto.length() > LeitorLinhas.LIMITE_REQUISICAO)
            throw new IllegalArgumentException("Envie uma mensagem por vez, em uma linha.");
        Canal atual = canal;
        if (fechada.get() || !pronta || atual == null) return;
        if (!atual.saida.offer(texto)) atual.close();
    }

    public void enviarEvento(Evento evento) {
        enviar(Protocolo.PREFIXO_PEDIDO + Protocolo.codificar(evento));
    }

    public boolean enviarAguardando(Evento evento) {
        String texto = Protocolo.PREFIXO_PEDIDO + Protocolo.codificar(evento);
        if (texto.length() > LeitorLinhas.LIMITE_REQUISICAO)
            throw new IllegalArgumentException("Bloco de arquivo excessivo.");
        Canal atual = canal;
        if (fechada.get() || !pronta || atual == null) return false;
        try {
            return atual.saida.offer(texto, 5, TimeUnit.SECONDS) && canal == atual && pronta;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void terminar(String motivo) {
        if (!fechada.compareAndSet(false, true)) return;
        pronta = false;
        heartbeat.shutdownNow();
        Canal atual = canal;
        if (atual != null) atual.close();
        if (rede != null && rede != Thread.currentThread()) rede.interrupt();
        encerrar.accept(motivo);
    }

    @Override
    public void close() {
        terminar("Você saiu do chat.");
    }
}
