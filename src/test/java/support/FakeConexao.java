package support;
import model.Usuario;
import protocol.Evento;
import service.ConexaoCliente;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
public final class FakeConexao implements ConexaoCliente {
    private Usuario usuario;
    public final List<Evento> recebidos = new CopyOnWriteArrayList<>();
    private final String sessao=java.util.UUID.randomUUID().toString();
    @Override public String sessao() { return sessao; }
    public boolean falhar;
    public boolean fechada;
    public FakeConexao(String nome) { this(nome,"smile"); }
    public FakeConexao(String nome,String emoji) { usuario = new Usuario(nome,emoji); }
    @Override public Usuario getUsuario() { return usuario; }
    @Override public void atualizarUsuario(Usuario usuario) { this.usuario=usuario; }
    @Override public void enviar(Evento e) { if (falhar) throw new IllegalStateException("Simulando conexão lenta"); recebidos.add(e); }
    @Override public void fechar() { fechada = true; }
    public void limpar() { recebidos.clear(); }
}
