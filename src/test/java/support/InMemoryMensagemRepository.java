package support;

import model.Mensagem;

import persistence.MensagemRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InMemoryMensagemRepository implements MensagemRepository {
    private final List<Mensagem> mensagens = new ArrayList<>();
    private long proximoId = 1;
    private final java.util.Map<Long, java.util.Map<String, java.util.Set<String>>> reacoes =
            new java.util.HashMap<>();
    private final java.util.Set<Long> fixadas = new java.util.LinkedHashSet<>();

    @Override
    public synchronized java.util.Optional<Mensagem> buscarPorId(long id) {
        return mensagens.stream().filter(m -> m.id() == id).findFirst();
    }

    @Override
    public synchronized void alternarReacao(long id, String participante, String emoji) {
        var usuarios = reacoes.computeIfAbsent(id, k -> new java.util.HashMap<>());
        var minhas = usuarios.computeIfAbsent(participante, k -> new java.util.HashSet<>());
        if (!minhas.remove(emoji)) minhas.add(emoji);
    }

    @Override
    public synchronized java.util.Map<Long, model.EstadoReacoes> carregarReacoes(List<Long> ids) {
        var resultado = new java.util.LinkedHashMap<Long, model.EstadoReacoes>();
        for (long id : ids) {
            var emojis = new java.util.HashMap<String, java.util.Set<String>>();
            reacoes.getOrDefault(id, java.util.Map.of())
                    .forEach((pessoa, opcoes) ->
                            opcoes.forEach(emoji ->
                                    emojis.computeIfAbsent(emoji, k -> new java.util.HashSet<>())
                                            .add(pessoa)));
            if (!emojis.isEmpty()) resultado.put(id, new model.EstadoReacoes(emojis));
        }
        return java.util.Map.copyOf(resultado);
    }

    @Override
    public synchronized List<Mensagem> fixadas() {
        return mensagens.stream().filter(m -> fixadas.contains(m.id())).toList();
    }

    @Override
    public synchronized void fixar(long id, boolean fixar) {
        if (fixar) fixadas.add(id);
        else fixadas.remove(id);
    }

    @Override
    public synchronized Mensagem salvar(Mensagem m) {
        var salva = m.comId(proximoId++);
        mensagens.add(salva);
        return salva;
    }

    @Override
    public synchronized int limpar() {
        int quantidade = mensagens.size();
        mensagens.clear();
        anexos.clear();
        reacoes.clear();
        fixadas.clear();
        return quantidade;
    }

    @Override
    public synchronized List<Mensagem> buscarUltimas(int n) {
        return List.copyOf(mensagens.subList(Math.max(0, mensagens.size() - n), mensagens.size()));
    }

    @Override
    public synchronized List<Mensagem> buscarPorTexto(String texto, int n) {
        var filtradas =
                mensagens.stream()
                        .filter(m ->
                                m.conteudo()
                                        .toLowerCase(Locale.ROOT)
                                        .contains(texto.toLowerCase(Locale.ROOT)))
                        .toList();
        return filtradas.subList(Math.max(0, filtradas.size() - n), filtradas.size());
    }

    @Override
    public synchronized List<Mensagem> buscar(model.FiltroBusca filtro, int quantidade) {
        var filtradas = mensagens.stream().filter(filtro::corresponde).toList();
        return filtradas.subList(Math.max(0, filtradas.size() - quantidade), filtradas.size());
    }

    private final java.util.Map<String, byte[]> anexos = new java.util.HashMap<>();

    @Override
    public synchronized Mensagem salvarAnexo(Mensagem m, byte[] dados) {
        anexos.put(m.anexo().id(), dados.clone());
        return salvar(m);
    }

    @Override
    public synchronized java.util.Optional<byte[]> lerAnexo(String id) {
        return java.util.Optional.ofNullable(anexos.get(id)).map(byte[]::clone);
    }
}
