package client;

import model.Citacao;

import java.util.*;

/** Estado da sessão local; nenhum rascunho é transmitido ao servidor. */
final class EstadoSalas {
    record Rascunho(String texto, String destino, Citacao citacao, int cursor) {}

    private final Map<String, Rascunho> rascunhos = new HashMap<>();
    private final Map<String, NavigableSet<Long>> naoLidas = new HashMap<>();
    private final Map<String, Long> lidas = new HashMap<>();

    void guardar(String sala, Rascunho rascunho) {
        rascunhos.put(sala, rascunho);
    }

    Rascunho rascunho(String sala) {
        return rascunhos.getOrDefault(sala, new Rascunho("", "Todos", null, 0));
    }

    void nova(String sala, long id) {
        if (id > lidas.getOrDefault(sala, 0L))
            naoLidas.computeIfAbsent(sala, k -> new TreeSet<>()).add(id);
    }

    int quantidade(String sala) {
        return naoLidas.getOrDefault(sala, Collections.emptyNavigableSet()).size();
    }

    Long primeira(String sala) {
        var ids = naoLidas.get(sala);
        return ids == null || ids.isEmpty() ? null : ids.first();
    }

    void lerAte(String sala, long id) {
        lidas.merge(sala, id, Math::max);
        var ids = naoLidas.get(sala);
        if (ids != null) ids.headSet(id, true).clear();
    }

    boolean contarNoHistorico(String sala, long id) {
        Long primeira = primeira(sala);
        return lidas.containsKey(sala) && id > lidas.get(sala)
                || primeira != null && id >= primeira;
    }

    void limparSala(String sala) {
        naoLidas.remove(sala);
        rascunhos.computeIfPresent(
                sala, (s, r) -> new Rascunho(r.texto(), r.destino(), null, r.cursor()));
    }

    int total() {
        return naoLidas.values().stream().mapToInt(Set::size).sum();
    }

    void limpar() {
        rascunhos.clear();
        naoLidas.clear();
        lidas.clear();
    }
}
