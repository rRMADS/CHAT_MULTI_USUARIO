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
        comandos = new java.util.LinkedHashMap<>(Map.of(
            "/help", (c, a) -> c.enviar(Evento.resposta("Comandos disponíveis", textoAjuda() + ajudaSalas.get())),
            "/users", (c, a) -> {
                var usuarios = chat.usuariosOnline(); c.enviar(new Evento(USUARIOS, usuarios));
                c.enviar(Evento.resposta("Participantes da sala", String.join("\n", usuarios)));
            },
            "/history", (c, a) -> chat.buscarHistorico(c, a.isBlank() ? 50 : Integer.parseInt(a)),
            "/buscar", chat::buscarTexto,
            "/limpar", (c, a) -> {
                if (a.isBlank()) chat.solicitarLimpeza(c);
                else if (a.equalsIgnoreCase("confirmar")) chat.limparHistorico(c);
                else if (a.equalsIgnoreCase("cancelar")) chat.cancelarLimpeza(c);
                else throw new IllegalArgumentException("Use /limpar, /limpar confirmar ou /limpar cancelar. A limpeza vale somente para a sala atual.");
            },
            "/msg", this::enviarPrivada,
            "/typing", (c, a) -> {
                if (!a.isBlank() && !a.equalsIgnoreCase("stop")) throw new IllegalArgumentException("Estado de digitação inválido.");
                chat.digitando(c, a.isBlank());
            },
            "/ping", (c, a) -> c.enviar(Evento.de(PONG)),
            "/quit", (c, a) -> c.fechar()
        ));
        comandos.put("/responder",(c,a)->{ var p=partes(a); chat.responder(c,Long.parseLong(p[0]),p[1]); });
        comandos.put("/reagir",(c,a)->{ var p=partes(a); chat.reagir(c,Long.parseLong(p[0]),p[1]); });
        comandos.put("/fixar",(c,a)->chat.fixar(c,Long.parseLong(a),true));
        comandos.put("/desfixar",(c,a)->chat.fixar(c,Long.parseLong(a),false));
    }
    private static String[] partes(String argumentos) {
        var p=argumentos.strip().split("\\s+",2);
        if(p.length!=2) throw new IllegalArgumentException("Informe o número da mensagem e o texto ou emoji. Você também pode usar os botões da mensagem.");
        return p;
    }
    private static String textoAjuda() { return protocol.CatalogoComandos.ajuda(); }
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
        catch (NumberFormatException e) { throw new IllegalArgumentException("Informe um número válido. O histórico aceita de 1 a 200 mensagens."); }
    }
}
