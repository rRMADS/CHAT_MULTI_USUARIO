package persistence.jdbc;

import model.Mensagem;
import model.Usuario;
import model.Sala;
import persistence.MensagemRepository;
import persistence.PersistenciaException;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JdbcMensagemRepository implements MensagemRepository {
    private final ConnectionFactory factory;
    private final Sala sala;
    public JdbcMensagemRepository(ConnectionFactory factory) { this(factory, Sala.GERAL); }
    public JdbcMensagemRepository(ConnectionFactory factory, Sala sala) { this.factory = factory; this.sala = sala; }
    public void inicializar() {
        try (var input = getClass().getResourceAsStream("/schema.sql"); var c = factory.abrir()) {
            if (input == null) throw new IOException("schema.sql não encontrado");
            var script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (var statement = c.createStatement()) {
                for (String sql : script.split(";")) if (!sql.isBlank()) statement.execute(sql);
            }
        } catch (SQLException | IOException e) { throw new PersistenciaException("Não foi possível preparar o histórico.", e); }
    }
    @Override public Mensagem salvar(Mensagem mensagem) {
        try (var c = factory.abrir(); var s = c.prepareStatement(
                "INSERT INTO mensagem(remetente, conteudo, horario, sala, emoji) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, mensagem.remetente().nome()); s.setString(2, mensagem.conteudo());
            s.setTimestamp(3, Timestamp.valueOf(mensagem.horario())); s.setString(4, sala.id()); s.setString(5,mensagem.remetente().emoji().id()); s.executeUpdate();
            try (var keys = s.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("ID não retornado");
                return mensagem.comId(keys.getLong(1));
            }
        } catch (SQLException e) { throw new PersistenciaException("Mensagem não salva. Tente novamente.", e); }
    }
    @Override public List<Mensagem> buscarUltimas(int quantidade) { return consultar(null, quantidade); }
    @Override public List<Mensagem> buscarPorTexto(String texto, int quantidade) { return consultar(texto, quantidade); }
    @Override public int limpar() {
        try (var c = factory.abrir(); var s = c.prepareStatement("DELETE FROM mensagem WHERE sala = ?")) {
            s.setString(1, sala.id()); return s.executeUpdate();
        } catch (SQLException e) { throw new PersistenciaException("Não foi possível confirmar a limpeza. Recarregue o histórico antes de tentar novamente.", e); }
    }
    private List<Mensagem> consultar(String texto, int quantidade) {
        if (quantidade < 1 || quantidade > 200) throw new IllegalArgumentException("Histórico: escolha de 1 a 200 mensagens.");
        // LOCATE trata %, _ e aspas como texto literal, sem interpolação de SQL.
        String sql = texto == null ? "SELECT * FROM mensagem WHERE sala = ? ORDER BY id DESC LIMIT ?"
                : "SELECT * FROM mensagem WHERE sala = ? AND LOCATE(LOWER(?), LOWER(conteudo)) > 0 ORDER BY id DESC LIMIT ?";
        try (var c = factory.abrir(); var s = c.prepareStatement(sql)) {
            s.setString(1, sala.id()); int index = 2;
            if (texto != null) s.setString(index++, texto);
            s.setInt(index, quantidade);
            try (var rows = s.executeQuery()) {
                List<Mensagem> mensagens = new ArrayList<>();
                while (rows.next()) mensagens.add(new Mensagem(rows.getLong("id"), new Usuario(rows.getString("remetente"),rows.getString("emoji")),
                        rows.getString("conteudo"), rows.getTimestamp("horario").toLocalDateTime()));
                Collections.reverse(mensagens);
                return List.copyOf(mensagens);
            }
        } catch (SQLException e) { throw new PersistenciaException("Não foi possível carregar o histórico.", e); }
    }
}
