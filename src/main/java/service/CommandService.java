package service;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import protocol.Evento;
import static protocol.Evento.Tipo.*;
public final class CommandService {
    private static final Pattern PRIVADA = Pattern.compile("^(?:\"([^\"]+)\"|([^ ]+)) +(.+)$");
    private final ChatService chat;
    private final Map<String, BiConsumer<ConexaoCliente, String>> comandos;
    public CommandService(ChatService chat) {
        this.chat = chat;
        comandos = Map.of(
            "/help", (c, a) -> c.enviar(Evento.de(SISTEMA, "/salas · /join sala · /users · /history [1–200] · /buscar texto · /msg \"Nome com espaços\" texto · /quit")),
            "/users", (c, a) -> chat.publicarUsuarios(),
            "/history", (c, a) -> chat.buscarHistorico(c, a.isBlank() ? 50 : Integer.parseInt(a)),
            "/buscar", chat::buscarTexto,
            "/msg", this::enviarPrivada,
            "/typing", (c, a) -> chat.digitando(c),
            "/ping", (c, a) -> c.enviar(Evento.de(PONG)),
            "/quit", (c, a) -> c.fechar()
        );
    }
    private void enviarPrivada(ConexaoCliente cliente, String argumentos) {
        var partes = PRIVADA.matcher(argumentos);
        if (!partes.matches()) throw new IllegalArgumentException("Use /msg apelido mensagem ou /msg \"Nome com espaços\" mensagem.");
        String destinatario = partes.group(1) != null ? partes.group(1) : partes.group(2);
        chat.enviarPrivada(cliente, destinatario, partes.group(3));
    }
    public void executar(ConexaoCliente cliente, String linha) {
        if (!linha.startsWith("/")) { chat.enviarMensagem(cliente, linha); return; }
        String[] partes = linha.split(" +", 2);
        var comando = comandos.get(partes[0].toLowerCase(java.util.Locale.ROOT));
        if (comando == null) throw new IllegalArgumentException("Comando desconhecido. Use /help.");
        try { comando.accept(cliente, partes.length == 2 ? partes[1].strip() : ""); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Informe uma quantidade entre 1 e 200."); }
    }
}
