package persistence;
import model.Mensagem;
import java.util.List;
public interface MensagemRepository {
    Mensagem salvar(Mensagem mensagem);
    List<Mensagem> buscarUltimas(int quantidade);
    List<Mensagem> buscarPorTexto(String texto, int quantidade);
}
