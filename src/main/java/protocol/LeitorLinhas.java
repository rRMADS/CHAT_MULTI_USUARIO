package protocol;

import java.io.IOException;
import java.io.Reader;

/** Limites distintos para requisições curtas e respostas com histórico/Markdown em Base64. */
public final class LeitorLinhas {
    public static final int LIMITE_APRESENTACAO = 384;
    public static final int LIMITE_REQUISICAO = 2048;
    public static final int LIMITE_EVENTO = 262144;

    private LeitorLinhas() {}

    public static String ler(Reader leitor, int limite) throws IOException {
        StringBuilder texto = new StringBuilder();
        int c;
        while ((c = leitor.read()) != -1) {
            if (c == '\n') return texto.toString();
            if (c == '\r') continue;
            if (texto.length() >= limite)
                throw new IOException("Linha excede o limite do protocolo.");
            texto.append((char) c);
        }
        return texto.isEmpty() ? null : texto.toString();
    }
}
