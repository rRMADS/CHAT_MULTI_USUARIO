package client;

import static org.junit.jupiter.api.Assertions.*;

import model.Citacao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

class PreferenciasESalasTest {
    @TempDir Path pasta;

    @Test
    void rascunhosDestinatariosECitacoesSaoSeparadosPorSala() {
        var e = new EstadoSalas();
        e.guardar(
                "geral",
                new EstadoSalas.Rascunho(
                        "Texto Geral", "Todos", new Citacao(1, "Ana", "Original"), 4));
        e.guardar("estudos", new EstadoSalas.Rascunho("Texto Estudos", "Bruno", null, 5));
        assertEquals("Texto Geral", e.rascunho("geral").texto());
        assertEquals("Bruno", e.rascunho("estudos").destino());
        e.limparSala("geral");
        assertNull(e.rascunho("geral").citacao());
        assertEquals("Texto Geral", e.rascunho("geral").texto());
        e.limpar();
        assertEquals("", e.rascunho("geral").texto());
    }

    @Test
    void naoLidasNaoDuplicamELeituraMantemOutrasSalas() {
        var e = new EstadoSalas();
        e.nova("geral", 2);
        e.nova("geral", 4);
        e.nova("geral", 4);
        e.nova("estudos", 3);
        assertEquals(3, e.total());
        assertEquals(2L, e.primeira("geral"));
        e.lerAte("geral", 2);
        assertEquals(4L, e.primeira("geral"));
        e.lerAte("geral", 4);
        e.nova("geral", 2);
        assertEquals(0, e.quantidade("geral"));
        assertEquals(1, e.quantidade("estudos"));
    }

    @Test
    void preferenciasNovasPreservamTemaEUnicode() {
        var p = new PreferenciasCliente(pasta.resolve("cliente.properties"));
        p.salvar("tema", "ROXO");
        p.salvar(Map.of("emojis.favoritos", "💜|😂", "avisos.modo", "SILENCIOSO"));
        assertEquals("ROXO", p.ler("tema", ""));
        assertEquals(
                "💜|😂",
                new PreferenciasCliente(pasta.resolve("cliente.properties"))
                        .ler("emojis.favoritos", ""));
        p.salvar("tema", "ESCURO");
        assertEquals("SILENCIOSO", p.ler("avisos.modo", ""));
    }

    @Test
    void modosDeAvisoFiltramPublicasMencoesEPrivadas() {
        var p = new PreferenciasCliente(pasta.resolve("cliente.properties"));
        var n = new Notificacoes(p);
        assertFalse(n.permite("servidor:5050", "geral", Notificacoes.Tipo.PUBLICA));
        assertTrue(n.permite("servidor:5050", "geral", Notificacoes.Tipo.MENCAO));
        p.salvar("avisos.modo", "TODAS");
        assertTrue(n.permite("servidor:5050", "geral", Notificacoes.Tipo.PUBLICA));
        p.salvar("avisos.modo", "SILENCIOSO");
        assertFalse(n.permite("servidor:5050", "geral", Notificacoes.Tipo.PRIVADA));
    }
}
