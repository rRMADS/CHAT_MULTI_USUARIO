package protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.*;

class LeitorLinhasTest {
    @Test
    void aceitaCrlfLimiteExatoEUltimaLinhaSemQuebra() throws Exception {
        var leitor = new StringReader("Olá\r\n12345\nFim");
        assertEquals("Olá", LeitorLinhas.ler(leitor, 5));
        assertEquals("12345", LeitorLinhas.ler(leitor, 5));
        assertEquals("Fim", LeitorLinhas.ler(leitor, 5));
        assertNull(LeitorLinhas.ler(leitor, 5));
        assertEquals("", LeitorLinhas.ler(new StringReader("\n"), 5));
    }

    @Test
    void recusaRespostaExcessivaAntesDeAcumularTudo() {
        assertThrows(
                IOException.class,
                () ->
                        LeitorLinhas.ler(
                                new StringReader("x".repeat(LeitorLinhas.LIMITE_EVENTO + 1)),
                                LeitorLinhas.LIMITE_EVENTO));
    }
}
