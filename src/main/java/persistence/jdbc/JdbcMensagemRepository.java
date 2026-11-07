package persistence.jdbc;

import model.Mensagem;
import model.Usuario;
import model.Sala;
import model.Citacao;
import model.Reacao;
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
                "INSERT INTO mensagem(remetente, conteudo, horario, sala, emoji, formato, resposta_id) VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, mensagem.remetente().nome()); s.setString(2, mensagem.conteudo());
            s.setTimestamp(3, Timestamp.valueOf(mensagem.horario())); s.setString(4, sala.id()); s.setString(5,mensagem.remetente().emoji().id()); s.setString(6,mensagem.formato().name());
            if(mensagem.citacao()==null) s.setNull(7,Types.BIGINT); else s.setLong(7,mensagem.citacao().id()); s.executeUpdate();
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
    private Mensagem ler(ResultSet r,Connection c) throws SQLException {
        Citacao citacao=null; long resposta=r.getLong("resposta_id");
        if(!r.wasNull()) try(var q=c.prepareStatement("SELECT remetente,conteudo FROM mensagem WHERE id=? AND sala=?")) {
            q.setLong(1,resposta); q.setString(2,sala.id());
            try(var original=q.executeQuery()) { if(original.next()) citacao=new Citacao(resposta,original.getString(1),original.getString(2)); }
        }
        return new Mensagem(r.getLong("id"),new Usuario(r.getString("remetente"),r.getString("emoji")),r.getString("conteudo"),
                r.getTimestamp("horario").toLocalDateTime(),model.FormatoMensagem.valueOf(r.getString("formato")),citacao);
    }
    @Override public java.util.Optional<Mensagem> buscarPorId(long id) {
        try(var c=factory.abrir(); var q=c.prepareStatement("SELECT * FROM mensagem WHERE id=? AND sala=?")) {
            q.setLong(1,id); q.setString(2,sala.id());
            try(var r=q.executeQuery()) { return r.next() ? java.util.Optional.of(ler(r,c)) : java.util.Optional.empty(); }
        } catch(SQLException e) { throw new PersistenciaException("Não foi possível carregar a mensagem.",e); }
    }
    @Override public void alternarReacao(long id,String participante,String emoji) {
        Reacao.validar(emoji);
        try(var c=factory.abrir()) {
            c.setAutoCommit(false);
            try {
                exigirMensagem(c,id);
                int apagadas;
                try(var q=c.prepareStatement("DELETE FROM reacao WHERE mensagem_id=? AND participante=? AND emoji=?")) {
                    q.setLong(1,id); q.setString(2,participante); q.setString(3,emoji); apagadas=q.executeUpdate();
                }
                if(apagadas==0) try(var q=c.prepareStatement("INSERT INTO reacao(mensagem_id,participante,emoji) VALUES(?,?,?)")) {
                    q.setLong(1,id); q.setString(2,participante); q.setString(3,emoji); q.executeUpdate();
                }
                c.commit();
            } catch(SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch(SQLException e) { throw new PersistenciaException("Não foi possível salvar a reação.",e); }
    }
    private void exigirMensagem(Connection c,long id) throws SQLException {
        try(var q=c.prepareStatement("SELECT id FROM mensagem WHERE id=? AND sala=?")) {
            q.setLong(1,id); q.setString(2,sala.id());
            try(var r=q.executeQuery()) { if(!r.next()) throw new IllegalArgumentException("Esta mensagem não está mais disponível nesta sala."); }
        }
    }
    @Override public List<Reacao> reacoes(long id,String participante) {
        String sql="SELECT r.emoji,COUNT(*) AS total,MAX(CASE WHEN r.participante=? THEN 1 ELSE 0 END) AS minha FROM reacao r JOIN mensagem m ON m.id=r.mensagem_id WHERE m.id=? AND m.sala=? GROUP BY r.emoji ORDER BY r.emoji";
        try(var c=factory.abrir(); var q=c.prepareStatement(sql)) {
            q.setString(1,participante); q.setLong(2,id); q.setString(3,sala.id());
            try(var r=q.executeQuery()) { var lista=new ArrayList<Reacao>(); while(r.next()) lista.add(new Reacao(r.getString(1),r.getInt(2),r.getInt(3)==1)); return List.copyOf(lista); }
        } catch(SQLException e) { throw new PersistenciaException("Não foi possível carregar as reações.",e); }
    }
    @Override public List<Mensagem> fixadas() {
        try(var c=factory.abrir(); var q=c.prepareStatement("SELECT * FROM mensagem WHERE sala=? AND fixada=TRUE ORDER BY id DESC LIMIT 5")) {
            q.setString(1,sala.id());
            try(var r=q.executeQuery()) { var lista=new ArrayList<Mensagem>(); while(r.next()) lista.add(ler(r,c)); return List.copyOf(lista); }
        } catch(SQLException e) { throw new PersistenciaException("Não foi possível carregar as mensagens fixadas.",e); }
    }
    @Override public void fixar(long id,boolean fixada) {
        try(var c=factory.abrir(); var q=c.prepareStatement("UPDATE mensagem SET fixada=? WHERE id=? AND sala=?")) {
            q.setBoolean(1,fixada); q.setLong(2,id); q.setString(3,sala.id());
            if(q.executeUpdate()==0) throw new IllegalArgumentException("Esta mensagem não está mais disponível nesta sala.");
        } catch(SQLException e) { throw new PersistenciaException("Não foi possível atualizar a mensagem fixada.",e); }
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
                while (rows.next()) mensagens.add(ler(rows,c));
                Collections.reverse(mensagens);
                return List.copyOf(mensagens);
            }
        } catch (SQLException e) { throw new PersistenciaException("Não foi possível carregar o histórico.", e); }
    }
}
