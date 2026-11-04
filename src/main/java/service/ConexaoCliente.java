package service;
import model.Usuario;
import protocol.Evento;
public interface ConexaoCliente {
    Usuario getUsuario();
    void enviar(Evento evento);
    void fechar();
}
