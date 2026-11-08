package model;

public final class MensagemInvalidaException extends IllegalArgumentException {
    @java.io.Serial private static final long serialVersionUID = 1L;

    public MensagemInvalidaException(String mensagem) {
        super(mensagem);
    }
}
