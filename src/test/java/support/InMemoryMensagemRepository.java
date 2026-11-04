package support;
import model.Mensagem;
import persistence.MensagemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
public final class InMemoryMensagemRepository implements MensagemRepository {
    private final List<Mensagem> mensagens = new ArrayList<>();
    @Override public synchronized Mensagem salvar(Mensagem m) { var salva = m.comId((long)mensagens.size() + 1); mensagens.add(salva); return salva; }
    @Override public synchronized List<Mensagem> buscarUltimas(int n) { return List.copyOf(mensagens.subList(Math.max(0, mensagens.size() - n), mensagens.size())); }
    @Override public synchronized List<Mensagem> buscarPorTexto(String texto, int n) {
        var filtradas = mensagens.stream().filter(m -> m.conteudo().toLowerCase(Locale.ROOT).contains(texto.toLowerCase(Locale.ROOT))).toList();
        return filtradas.subList(Math.max(0, filtradas.size() - n), filtradas.size());
    }
}
