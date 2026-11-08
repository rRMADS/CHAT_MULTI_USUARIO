package model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Snapshot imutável: a mesma leitura atende todos os destinatários da sala. */
public record EstadoReacoes(Map<String, Set<String>> participantes) {
    public static final EstadoReacoes VAZIO = new EstadoReacoes(Map.of());

    public EstadoReacoes {
        var copia = new LinkedHashMap<String, Set<String>>();
        participantes.forEach((emoji, pessoas) -> {
            Reacao.validar(emoji);
            if (!pessoas.isEmpty()) copia.put(emoji, Set.copyOf(pessoas));
        });
        participantes = Map.copyOf(copia);
    }

    public List<Reacao> para(String participante) {
        return Reacao.PERMITIDAS.stream()
                .filter(participantes::containsKey)
                .map(emoji -> {
                    var pessoas = participantes.get(emoji);
                    return new Reacao(emoji, pessoas.size(), pessoas.contains(participante));
                })
                .toList();
    }
}
