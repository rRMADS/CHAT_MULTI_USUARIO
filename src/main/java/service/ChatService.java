package service;

import model.Mensagem;
import model.Citacao;
import model.Mencoes;
import model.Usuario;
import model.Sala;
import persistence.MensagemRepository;
import protocol.Evento;
import static protocol.Evento.Tipo.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.time.Instant;
import java.util.Map;
import java.util.IdentityHashMap;

public final class ChatService {
    private final MensagemRepository repository;
    private final ClientRegistry registry;
    private final Clock clock;
    private final int tamanhoHistorico;
    private final Sala sala;
    private long versaoHistorico;
    private final Map<ConexaoCliente, Instant> confirmacoesLimpeza = new IdentityHashMap<>();
    public ChatService(MensagemRepository repository, ClientRegistry registry, Clock clock, int tamanhoHistorico) {
        this(repository, registry, clock, tamanhoHistorico, Sala.GERAL);
    }
    public ChatService(MensagemRepository repository, ClientRegistry registry, Clock clock, int tamanhoHistorico, Sala sala) {
        this.repository = repository; this.registry = registry; this.clock = clock; this.tamanhoHistorico = tamanhoHistorico;
        this.sala = sala;
    }
    // O mesmo lock ordena histórico inicial, persistência e publicação. enviar() apenas enfileira, sem I/O.
    public synchronized void conectar(ConexaoCliente cliente) {
        var ultimas = repository.buscarUltimas(tamanhoHistorico);
        registry.adicionar(cliente);
        try {
            cliente.enviar(Evento.de(BEM_VINDO, cliente.getUsuario().nome(), sala.id(), sala.nome(),cliente.getUsuario().emoji().id()));
            historico(cliente, ultimas, "Últimas mensagens");
            enviarFixadas(cliente);
            broadcast(Evento.de(SISTEMA, cliente.getUsuario().nome() + " entrou no chat."));
            publicarUsuarios();
        } catch (RuntimeException e) { registry.remover(cliente); throw e; }
    }
    public synchronized void desconectar(ConexaoCliente cliente) {
        confirmacoesLimpeza.remove(cliente);
        if (registry.remover(cliente)) {
            broadcast(Evento.de(SISTEMA, cliente.getUsuario().nome() + " saiu do chat."));
            publicarUsuarios();
        }
    }
    public synchronized Mensagem enviarMensagem(ConexaoCliente cliente, String conteudo) {
        return responder(cliente,null,conteudo);
    }
    public synchronized Mensagem responder(ConexaoCliente cliente,Long id,String conteudo) {
        exigirConectado(cliente);
        Citacao citacao=id==null ? null : Citacao.de(exigirMensagem(id));
        Mensagem salva=repository.salvar(new Mensagem(null,cliente.getUsuario(),conteudo,LocalDateTime.now(clock),model.FormatoMensagem.TEXTO,citacao));
        broadcast(eventoMensagem(salva));
        for(var c:registry.snapshot()) if(c!=cliente && Mencoes.contem(salva.conteudo(),c.getUsuario().nome()))
            enviarSeguro(c,Evento.de(MENCAO,salva.id().toString(),salva.remetente().nome()));
        return salva;
    }
    public synchronized void enviarPrivada(ConexaoCliente origem, String apelido, String texto) {
        exigirConectado(origem);
        Usuario usuario = new Usuario(apelido);
        if (usuario.equals(origem.getUsuario())) throw new IllegalArgumentException("Escolha outra pessoa para enviar uma mensagem privada.");
        ConexaoCliente destino = registry.buscarPorUsuario(usuario).orElseThrow(() -> new IllegalArgumentException("Esta pessoa está offline."));
        Mensagem m = new Mensagem(null, origem.getUsuario(), texto, LocalDateTime.now(clock));
        Evento evento = Evento.de(PRIVADA, origem.getUsuario().nome(), destino.getUsuario().nome(), m.conteudo(), m.horario().toString(),origem.getUsuario().emoji().id());
        try { destino.enviar(evento); } catch (RuntimeException e) {
            desconectar(destino); destino.fechar(); throw new IllegalArgumentException("A pessoa se desconectou. Mensagem privada não enviada.");
        }
        origem.enviar(evento);
    }
    public synchronized void buscarHistorico(ConexaoCliente cliente, int quantidade) {
        if (quantidade < 1 || quantidade > 200) throw new IllegalArgumentException("Escolha de 1 a 200 mensagens.");
        historico(cliente, repository.buscarUltimas(quantidade), "Últimas " + quantidade + " mensagens");
    }
    public synchronized void buscarTexto(ConexaoCliente cliente, String texto) {
        if (texto.isBlank() || texto.length() > 100) throw new IllegalArgumentException("Busque um texto de 1 a 100 caracteres.");
        historico(cliente, repository.buscarPorTexto(texto, 100), "Busca: " + texto);
    }
    public synchronized void solicitarLimpeza(ConexaoCliente cliente) {
        exigirConectado(cliente);
        confirmacoesLimpeza.put(cliente, clock.instant().plusSeconds(30));
        cliente.enviar(Evento.resposta("Confirmar limpeza do histórico",
                "Isso apagará permanentemente todas as mensagens públicas da sala " + sala.nome()
                + " para todos os participantes. Outras salas e mensagens privadas serão preservadas.\n\n"
                + "Para confirmar, envie /limpar confirmar em até 30 segundos, sem trocar de sala.\n"
                + "Para cancelar, envie /limpar cancelar ou aguarde o prazo."));
    }
    public synchronized void cancelarLimpeza(ConexaoCliente cliente) {
        exigirConectado(cliente); confirmacoesLimpeza.remove(cliente);
        cliente.enviar(Evento.resposta("Limpeza cancelada", "Nenhuma mensagem foi apagada."));
    }
    public synchronized void limparHistorico(ConexaoCliente cliente) {
        exigirConectado(cliente);
        Instant prazo = confirmacoesLimpeza.remove(cliente);
        if (prazo == null || !clock.instant().isBefore(prazo))
            throw new IllegalArgumentException("Confirmação ausente ou expirada. Use /limpar nesta sala antes de enviar /limpar confirmar.");
        int removidas = repository.limpar();
        versaoHistorico++;
        confirmacoesLimpeza.clear();
        // A exclusão e o evento ficam no mesmo lock dos envios: nenhum sucesso antes do commit.
        broadcast(Evento.de(HISTORICO_LIMPO, sala.id(), sala.nome(), cliente.getUsuario().nome(), Integer.toString(removidas)));
        publicarFixadas();
    }
    private void historico(ConexaoCliente cliente, List<Mensagem> mensagens, String titulo) {
        cliente.enviar(Evento.de(HISTORICO_INICIO, titulo));
        mensagens.forEach(m -> { cliente.enviar(eventoMensagem(m)); enviarReacoes(cliente,m.id()); });
        cliente.enviar(Evento.de(HISTORICO_FIM, Integer.toString(mensagens.size())));
    }
    public synchronized long versaoHistorico() { return versaoHistorico; }
    public synchronized void publicarRespostaIA(String resposta, long versao) {
        if(versaoHistorico!=versao) throw new IllegalArgumentException("A sala teve o histórico limpo durante a consulta. A resposta da IA foi cancelada.");
        Mensagem salva=repository.salvar(Mensagem.respostaIA(resposta,LocalDateTime.now(clock)));
        broadcast(eventoMensagem(salva));
    }
    public synchronized void publicarUsuarios() {
        broadcast(new Evento(USUARIOS, usuariosOnline()));
        List<String> perfis = new ArrayList<>();
        for(var c:registry.snapshot()) { perfis.add(c.getUsuario().nome()); perfis.add(c.getUsuario().emoji().id()); }
        broadcast(new Evento(PERFIS,perfis));
    }
    public List<String> usuariosOnline() { return registry.snapshot().stream().map(c -> c.getUsuario().nome()).sorted(String.CASE_INSENSITIVE_ORDER).toList(); }
    public int quantidadeUsuarios() { return registry.quantidade(); }
    public synchronized void digitando(ConexaoCliente origem) {
        digitando(origem, true);
    }
    public synchronized void digitando(ConexaoCliente origem, boolean ativo) {
        exigirConectado(origem);
        boolean houveFalha = false;
        for (var c : registry.snapshot()) if (c != origem) {
            try { c.enviar(ativo ? Evento.de(DIGITANDO, origem.getUsuario().nome()) : Evento.de(DIGITANDO, origem.getUsuario().nome(), "stop")); }
            catch (RuntimeException e) { registry.remover(c); c.fechar(); houveFalha = true; }
        }
        if (houveFalha) publicarUsuarios();
    }
    private Evento eventoMensagem(Mensagem m) {
        return Evento.de(MENSAGEM, m.id().toString(), m.remetente().nome(), m.conteudo(), m.horario().toString(),m.remetente().emoji().id(),m.formato().name(),
                m.citacao()==null ? "" : Long.toString(m.citacao().id()), m.citacao()==null ? "" : m.citacao().autor(), m.citacao()==null ? "" : m.citacao().texto());
    }
    public synchronized void validarPerfil(ConexaoCliente cliente,Usuario usuario) {
        if(usuario.nome().equalsIgnoreCase("Conexo IA")) throw new IllegalArgumentException("Este apelido é reservado ao assistente.");
        for(var c:registry.snapshot()) if(c!=cliente && c.getUsuario().equals(usuario)) throw new IllegalArgumentException("Este apelido já está em uso nesta sala.");
    }
    public synchronized void perfilAlterado(ConexaoCliente cliente,Usuario anterior) {
        broadcast(Evento.de(PERFIL_ALTERADO,anterior.nome(),cliente.getUsuario().nome(),cliente.getUsuario().emoji().id()));
        publicarUsuarios();
    }
    public synchronized void reagir(ConexaoCliente cliente,long id,String emoji) {
        exigirConectado(cliente); exigirMensagem(id); model.Reacao.validar(emoji);
        repository.alternarReacao(id,identificador(cliente),emoji);
        for(var c:registry.snapshot()) enviarSeguro(c,eventoReacoes(c,id));
    }
    private String identificador(ConexaoCliente c) { return c.sessao().isEmpty() ? c.getUsuario().nome().toLowerCase(java.util.Locale.ROOT) : c.sessao(); }
    private Evento eventoReacoes(ConexaoCliente c,long id) {
        var campos=new ArrayList<String>(); campos.add(Long.toString(id));
        for(var r:repository.reacoes(id,identificador(c))) { campos.add(r.emoji()); campos.add(Integer.toString(r.quantidade())); campos.add(Boolean.toString(r.minha())); }
        return new Evento(REACOES,campos);
    }
    private void enviarReacoes(ConexaoCliente c,long id) { c.enviar(eventoReacoes(c,id)); }
    private Mensagem exigirMensagem(long id) { return repository.buscarPorId(id).orElseThrow(()->new IllegalArgumentException("Esta mensagem não está mais disponível nesta sala.")); }
    public synchronized void fixar(ConexaoCliente cliente,long id,boolean fixar) {
        exigirConectado(cliente); exigirMensagem(id);
        var atuais=repository.fixadas();
        if(fixar && atuais.size()>=5 && atuais.stream().noneMatch(m->m.id()==id)) throw new IllegalArgumentException("A sala permite até 5 mensagens fixadas. Remova uma antes de fixar outra.");
        repository.fixar(id,fixar); publicarFixadas();
        broadcast(Evento.de(SISTEMA,cliente.getUsuario().nome()+(fixar ? " fixou" : " desafixou")+" uma mensagem da sala."));
    }
    private Evento eventoFixadas() {
        var campos=new ArrayList<String>();
        for(var m:repository.fixadas()) campos.addAll(eventoMensagem(m).campos().subList(0,6));
        return new Evento(FIXADAS,campos);
    }
    private void enviarFixadas(ConexaoCliente c) { c.enviar(eventoFixadas()); }
    private void publicarFixadas() { broadcast(eventoFixadas()); }
    private void enviarSeguro(ConexaoCliente c,Evento evento) {
        try { c.enviar(evento); } catch(RuntimeException e) { desconectar(c); c.fechar(); }
    }
    private void exigirConectado(ConexaoCliente c) {
        if (!registry.snapshot().contains(c)) throw new IllegalArgumentException("Conecte-se novamente para enviar mensagens.");
    }
    private void broadcast(Evento evento) {
        List<ConexaoCliente> falhas = new ArrayList<>();
        for (var c : registry.snapshot()) {
            try { c.enviar(evento); } catch (RuntimeException e) { falhas.add(c); }
        }
        for (var c : falhas) { registry.remover(c); confirmacoesLimpeza.remove(c); c.fechar(); }
        if (!falhas.isEmpty()) publicarUsuarios();
    }
}
