package support;
import model.Mensagem;
import persistence.MensagemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
public final class InMemoryMensagemRepository implements MensagemRepository {
    private final List<Mensagem> mensagens = new ArrayList<>();
    private long proximoId = 1;
    @Override public synchronized Mensagem salvar(Mensagem m) { var salva = m.comId(proximoId++); mensagens.add(salva); return salva; }
    @Override public synchronized int limpar() { int quantidade = mensagens.size(); mensagens.clear(); return quantidade; }
    @Override public synchronized List<Mensagem> buscarUltimas(int n) { return List.copyOf(mensagens.subList(Math.max(0, mensagens.size() - n), mensagens.size())); }
    @Override public synchronized List<Mensagem> buscarPorTexto(String texto, int n) {
        var filtradas = mensagens.stream().filter(m -> m.conteudo().toLowerCase(Locale.ROOT).contains(texto.toLowerCase(Locale.ROOT))).toList();
        return filtradas.subList(Math.max(0, filtradas.size() - n), filtradas.size());
    }
}
