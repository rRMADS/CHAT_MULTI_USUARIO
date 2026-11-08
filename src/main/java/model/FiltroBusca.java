package model;

import java.time.LocalDate;

public record FiltroBusca(String texto, String autor, LocalDate inicio, LocalDate fim) {
    public FiltroBusca {
        texto = texto == null ? "" : texto.strip();
        autor = autor == null ? "" : autor.strip();
        if (texto.length() > 100 || autor.length() > 30)
            throw new IllegalArgumentException(
                    "A busca aceita até 100 caracteres e um apelido de até 30.");
        if (inicio != null && fim != null && fim.isBefore(inicio))
            throw new IllegalArgumentException(
                    "A data final deve ser igual ou posterior à inicial.");
        if (inicio != null && inicio.getYear() < 1900 || fim != null && fim.getYear() > 9998)
            throw new IllegalArgumentException("Escolha datas válidas.");
    }

    public boolean corresponde(Mensagem m) {
        return m.conteudo()
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains(texto.toLowerCase(java.util.Locale.ROOT))
                && (autor.isEmpty() || m.remetente().nome().equalsIgnoreCase(autor))
                && (inicio == null || !m.horario().toLocalDate().isBefore(inicio))
                && (fim == null || !m.horario().toLocalDate().isAfter(fim));
    }
}
