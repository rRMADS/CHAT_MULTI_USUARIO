package service;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import protocol.Evento;
import protocol.ComandoChat;
import static protocol.Evento.Tipo.*;
public final class CommandService {
    private static final Pattern PRIVADA = Pattern.compile("^(?:\"([^\"]+)\"|([^\\s\\p{Z}\"]+))[\\s\\p{Z}]+(.+)$");
    private final ChatService chat;
    private final Map<String, BiConsumer<ConexaoCliente, String>> comandos;
    public CommandService(ChatService chat) {
        this(chat, () -> "");
    }
    public CommandService(ChatService chat, Supplier<String> ajudaSalas) {
        this.chat = chat;
        comandos = Map.of(
            "/help", (c, a) -> c.enviar(Evento.resposta("Comandos disponíveis", textoAjuda() + ajudaSalas.get())),
            "/users", (c, a) -> {
                var usuarios = chat.usuariosOnline(); c.enviar(new Evento(USUARIOS, usuarios));
                c.enviar(Evento.resposta("Participantes da sala", String.join("\n", usuarios)));
            },
            "/history", (c, a) -> chat.buscarHistorico(c, a.isBlank() ? 50 : Integer.parseInt(a)),
            "/buscar", chat::buscarTexto,
            "/msg", this::enviarPrivada,
            "/typing", (c, a) -> {
                if (!a.isBlank() && !a.equalsIgnoreCase("stop")) throw new IllegalArgumentException("Estado de digitação inválido.");
                chat.digitando(c, a.isBlank());
            },
            "/ping", (c, a) -> c.enviar(Evento.de(PONG)),
            "/quit", (c, a) -> c.fechar()
        );
    }
    private static String textoAjuda() {
        return "/help ou /ajuda — mostra esta ajuda\n"
                + "/salas — lista as salas e os comandos para entrar\n"
                + "/join geral ou /sala geral — volta à sala de entrada\n"
                + "/users ou /usuarios — lista as pessoas desta sala\n"
                + "/history 50 ou /historico 50 — carrega as últimas mensagens\n"
                + "/buscar texto — procura no histórico da sala\n"
                + "/msg \"Ana Silva\" Olá — envia uma mensagem privada\n"
                + "/quit ou /sair — sai do chat";
    }
    private void enviarPrivada(ConexaoCliente cliente, String argumentos) {
        var partes = PRIVADA.matcher(argumentos);
        if (!partes.matches()) throw new IllegalArgumentException("Use /msg apelido mensagem ou /msg \"Nome com espaços\" mensagem.");
        String destinatario = partes.group(1) != null ? partes.group(1) : partes.group(2);
        chat.enviarPrivada(cliente, destinatario, partes.group(3));
    }
    public void executar(ConexaoCliente cliente, String linha) {
        var pedido = ComandoChat.ler(linha);
        if (pedido.isEmpty()) { chat.enviarMensagem(cliente, linha); return; }
        var comando = comandos.get(pedido.get().nome());
        if (comando == null) throw new IllegalArgumentException("Comando desconhecido: " + pedido.get().nome() + ". Use /help ou o botão Comandos.");
        try { comando.accept(cliente, pedido.get().argumentos()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Informe uma quantidade entre 1 e 200."); }
    }
}
