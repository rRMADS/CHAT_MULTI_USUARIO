package model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class DominioTest {
    private final LocalDateTime horario = LocalDateTime.of(2026, 11, 4, 15, 30);

    @Test
    void usuarioRejeitaNomeVazioInvalidoOuLongo() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario(""));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("   "));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("Ana\nSilva"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("Ana\tSilva"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("Ana\"Silva"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("a".repeat(31)));
    }

    @Test
    void usuarioAceitaNomeComEspacosEAcentos() {
        assertEquals("João da Silva", new Usuario("João da Silva").nome());
        assertEquals(30, new Usuario("A".repeat(14) + " " + "B".repeat(15)).nome().length());
    }

    @Test
    void usuarioNormalizaEspacosParaEvitarIdentidadesDuplicadas() {
        var usuario = new Usuario("  Ana   Silva  ");
        assertEquals("Ana Silva", usuario.nome());
        assertEquals(new Usuario("ana silva"), usuario);
        assertEquals(new Usuario("ana silva").hashCode(), usuario.hashCode());
    }

    @Test
    void nomesSaoIguaisSemDistinguirMaiusculas() {
        assertEquals(new Usuario("Ana"), new Usuario("ana"));
        assertEquals(new Usuario("Ana").hashCode(), new Usuario("ana").hashCode());
    }

    @Test
    void conteudoVazioOuNuloERecusado() {
        assertThrows(MensagemInvalidaException.class, () -> mensagem("  "));
        assertThrows(MensagemInvalidaException.class, () -> mensagem(null));
    }

    @Test
    void limiteDe500CaracteresEAplicado() {
        assertEquals(500, mensagem("x".repeat(500)).conteudo().length());
        assertThrows(MensagemInvalidaException.class, () -> mensagem("x".repeat(501)));
    }

    @Test
    void controlesNaoPodemInjetarNovasLinhas() {
        assertThrows(MensagemInvalidaException.class, () -> mensagem("oi\n/quit"));
    }

    @Test
    void formataRemetenteEHorarioDeterministico() {
        assertEquals("[04/11 15:30] Ana: Olá!", mensagem("  Olá!  ").formatar());
    }

    @Test
    void atribuirIdPreservaInstanciaOriginal() {
        var original = mensagem("Olá");
        assertNull(original.id());
        assertEquals(7L, original.comId(7L).id());
        assertNull(original.id());
    }

    private Mensagem mensagem(String texto) {
        return new Mensagem(null, new Usuario("Ana"), texto, horario);
    }

    @Test
    void respostaIaPreservaMarkdownECodigoMultilinhaComLimiteProprio() {
        String markdown =
                "## Título\r\n\r\n1. **Passo**\r\n\r\n```java\r\n\tSystem.out.println(\"Olá\");\r\n```";
        var m = Mensagem.respostaIA(markdown, horario);
        assertEquals(markdown.replace("\r\n", "\n"), m.conteudo());
        assertEquals(FormatoMensagem.MARKDOWN_IA, m.comId(2L).formato());
        assertEquals(8000, Mensagem.respostaIA("x".repeat(8000), horario).conteudo().length());
        assertThrows(
                MensagemInvalidaException.class,
                () -> Mensagem.respostaIA("x".repeat(8001), horario));
        assertThrows(
                MensagemInvalidaException.class, () -> Mensagem.respostaIA("Olá\u0000", horario));
    }

    @Test
    void formatoIaNaoPodeSerUsadoPorParticipanteComum() {
        assertThrows(
                MensagemInvalidaException.class,
                () ->
                        new Mensagem(
                                null,
                                new Usuario("Ana"),
                                "## Título",
                                horario,
                                FormatoMensagem.MARKDOWN_IA));
        assertThrows(MensagemInvalidaException.class, () -> mensagem("x".repeat(501)));
        assertThrows(MensagemInvalidaException.class, () -> mensagem("linha\nlinha"));
    }
}
