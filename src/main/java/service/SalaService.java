package service;

import model.Sala;
import protocol.Evento;
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
            if (salas.putIfAbsent(sala.id(), new Conversa(sala, chat, new CommandService(chat))) != null)
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
        var proxima = salas.get(id.strip().toLowerCase(Locale.ROOT));
        if (proxima == null) throw new IllegalArgumentException("Sala não encontrada. Use /salas para ver as opções.");
        if (anterior == proxima) { enviarSalas(cliente); return; }
        // Admite no destino antes de remover da origem. Se o banco ou o apelido falhar, preserva a sala atual.
        // O lock da central impede que outra mensagem seja roteada durante a movimentação.
        proxima.chat().conectar(cliente);
        participantes.put(cliente, proxima);
        anterior.chat().desconectar(cliente);
        publicarSalas();
    }
    public synchronized void executar(ConexaoCliente cliente, String linha) {
        var atual = exigirParticipante(cliente);
        String[] partes = linha.split(" +", 2);
        String comando = partes[0].toLowerCase(Locale.ROOT);
        if (comando.equals("/join") || comando.equals("/sala")) {
            if (partes.length < 2 || partes[1].isBlank()) throw new IllegalArgumentException("Use /join identificador-da-sala. Consulte as salas na lista lateral.");
            trocarSala(cliente, partes[1]);
        } else if (comando.equals("/salas") || comando.equals("/rooms")) enviarSalas(cliente);
        else atual.comandos().executar(cliente, linha);
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
