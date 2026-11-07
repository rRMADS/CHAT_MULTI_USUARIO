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
public final class SalaService implements AutoCloseable {
    private record Conversa(Sala sala, ChatService chat, CommandService comandos) {}
    private final Map<String, Conversa> salas = new LinkedHashMap<>();
    private final Map<ConexaoCliente, Conversa> participantes = new IdentityHashMap<>();
    private final ia.AssistenteIA assistente;
    private static final class Consulta {
        final Conversa conversa; final String pergunta, alcance; long versao;
        java.util.concurrent.Future<?> tarefa;
        Consulta(Conversa conversa,String pergunta,String alcance) { this.conversa=conversa; this.pergunta=pergunta; this.alcance=alcance; }
    }
    private final Map<ConexaoCliente,Consulta> consultas=new IdentityHashMap<>();
    private final Map<ConexaoCliente,Long> ultimaConsulta=new IdentityHashMap<>();

    public SalaService(Map<Sala, ChatService> conversas) {
        this(conversas,new ia.AssistenteIA(null));
    }
    public SalaService(Map<Sala, ChatService> conversas,ia.AssistenteIA assistente) {
        this.assistente=assistente;
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
        cliente.enviar(Evento.de(Evento.Tipo.IA_DISPONIVEL,Boolean.toString(assistente.disponivel())));
    }
    public synchronized void desconectar(ConexaoCliente cliente) {
        cancelarConsulta(cliente); ultimaConsulta.remove(cliente);
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
        cancelarConsulta(cliente);
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
        else if(pedido.isPresent() && pedido.get().nome().equals("/ia")) perguntarIA(cliente,atual,pedido.get().argumentos());
        else atual.comandos().executar(cliente, linha);
    }
    private void perguntarIA(ConexaoCliente cliente,Conversa conversa,String argumentos) {
        try {
            String[] partes=argumentos.strip().split("\\s+",2);
            if(partes.length!=2 || !(partes[0].equalsIgnoreCase("privado") || partes[0].equalsIgnoreCase("sala")))
                throw new IllegalArgumentException("Use /ia privado sua pergunta ou /ia sala sua pergunta. No modo sala, a pergunta e a resposta são públicas e ficam no histórico.");
            String pergunta=partes[1].strip(), alcance=partes[0].toLowerCase(Locale.ROOT);
            if(pergunta.isBlank() || pergunta.length()>450) throw new IllegalArgumentException("A pergunta deve ter de 1 a 450 caracteres.");
            if(consultas.containsKey(cliente)) throw new IllegalArgumentException("Aguarde a resposta da sua pergunta anterior.");
            long agora=System.nanoTime(); Long anterior=ultimaConsulta.get(cliente);
            if(anterior!=null && agora-anterior<java.util.concurrent.TimeUnit.SECONDS.toNanos(10)) throw new IllegalArgumentException("Aguarde alguns segundos entre as perguntas à IA.");
            Consulta consulta=new Consulta(conversa,pergunta,alcance);
            consulta.tarefa=assistente.perguntar(pergunta,() -> {
                if(alcance.equals("sala")) conversa.chat().enviarMensagem(cliente,"Pergunta à IA: "+pergunta);
                consulta.versao=conversa.chat().versaoHistorico();
                cliente.enviar(Evento.de(Evento.Tipo.IA_INICIO,alcance,conversa.sala().id(),pergunta));
            }, resposta -> concluirConsulta(cliente,consulta,resposta,null), erro -> concluirConsulta(cliente,consulta,null,erro));
            consultas.put(cliente,consulta); ultimaConsulta.put(cliente,agora);
        } catch(RuntimeException e) { cliente.enviar(Evento.de(Evento.Tipo.IA_ERRO,e instanceof IllegalArgumentException ? e.getMessage() : "Não foi possível iniciar a consulta à IA.")); }
    }
    private synchronized void concluirConsulta(ConexaoCliente cliente,Consulta consulta,String resposta,String erro) {
        if(consultas.get(cliente)!=consulta || participantes.get(cliente)!=consulta.conversa) return;
        consultas.remove(cliente);
        try {
            if(erro!=null) { cliente.enviar(Evento.de(Evento.Tipo.IA_ERRO,erro)); return; }
            if(resposta==null || resposta.isBlank()) throw new IllegalArgumentException("A IA não retornou uma resposta.");
            if(resposta.length()>8000) resposta=resposta.substring(0,7990)+"\n[…]";
            if(consulta.alcance.equals("sala")) consulta.conversa.chat().publicarRespostaIA(resposta,consulta.versao);
            cliente.enviar(Evento.de(Evento.Tipo.IA_RESPOSTA,consulta.alcance,consulta.conversa.sala().id(),consulta.pergunta,resposta));
        } catch(RuntimeException e) { cliente.enviar(Evento.de(Evento.Tipo.IA_ERRO,e instanceof IllegalArgumentException ? e.getMessage() : "Não foi possível publicar a resposta da IA.")); }
    }
    private void cancelarConsulta(ConexaoCliente cliente) {
        Consulta consulta=consultas.remove(cliente);
        if(consulta!=null) {
            assistente.cancelar(consulta.tarefa);
            try { cliente.enviar(Evento.de(Evento.Tipo.IA_ERRO,"A consulta à IA foi cancelada ao sair da sala.")); } catch(RuntimeException ignored) { }
        }
    }
    @Override public synchronized void close() { consultas.values().forEach(c -> assistente.cancelar(c.tarefa)); consultas.clear(); assistente.close(); }
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
