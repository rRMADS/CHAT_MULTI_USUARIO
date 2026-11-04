package protocol;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;
public final class Protocolo {
    private Protocolo() {}
    public static String codificar(Evento evento) {
        return evento.tipo().name() + evento.campos().stream().map(c -> "\t" + Base64.getEncoder()
                .encodeToString(c.getBytes(StandardCharsets.UTF_8))).collect(Collectors.joining());
    }
    public static Evento decodificar(String linha) {
        String[] partes = linha.split("\t", -1);
        return new Evento(Evento.Tipo.valueOf(partes[0]), Arrays.stream(partes).skip(1)
                .map(c -> new String(Base64.getDecoder().decode(c), StandardCharsets.UTF_8)).toList());
    }
}
