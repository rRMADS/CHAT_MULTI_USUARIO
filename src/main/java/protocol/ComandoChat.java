package protocol;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Interpretação compartilhada para o campo do cliente e o roteamento do servidor. */
public record ComandoChat(String nome, String argumentos) {
    private static final Map<String, String> ALIASES =
            Map.of(
                    "/",
                    "/help",
                    "/ajuda",
                    "/help",
                    "/usuarios",
                    "/users",
                    "/historico",
                    "/history",
                    "/sair",
                    "/quit",
                    "/sala",
                    "/join",
                    "/rooms",
                    "/salas",
                    "/clear",
                    "/limpar",
                    "/limparhistorico",
                    "/limpar");

    public static Optional<ComandoChat> ler(String linha) {
        String limpa = aparar(linha);
        if (!limpa.startsWith("/")) return Optional.empty();
        String[] partes = limpa.split("[\\s\\p{Z}]+", 2);
        String nome = partes[0].toLowerCase(Locale.ROOT);
        return Optional.of(
                new ComandoChat(
                        ALIASES.getOrDefault(nome, nome),
                        partes.length == 2 ? aparar(partes[1]) : ""));
    }

    public static String aparar(String texto) {
        return texto.replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "");
    }

    public String paraLinha() {
        return nome + (argumentos.isEmpty() ? "" : " " + argumentos);
    }
}
