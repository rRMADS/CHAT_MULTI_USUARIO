package protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;

public final class Protocolo {
    public static final String PREFIXO_PEDIDO = "!EVENTO\t";

    private Protocolo() {}

    public static String codificar(Evento evento) {
        return evento.tipo().name()
                + evento.campos().stream()
                        .map(c ->
                                "\t"
                                        + Base64.getEncoder()
                                                .encodeToString(c.getBytes(StandardCharsets.UTF_8)))
                        .collect(Collectors.joining());
    }

    public static Evento decodificar(String linha) {
        String[] partes = linha.split("\t", -1);
        return new Evento(
                Evento.Tipo.valueOf(partes[0]),
                Arrays.stream(partes)
                        .skip(1)
                        .map(c -> new String(Base64.getDecoder().decode(c), StandardCharsets.UTF_8))
                        .toList());
    }

    public static String apresentar(model.Usuario usuario) {
        return codificar(Evento.de(Evento.Tipo.APRESENTACAO, usuario.nome(), usuario.emoji().id()));
    }

    public static String apresentar(model.Usuario usuario, String sessao, String sala) {
        String linha =
                codificar(
                        Evento.de(
                                Evento.Tipo.APRESENTACAO,
                                usuario.nome(),
                                usuario.emoji().id(),
                                sessao,
                                sala));
        identificar(linha);
        return linha;
    }

    public static model.Usuario identificar(String linha) {
        try {
            Evento e = decodificar(linha);
            if (e.tipo() != Evento.Tipo.APRESENTACAO
                    || (e.campos().size() != 2 && e.campos().size() != 4))
                throw new IllegalArgumentException();
            if (e.campos().size() == 4) {
                java.util.UUID.fromString(e.campo(2));
                if (!e.campo(3).matches("[a-z0-9_-]{1,30}")) throw new IllegalArgumentException();
            }
            return new model.Usuario(e.campo(0), e.campo(1));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Entre pelo cliente atualizado e escolha apelido e emoji de identificação.");
        }
    }
}
