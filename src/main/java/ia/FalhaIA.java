package ia;

/** Apenas mensagens deliberadamente públicas podem atravessar a fronteira da API. */
public final class FalhaIA extends Exception {
    @java.io.Serial private static final long serialVersionUID = 1L;

    public FalhaIA(String mensagem) {
        super(mensagem);
    }
}
