package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public record Mensagem(Long id, Usuario remetente, String conteudo, LocalDateTime horario) {
    public Mensagem {
        Objects.requireNonNull(remetente, "Remetente obrigatório");
        Objects.requireNonNull(horario, "Horário obrigatório");
        if (conteudo == null || conteudo.isBlank() || conteudo.strip().length() > 500
                || conteudo.chars().anyMatch(c -> Character.isISOControl(c))) {
            throw new MensagemInvalidaException("Escreva uma mensagem de 1 a 500 caracteres, em uma linha.");
        }
        conteudo = conteudo.strip();
        if (id != null && id <= 0) throw new IllegalArgumentException("ID inválido");
    }
    public Mensagem comId(Long novoId) { return new Mensagem(novoId, remetente, conteudo, horario); }
    public String formatar() {
        return "[" + horario.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) + "] " + remetente.nome() + ": " + conteudo;
    }
}
