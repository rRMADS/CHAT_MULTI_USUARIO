package ia;

/** Apenas mensagens deliberadamente públicas podem atravessar a fronteira da API. */
public final class FalhaIA extends Exception {
    public FalhaIA(String mensagem) { super(mensagem); }
}
