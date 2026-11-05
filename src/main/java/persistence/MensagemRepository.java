package persistence;
import model.Mensagem;
import java.util.List;
public interface MensagemRepository {
    Mensagem salvar(Mensagem mensagem);
    List<Mensagem> buscarUltimas(int quantidade);
    List<Mensagem> buscarPorTexto(String texto, int quantidade);
    /** Remove somente as mensagens públicas da sala associada ao repository. */
    int limpar();
}
