package protocol;

import model.Citacao;
import model.FormatoMensagem;
import model.Mensagem;
import model.Usuario;

import java.time.LocalDateTime;

/** Centraliza os campos do evento público para que cliente e servidor não divirjam. */
public final class MensagensProtocolo {
    private MensagensProtocolo() {}

    public static Evento de(Mensagem mensagem) {
        Citacao citacao = mensagem.citacao();
        return Evento.de(
                Evento.Tipo.MENSAGEM,
                mensagem.id().toString(),
                mensagem.remetente().nome(),
                mensagem.conteudo(),
                mensagem.horario().toString(),
                mensagem.remetente().emoji().id(),
                mensagem.formato().name(),
                citacao == null ? "" : Long.toString(citacao.id()),
                citacao == null ? "" : citacao.autor(),
                citacao == null ? "" : citacao.texto());
    }

    public static Mensagem ler(Evento evento) {
        if (evento.tipo() != Evento.Tipo.MENSAGEM || evento.campos().size() < 5)
            throw new IllegalArgumentException("Evento de mensagem inválido.");
        Citacao citacao =
                evento.campos().size() > 8 && !evento.campo(6).isEmpty()
                        ? new Citacao(
                                Long.parseLong(evento.campo(6)), evento.campo(7), evento.campo(8))
                        : null;
        FormatoMensagem formato =
                evento.campos().size() > 5
                        ? FormatoMensagem.valueOf(evento.campo(5))
                        : FormatoMensagem.TEXTO;
        return new Mensagem(
                Long.valueOf(evento.campo(0)),
                new Usuario(evento.campo(1), evento.campo(4)),
                evento.campo(2),
                LocalDateTime.parse(evento.campo(3)),
                formato,
                citacao);
    }
}
