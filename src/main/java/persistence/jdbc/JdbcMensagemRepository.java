package persistence.jdbc;

import model.Citacao;
import model.EstadoReacoes;
import model.Mensagem;
import model.Reacao;
import model.Sala;
import model.Usuario;

import persistence.MensagemRepository;
import persistence.PersistenciaException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JdbcMensagemRepository implements MensagemRepository {
    private static final String SELECAO =
            """
            SELECT m.*, original.id AS citacao_id,
                   original.remetente AS citacao_autor,
                   original.conteudo AS citacao_texto
            FROM mensagem m
            LEFT JOIN mensagem original
                ON original.id = m.resposta_id AND original.sala = m.sala
            """;
    private final ConnectionFactory factory;
    private final Sala sala;

    public JdbcMensagemRepository(ConnectionFactory factory) {
        this(factory, Sala.GERAL);
    }

    public JdbcMensagemRepository(ConnectionFactory factory, Sala sala) {
        this.factory = factory;
        this.sala = sala;
    }

    public void inicializar() {
        try (var input = getClass().getResourceAsStream("/schema.sql");
                var c = factory.abrir()) {
            if (input == null) throw new IOException("schema.sql não encontrado");
            var script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (var statement = c.createStatement()) {
                for (String sql : script.split(";")) if (!sql.isBlank()) statement.execute(sql);
            }
        } catch (SQLException | IOException e) {
            throw new PersistenciaException("Não foi possível preparar o histórico.", e);
        }
    }

    @Override
    public Mensagem salvar(Mensagem mensagem) {
        try (var c = factory.abrir();
                var s =
                        c.prepareStatement(
                                "INSERT INTO mensagem(remetente, conteudo, horario, sala, emoji, formato, resposta_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                                Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, mensagem.remetente().nome());
            s.setString(2, mensagem.conteudo());
            s.setTimestamp(3, Timestamp.valueOf(mensagem.horario()));
            s.setString(4, sala.id());
            s.setString(5, mensagem.remetente().emoji().id());
            s.setString(6, mensagem.formato().name());
            if (mensagem.citacao() == null) s.setNull(7, Types.BIGINT);
            else s.setLong(7, mensagem.citacao().id());
            s.executeUpdate();
            try (var keys = s.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("ID não retornado");
                return mensagem.comId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Mensagem não salva. Tente novamente.", e);
        }
    }

    @Override
    public List<Mensagem> buscarUltimas(int quantidade) {
        return consultar(null, quantidade);
    }

    @Override
    public List<Mensagem> buscarPorTexto(String texto, int quantidade) {
        return consultar(texto, quantidade);
    }

    @Override
    public int limpar() {
        try (var c = factory.abrir();
                var s = c.prepareStatement("DELETE FROM mensagem WHERE sala = ?")) {
            s.setString(1, sala.id());
            return s.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Não foi possível confirmar a limpeza. Recarregue o histórico antes de tentar novamente.",
                    e);
        }
    }

    private Mensagem ler(ResultSet r) throws SQLException {
        long resposta = r.getLong("citacao_id");
        Citacao citacao =
                r.wasNull()
                        ? null
                        : new Citacao(
                                resposta,
                                r.getString("citacao_autor"),
                                r.getString("citacao_texto"));
        return new Mensagem(
                r.getLong("id"),
                new Usuario(r.getString("remetente"), r.getString("emoji")),
                r.getString("conteudo"),
                r.getTimestamp("horario").toLocalDateTime(),
                model.FormatoMensagem.valueOf(r.getString("formato")),
                citacao);
    }

    @Override
    public java.util.Optional<Mensagem> buscarPorId(long id) {
        try (var c = factory.abrir();
                var q = c.prepareStatement(SELECAO + "WHERE m.id=? AND m.sala=?")) {
            q.setLong(1, id);
            q.setString(2, sala.id());
            try (var r = q.executeQuery()) {
                return r.next() ? java.util.Optional.of(ler(r)) : java.util.Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível carregar a mensagem.", e);
        }
    }

    @Override
    public void alternarReacao(long id, String participante, String emoji) {
        Reacao.validar(emoji);
        try (var c = factory.abrir()) {
            c.setAutoCommit(false);
            try {
                exigirMensagem(c, id);
                int apagadas;
                try (var q =
                        c.prepareStatement(
                                "DELETE FROM reacao WHERE mensagem_id=? AND participante=? AND emoji=?")) {
                    q.setLong(1, id);
                    q.setString(2, participante);
                    q.setString(3, emoji);
                    apagadas = q.executeUpdate();
                }
                if (apagadas == 0)
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO reacao(mensagem_id,participante,emoji) VALUES(?,?,?)")) {
                        q.setLong(1, id);
                        q.setString(2, participante);
                        q.setString(3, emoji);
                        q.executeUpdate();
                    }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível salvar a reação.", e);
        }
    }

    private void exigirMensagem(Connection c, long id) throws SQLException {
        try (var q = c.prepareStatement("SELECT id FROM mensagem WHERE id=? AND sala=?")) {
            q.setLong(1, id);
            q.setString(2, sala.id());
            try (var r = q.executeQuery()) {
                if (!r.next())
                    throw new IllegalArgumentException(
                            "Esta mensagem não está mais disponível nesta sala.");
            }
        }
    }

    @Override
    public Map<Long, EstadoReacoes> carregarReacoes(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        if (ids.size() > 200)
            throw new IllegalArgumentException("Carregue no máximo 200 mensagens por vez.");
        String parametros = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                "SELECT r.mensagem_id,r.emoji,r.participante FROM reacao r JOIN mensagem m ON m.id=r.mensagem_id WHERE m.sala=? AND r.mensagem_id IN ("
                        + parametros
                        + ")";
        try (var c = factory.abrir();
                var q = c.prepareStatement(sql)) {
            q.setString(1, sala.id());
            for (int i = 0; i < ids.size(); i++) q.setLong(i + 2, ids.get(i));
            try (var r = q.executeQuery()) {
                Map<Long, Map<String, Set<String>>> grupos = new LinkedHashMap<>();
                while (r.next())
                    grupos.computeIfAbsent(r.getLong(1), k -> new LinkedHashMap<>())
                            .computeIfAbsent(r.getString(2), k -> new HashSet<>())
                            .add(r.getString(3));
                Map<Long, EstadoReacoes> resultado = new LinkedHashMap<>();
                grupos.forEach((id, emojis) -> resultado.put(id, new EstadoReacoes(emojis)));
                return Map.copyOf(resultado);
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível carregar as reações.", e);
        }
    }

    @Override
    public List<Mensagem> fixadas() {
        try (var c = factory.abrir();
                var q =
                        c.prepareStatement(
                                SELECAO
                                        + "WHERE m.sala=? AND m.fixada=TRUE ORDER BY m.id DESC LIMIT 5")) {
            q.setString(1, sala.id());
            try (var r = q.executeQuery()) {
                var lista = new ArrayList<Mensagem>();
                while (r.next()) lista.add(ler(r));
                return List.copyOf(lista);
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível carregar as mensagens fixadas.", e);
        }
    }

    @Override
    public void fixar(long id, boolean fixada) {
        try (var c = factory.abrir();
                var q = c.prepareStatement("UPDATE mensagem SET fixada=? WHERE id=? AND sala=?")) {
            q.setBoolean(1, fixada);
            q.setLong(2, id);
            q.setString(3, sala.id());
            if (q.executeUpdate() == 0)
                throw new IllegalArgumentException(
                        "Esta mensagem não está mais disponível nesta sala.");
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível atualizar a mensagem fixada.", e);
        }
    }

    private List<Mensagem> consultar(String texto, int quantidade) {
        if (quantidade < 1 || quantidade > 200)
            throw new IllegalArgumentException("Histórico: escolha de 1 a 200 mensagens.");
        // LOCATE trata %, _ e aspas como texto literal, sem interpolação de SQL.
        String sql =
                texto == null
                        ? SELECAO + "WHERE m.sala = ? ORDER BY m.id DESC LIMIT ?"
                        : SELECAO
                                + "WHERE m.sala = ? AND LOCATE(LOWER(?), LOWER(m.conteudo)) > 0 ORDER BY m.id DESC LIMIT ?";
        try (var c = factory.abrir();
                var s = c.prepareStatement(sql)) {
            s.setString(1, sala.id());
            int index = 2;
            if (texto != null) s.setString(index++, texto);
            s.setInt(index, quantidade);
            try (var rows = s.executeQuery()) {
                List<Mensagem> mensagens = new ArrayList<>();
                while (rows.next()) mensagens.add(ler(rows));
                Collections.reverse(mensagens);
                return List.copyOf(mensagens);
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível carregar o histórico.", e);
        }
    }
}
