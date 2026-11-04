package persistence;
public final class PersistenciaException extends RuntimeException {
    public PersistenciaException(String mensagem, Throwable causa) { super(mensagem, causa); }
}
