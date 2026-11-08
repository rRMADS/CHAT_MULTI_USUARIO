package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public record Mensagem(
        Long id,
        Usuario remetente,
        String conteudo,
        LocalDateTime horario,
        FormatoMensagem formato,
        Citacao citacao,
        Anexo anexo) {
    public Mensagem(
            Long id,
            Usuario remetente,
            String conteudo,
            LocalDateTime horario,
            FormatoMensagem formato,
            Citacao citacao) {
        this(id, remetente, conteudo, horario, formato, citacao, null);
    }

    public Mensagem(
            Long id,
            Usuario remetente,
            String conteudo,
            LocalDateTime horario,
            FormatoMensagem formato) {
        this(id, remetente, conteudo, horario, formato, null);
    }

    public Mensagem(Long id, Usuario remetente, String conteudo, LocalDateTime horario) {
        this(id, remetente, conteudo, horario, FormatoMensagem.TEXTO);
    }

    public Mensagem {
        Objects.requireNonNull(remetente, "Remetente obrigatório");
        Objects.requireNonNull(horario, "Horário obrigatório");
        Objects.requireNonNull(formato, "Formato obrigatório");
        boolean ia = formato == FormatoMensagem.MARKDOWN_IA;
        if (ia
                && (!Marca.assistente(remetente.nome())
                        || remetente.emoji() != EmojiIdentificacao.ROBOT))
            throw new MensagemInvalidaException("Formato reservado às respostas do assistente.");
        if (ia && conteudo != null) conteudo = conteudo.replace("\r\n", "\n").replace('\r', '\n');
        if (conteudo == null
                || conteudo.isBlank()
                || conteudo.strip().length() > (ia ? 8000 : 500)
                || conteudo.chars()
                        .anyMatch(c ->
                                Character.isISOControl(c) && !(ia && (c == '\n' || c == '\t')))) {
            if (ia)
                throw new MensagemInvalidaException(
                        "A resposta da IA deve ter de 1 a 8000 caracteres válidos.");
            throw new MensagemInvalidaException(
                    "Escreva uma mensagem de 1 a 500 caracteres, em uma linha.");
        }
        conteudo = conteudo.strip();
        if (id != null && id <= 0) throw new IllegalArgumentException("ID inválido");
    }

    public static Mensagem respostaIA(String conteudo, LocalDateTime horario) {
        return new Mensagem(
                null,
                new Usuario("Orkut IA", "robot"),
                conteudo,
                horario,
                FormatoMensagem.MARKDOWN_IA);
    }

    public Mensagem comId(Long novoId) {
        return new Mensagem(novoId, remetente, conteudo, horario, formato, citacao, anexo);
    }

    public String formatar() {
        return "["
                + horario.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                + "] "
                + remetente.nome()
                + ": "
                + conteudo;
    }
}
