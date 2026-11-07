package support;
import model.Mensagem;
import persistence.MensagemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
public final class InMemoryMensagemRepository implements MensagemRepository {
    private final List<Mensagem> mensagens = new ArrayList<>();
    private long proximoId = 1;
    private final java.util.Map<Long,java.util.Map<String,java.util.Set<String>>> reacoes=new java.util.HashMap<>();
    private final java.util.Set<Long> fixadas=new java.util.LinkedHashSet<>();
    @Override public synchronized java.util.Optional<Mensagem> buscarPorId(long id) { return mensagens.stream().filter(m->m.id()==id).findFirst(); }
    @Override public synchronized void alternarReacao(long id,String participante,String emoji) {
        var usuarios=reacoes.computeIfAbsent(id,k->new java.util.HashMap<>());
        var minhas=usuarios.computeIfAbsent(participante,k->new java.util.HashSet<>());
        if(!minhas.remove(emoji)) minhas.add(emoji);
    }
    @Override public synchronized List<model.Reacao> reacoes(long id,String participante) {
        var usuarios=reacoes.getOrDefault(id,java.util.Map.of()); var resultado=new ArrayList<model.Reacao>();
        for(String emoji:model.Reacao.PERMITIDAS) {
            int n=(int)usuarios.values().stream().filter(r->r.contains(emoji)).count();
            if(n>0) resultado.add(new model.Reacao(emoji,n,usuarios.getOrDefault(participante,java.util.Set.of()).contains(emoji)));
        }
        return resultado;
    }
    @Override public synchronized List<Mensagem> fixadas() { return mensagens.stream().filter(m->fixadas.contains(m.id())).toList(); }
    @Override public synchronized void fixar(long id,boolean fixar) { if(fixar) fixadas.add(id); else fixadas.remove(id); }
    @Override public synchronized Mensagem salvar(Mensagem m) { var salva = m.comId(proximoId++); mensagens.add(salva); return salva; }
    @Override public synchronized int limpar() { int quantidade = mensagens.size(); mensagens.clear(); reacoes.clear(); fixadas.clear(); return quantidade; }
    @Override public synchronized List<Mensagem> buscarUltimas(int n) { return List.copyOf(mensagens.subList(Math.max(0, mensagens.size() - n), mensagens.size())); }
    @Override public synchronized List<Mensagem> buscarPorTexto(String texto, int n) {
        var filtradas = mensagens.stream().filter(m -> m.conteudo().toLowerCase(Locale.ROOT).contains(texto.toLowerCase(Locale.ROOT))).toList();
        return filtradas.subList(Math.max(0, filtradas.size() - n), filtradas.size());
    }
}
