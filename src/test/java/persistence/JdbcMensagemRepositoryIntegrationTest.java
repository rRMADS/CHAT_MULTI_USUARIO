package persistence;
import model.*;
import persistence.jdbc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class JdbcMensagemRepositoryIntegrationTest {
    @TempDir Path pasta;
    private JdbcMensagemRepository criar(String url) { var r = new JdbcMensagemRepository(new ConnectionFactory(url, "sa", "")); r.inicializar(); return r; }
    private Mensagem mensagem(String t) { return new Mensagem(null, new Usuario("Ana"), t, LocalDateTime.of(2026, 11, 4, 12, 30)); }
    @Test void ultimasMensagensVemEmOrdemCronologicaComIdsGerados() {
        var r = criar("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        r.salvar(mensagem("a")); r.salvar(mensagem("b")); r.salvar(mensagem("c"));
        var ultimas = r.buscarUltimas(2); assertEquals(java.util.List.of("b", "c"), ultimas.stream().map(Mensagem::conteudo).toList()); assertTrue(ultimas.get(1).id() > ultimas.get(0).id());
    }
    @Test void buscaTrataAspasECuringasComoTextoLiteral() {
        var r = criar("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        r.salvar(mensagem("100% de atenção")); r.salvar(mensagem("O'Brien")); r.salvar(mensagem("outra"));
        assertEquals(1, r.buscarPorTexto("%", 10).size()); assertEquals(1, r.buscarPorTexto("o'brien", 10).size()); assertTrue(r.buscarPorTexto("' OR 1=1 --", 10).isEmpty());
    }
    @Test void historicoSobreviveANovaInstanciaDoRepository() {
        String url = "jdbc:h2:" + pasta.resolve("chat").toAbsolutePath();
        criar(url).salvar(mensagem("Persistente")); assertEquals("Persistente", criar(url).buscarUltimas(1).get(0).conteudo());
    }
}
