package model;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
class DominioTest {
    private final LocalDateTime horario = LocalDateTime.of(2026, 11, 4, 15, 30);
    @Test void usuarioRejeitaNomeVazioOuComEspaco() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario(""));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("Ana Silva"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("a".repeat(31)));
    }
    @Test void nomesSaoIguaisSemDistinguirMaiusculas() {
        assertEquals(new Usuario("Ana"), new Usuario("ana"));
        assertEquals(new Usuario("Ana").hashCode(), new Usuario("ana").hashCode());
    }
    @Test void conteudoVazioOuNuloERecusado() {
        assertThrows(MensagemInvalidaException.class, () -> mensagem("  "));
        assertThrows(MensagemInvalidaException.class, () -> mensagem(null));
    }
    @Test void limiteDe500CaracteresEAplicado() { assertEquals(500, mensagem("x".repeat(500)).conteudo().length()); assertThrows(MensagemInvalidaException.class, () -> mensagem("x".repeat(501))); }
    @Test void controlesNaoPodemInjetarNovasLinhas() { assertThrows(MensagemInvalidaException.class, () -> mensagem("oi\n/quit")); }
    @Test void formataRemetenteEHorarioDeterministico() { assertEquals("[04/11 15:30] Ana: Olá!", mensagem("  Olá!  ").formatar()); }
    @Test void atribuirIdPreservaInstanciaOriginal() {
        var original = mensagem("Olá"); assertNull(original.id()); assertEquals(7L, original.comId(7L).id()); assertNull(original.id());
    }
    private Mensagem mensagem(String texto) { return new Mensagem(null, new Usuario("Ana"), texto, horario); }
}
