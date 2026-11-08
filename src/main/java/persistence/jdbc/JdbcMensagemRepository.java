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
                   original.conteudo AS citacao_texto,
                   a.nome AS anexo_nome, a.tamanho AS anexo_tamanho, a.tipo AS anexo_tipo
            FROM mensagem m
            LEFT JOIN mensagem original
                ON original.id = m.resposta_id AND original.sala = m.sala
            LEFT JOIN anexo a ON a.id=m.anexo_id AND a.sala=m.sala
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
    public Mensagem salvar(Mensagem m) {
        if (m.anexo() != null) throw new IllegalArgumentException("Use o envio de anexos.");
        return salvarInterno(m, null);
    }

    @Override
    public Mensagem salvarAnexo(Mensagem m, byte[] dados) {
        if (m.anexo() == null || dados == null || dados.length != m.anexo().tamanho())
            throw new IllegalArgumentException("Arquivo incompleto.");
        return salvarInterno(m, dados);
    }

    private Mensagem salvarInterno(Mensagem m, byte[] dados) {
        try (var c = factory.abrir()) {
            c.setAutoCommit(false);
            try {
                if (dados != null)
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO anexo(id,sala,nome,tamanho,tipo,dados) VALUES(?,?,?,?,?,?)")) {
                        q.setString(1, m.anexo().id());
                        q.setString(2, sala.id());
                        q.setString(3, m.anexo().nome());
                        q.setLong(4, dados.length);
                        q.setString(5, m.anexo().tipo());
                        q.setBytes(6, dados);
                        q.executeUpdate();
                    }
                Mensagem salva;
                try (var q =
                        c.prepareStatement(
                                "INSERT INTO mensagem(remetente,conteudo,horario,sala,emoji,formato,resposta_id,anexo_id) VALUES(?,?,?,?,?,?,?,?)",
                                Statement.RETURN_GENERATED_KEYS)) {
                    q.setString(1, m.remetente().nome());
                    q.setString(2, m.conteudo());
                    q.setTimestamp(3, Timestamp.valueOf(m.horario()));
                    q.setString(4, sala.id());
                    q.setString(5, m.remetente().emoji().id());
                    q.setString(6, m.formato().name());
                    if (m.citacao() == null) q.setNull(7, Types.BIGINT);
                    else q.setLong(7, m.citacao().id());
                    q.setString(8, m.anexo() == null ? null : m.anexo().id());
                    q.executeUpdate();
                    try (var keys = q.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("ID não retornado");
                        salva = m.comId(keys.getLong(1));
                    }
                }
                c.commit();
                return salva;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Mensagem não salva. Tente novamente.", e);
        }
    }

    @Override
    public java.util.Optional<byte[]> lerAnexo(String id) {
        try (var c = factory.abrir();
                var q = c.prepareStatement("SELECT dados FROM anexo WHERE id=? AND sala=?")) {
            q.setString(1, id);
            q.setString(2, sala.id());
            try (var r = q.executeQuery()) {
                return r.next() ? java.util.Optional.of(r.getBytes(1)) : java.util.Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível carregar o arquivo.", e);
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
        try (var c = factory.abrir()) {
            c.setAutoCommit(false);
            try {
                int quantidade;
                try (var q = c.prepareStatement("DELETE FROM mensagem WHERE sala=?")) {
                    q.setString(1, sala.id());
                    quantidade = q.executeUpdate();
                }
                try (var q = c.prepareStatement("DELETE FROM anexo WHERE sala=?")) {
                    q.setString(1, sala.id());
                    q.executeUpdate();
                }
                c.commit();
                return quantidade;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível confirmar a limpeza.", e);
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
                citacao,
                r.getString("anexo_nome") == null
                        ? null
                        : new model.Anexo(
                                r.getString("anexo_id"),
                                r.getString("anexo_nome"),
                                r.getLong("anexo_tamanho"),
                                r.getString("anexo_tipo")));
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
        return buscar(new model.FiltroBusca(texto, "", null, null), quantidade);
    }

    @Override
    public List<Mensagem> buscar(model.FiltroBusca filtro, int quantidade) {
        if (quantidade < 1 || quantidade > 200)
            throw new IllegalArgumentException("Histórico: escolha de 1 a 200 mensagens.");
        String sql = SELECAO + "WHERE m.sala=?";
        if (!filtro.texto().isEmpty()) sql += " AND LOCATE(LOWER(?),LOWER(m.conteudo))>0";
        if (!filtro.autor().isEmpty()) sql += " AND LOWER(m.remetente)=LOWER(?)";
        if (filtro.inicio() != null) sql += " AND m.horario>=?";
        if (filtro.fim() != null) sql += " AND m.horario<?";
        sql += " ORDER BY m.id DESC LIMIT ?";
        try (var c = factory.abrir();
                var q = c.prepareStatement(sql)) {
            int i = 1;
            q.setString(i++, sala.id());
            if (!filtro.texto().isEmpty()) q.setString(i++, filtro.texto());
            if (!filtro.autor().isEmpty()) q.setString(i++, filtro.autor());
            if (filtro.inicio() != null)
                q.setTimestamp(i++, Timestamp.valueOf(filtro.inicio().atStartOfDay()));
            if (filtro.fim() != null)
                q.setTimestamp(i++, Timestamp.valueOf(filtro.fim().plusDays(1).atStartOfDay()));
            q.setInt(i, quantidade);
            try (var r = q.executeQuery()) {
                var mensagens = new ArrayList<Mensagem>();
                while (r.next()) mensagens.add(ler(r));
                Collections.reverse(mensagens);
                return List.copyOf(mensagens);
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível carregar o histórico.", e);
        }
    }
}
