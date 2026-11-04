package server;

import config.Config;
import persistence.jdbc.*;
import service.*;
import java.net.*;
import java.io.IOException;
import java.time.Clock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Servidor implements AutoCloseable {
    private final ChatService chat;
    private final CommandService comandos;
    private final ServerSocket escuta;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final int maxClientes;
    private volatile boolean ativo = true;
    private Thread aceitador;
    public Servidor(String bind, int porta, int maxClientes, ChatService chat) throws IOException {
        this.chat = chat; this.comandos = new CommandService(chat); this.maxClientes = maxClientes;
        escuta = new ServerSocket(); escuta.setReuseAddress(true); escuta.bind(new InetSocketAddress(bind, porta));
    }
    public int porta() { return escuta.getLocalPort(); }
    public synchronized void iniciar() {
        if (aceitador != null) throw new IllegalStateException("Servidor já iniciado");
        aceitador = new Thread(this::aceitar, "chat-servidor"); aceitador.start();
    }
    private void aceitar() {
        while (ativo) {
            try {
                Socket socket = escuta.accept(); socket.setTcpNoDelay(true);
                if (!ativo || sockets.size() >= maxClientes) { socket.close(); continue; }
                sockets.add(socket);
                var handler = new ClienteHandler(socket, chat, comandos);
                Thread cliente = new Thread(() -> {
                    try { handler.run(); } finally { sockets.remove(socket); }
                }, "chat-cliente-" + socket.getPort());
                cliente.setDaemon(true); cliente.start();
            } catch (IOException e) { if (ativo) System.err.println("Falha ao aceitar conexão: " + e.getMessage()); }
        }
    }
    @Override public void close() {
        ativo = false;
        try { escuta.close(); } catch (IOException e) { System.err.println(e.getMessage()); }
        for (var socket : sockets) try { socket.close(); } catch (IOException e) { System.err.println(e.getMessage()); }
    }
    public static void main(String[] args) throws Exception {
        Config config = Config.carregar(); Files.createDirectories(Path.of("data"));
        var repository = new JdbcMensagemRepository(new ConnectionFactory(config.banco(), config.usuarioBanco(), config.senhaBanco()));
        repository.inicializar();
        var chat = new ChatService(repository, new ClientRegistry(), Clock.systemDefaultZone(), config.historico());
        var servidor = new Servidor(config.bind(), config.porta(), config.maxClientes(), chat);
        Runtime.getRuntime().addShutdownHook(new Thread(servidor::close));
        servidor.iniciar();
        System.out.println("CONEXO | Servidor ativo na porta " + servidor.porta());
        System.out.println("Neste computador: 127.0.0.1:" + servidor.porta());
        var interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            var rede = interfaces.nextElement(); if (!rede.isUp() || rede.isLoopback()) continue;
            var enderecos = rede.getInetAddresses();
            while (enderecos.hasMoreElements()) {
                var ip = enderecos.nextElement();
                if (ip instanceof Inet4Address) System.out.println("Na sua rede: " + ip.getHostAddress() + ":" + servidor.porta());
            }
        }
        System.out.println("Histórico salvo em data/chat.mv.db | Ctrl+C para encerrar.");
    }
}
