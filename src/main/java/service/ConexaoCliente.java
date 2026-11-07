package service;
import model.Usuario;
import protocol.Evento;
public interface ConexaoCliente {
    Usuario getUsuario();
    default void atualizarUsuario(Usuario usuario) { throw new UnsupportedOperationException(); }
    default String sessao() { return ""; }
    default String salaDesejada() { return "geral"; }
    void enviar(Evento evento);
    void fechar();
}
