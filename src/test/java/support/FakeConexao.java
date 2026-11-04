package support;
import model.Usuario;
import protocol.Evento;
import service.ConexaoCliente;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
public final class FakeConexao implements ConexaoCliente {
    private final Usuario usuario;
    public final List<Evento> recebidos = new CopyOnWriteArrayList<>();
    public boolean falhar;
    public boolean fechada;
    public FakeConexao(String nome) { usuario = new Usuario(nome); }
    @Override public Usuario getUsuario() { return usuario; }
    @Override public void enviar(Evento e) { if (falhar) throw new IllegalStateException("Simulando conexão lenta"); recebidos.add(e); }
    @Override public void fechar() { fechada = true; }
    public void limpar() { recebidos.clear(); }
}
