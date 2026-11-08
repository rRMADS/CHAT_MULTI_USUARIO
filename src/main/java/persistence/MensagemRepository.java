package persistence;

import model.Mensagem;

import java.util.List;

public interface MensagemRepository {
    Mensagem salvar(Mensagem mensagem);

    Mensagem salvarAnexo(Mensagem mensagem, byte[] dados);

    java.util.Optional<byte[]> lerAnexo(String id);

    List<Mensagem> buscarUltimas(int quantidade);

    List<Mensagem> buscarPorTexto(String texto, int quantidade);

    List<Mensagem> buscar(model.FiltroBusca filtro, int quantidade);

    java.util.Optional<Mensagem> buscarPorId(long id);

    java.util.Map<Long, model.EstadoReacoes> carregarReacoes(List<Long> ids);

    default List<model.Reacao> reacoes(long id, String participante) {
        return carregarReacoes(List.of(id))
                .getOrDefault(id, model.EstadoReacoes.VAZIO)
                .para(participante);
    }

    void alternarReacao(long id, String participante, String emoji);

    List<Mensagem> fixadas();

    void fixar(long id, boolean fixada);

    /** Remove somente as mensagens públicas da sala associada ao repository. */
    int limpar();
}
