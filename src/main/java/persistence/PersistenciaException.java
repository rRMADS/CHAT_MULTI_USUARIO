package persistence;

public final class PersistenciaException extends RuntimeException {
    @java.io.Serial private static final long serialVersionUID = 1L;

    public PersistenciaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
