package persistence;
import model.Mensagem;
import java.util.List;
public interface MensagemRepository {
    Mensagem salvar(Mensagem mensagem);
    List<Mensagem> buscarUltimas(int quantidade);
    List<Mensagem> buscarPorTexto(String texto, int quantidade);
    default java.util.Optional<Mensagem> buscarPorId(long id) { return buscarUltimas(200).stream().filter(m->m.id()==id).findFirst(); }
    default List<model.Reacao> reacoes(long id,String participante) { return List.of(); }
    default void alternarReacao(long id,String participante,String emoji) { throw new UnsupportedOperationException(); }
    default List<Mensagem> fixadas() { return List.of(); }
    default void fixar(long id,boolean fixada) { throw new UnsupportedOperationException(); }
    /** Remove somente as mensagens públicas da sala associada ao repository. */
    int limpar();
}
