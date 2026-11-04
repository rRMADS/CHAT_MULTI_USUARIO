package service;

import model.Sala;
import protocol.Evento;
import protocol.ComandoChat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static protocol.Evento.Tipo.SALAS;

/** Coordena a associação de cada conexão a uma única conversa. Não faz I/O de rede ou SQL. */
public final class SalaService {
    private record Conversa(Sala sala, ChatService chat, CommandService comandos) {}
    private final Map<String, Conversa> salas = new LinkedHashMap<>();
    private final Map<ConexaoCliente, Conversa> participantes = new IdentityHashMap<>();

    public SalaService(Map<Sala, ChatService> conversas) {
        conversas.forEach((sala, chat) -> {
            if (salas.putIfAbsent(sala.id(), new Conversa(sala, chat, new CommandService(chat, () -> "\n\nSalas deste servidor:\n" + textoSalas()))) != null)
                throw new IllegalArgumentException("Identificador de sala repetido: " + sala.id());
        });
        if (!salas.containsKey(Sala.GERAL.id())) throw new IllegalArgumentException("A sala Geral é obrigatória.");
    }
    public static List<Sala> salasPadrao() {
        return Sala.padrao();
    }
    public synchronized void conectar(ConexaoCliente cliente) {
        if (participantes.containsKey(cliente)) throw new IllegalArgumentException("Esta conexão já entrou em uma sala.");
        var geral = salas.get(Sala.GERAL.id()); geral.chat().conectar(cliente);
        participantes.put(cliente, geral); publicarSalas();
    }
    public synchronized void desconectar(ConexaoCliente cliente) {
        var anterior = participantes.remove(cliente);
        if (anterior != null) { anterior.chat().desconectar(cliente); publicarSalas(); }
    }
    public synchronized void trocarSala(ConexaoCliente cliente, String id) {
        var anterior = exigirParticipante(cliente);
        var proxima = localizarSala(id);
        if (anterior == proxima) {
            enviarSalas(cliente); cliente.enviar(Evento.resposta("Sala atual", "Você já está na sala " + proxima.sala().nome() + ".")); return;
        }
        // Admite no destino antes de remover da origem. Se o banco ou o apelido falhar, preserva a sala atual.
        // O lock da central impede que outra mensagem seja roteada durante a movimentação.
        proxima.chat().conectar(cliente);
        participantes.put(cliente, proxima);
        anterior.chat().desconectar(cliente);
        publicarSalas();
        cliente.enviar(Evento.resposta("Sala alterada", "Você entrou na sala " + proxima.sala().nome() + ". Você está vendo apenas as mensagens e os participantes desta sala."));
    }
    public synchronized void executar(ConexaoCliente cliente, String linha) {
        var atual = exigirParticipante(cliente);
        var pedido = ComandoChat.ler(linha);
        if (pedido.isPresent() && pedido.get().nome().equals("/join")) {
            if (pedido.get().argumentos().isBlank()) throw new IllegalArgumentException("Informe uma sala. Exemplos disponíveis: " + exemplosSalas());
            trocarSala(cliente, pedido.get().argumentos());
        } else if (pedido.isPresent() && pedido.get().nome().equals("/salas")) {
            enviarSalas(cliente); cliente.enviar(Evento.resposta("Salas disponíveis", "Você está em " + atual.sala().nome() + ".\n\n" + textoSalas()));
        }
        else atual.comandos().executar(cliente, linha);
    }
    private Conversa localizarSala(String entrada) {
        String nome = ComandoChat.aparar(entrada);
        if (nome.length() >= 2 && ((nome.startsWith("\"") && nome.endsWith("\"")) || (nome.startsWith("'") && nome.endsWith("'")))) nome = ComandoChat.aparar(nome.substring(1, nome.length() - 1));
        var porId = salas.get(nome.toLowerCase(Locale.ROOT)); if (porId != null) return porId;
        String comparacao = normalizarNome(nome);
        var porNome = salas.values().stream().filter(s -> normalizarNome(s.sala().nome()).equals(comparacao)).toList();
        if (porNome.size() == 1) return porNome.get(0);
        if (porNome.size() > 1) throw new IllegalArgumentException("Mais de uma sala tem esse nome. Use um identificador: " + porNome.stream().map(s -> "/join " + s.sala().id()).collect(java.util.stream.Collectors.joining(" · ")));
        throw new IllegalArgumentException("Sala não encontrada: " + entrada + ". Experimente " + exemplosSalas() + ". Use /salas para ver os nomes e identificadores.");
    }
    private static String normalizarNome(String nome) {
        return Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replaceAll("[\\s\\p{Z}]+", " ").toLowerCase(Locale.ROOT);
    }
    private String exemplosSalas() { return salas.values().stream().limit(4).map(s -> "/join " + s.sala().id()).collect(java.util.stream.Collectors.joining(" · ")); }
    private String textoSalas() {
        return salas.values().stream().map(s -> s.sala().nome() + " — " + s.chat().quantidadeUsuarios() + " pessoa(s) — /join " + s.sala().id()).collect(java.util.stream.Collectors.joining("\n"));
    }
    private Conversa exigirParticipante(ConexaoCliente cliente) {
        var atual = participantes.get(cliente);
        if (atual == null) throw new IllegalArgumentException("Entre em uma sala para conversar.");
        return atual;
    }
    private void enviarSalas(ConexaoCliente cliente) {
        var atual = exigirParticipante(cliente);
        List<String> campos = new ArrayList<>(); campos.add(atual.sala().id());
        for (var conversa : salas.values()) {
            campos.add(conversa.sala().id()); campos.add(conversa.sala().nome());
            campos.add(Integer.toString(conversa.chat().quantidadeUsuarios()));
        }
        cliente.enviar(new Evento(SALAS, campos));
    }
    private void publicarSalas() {
        for (var cliente : new ArrayList<>(participantes.keySet())) {
            try { enviarSalas(cliente); }
            catch (RuntimeException e) {
                var atual = participantes.remove(cliente);
                if (atual != null) atual.chat().desconectar(cliente);
                cliente.fechar();
            }
        }
    }
}
