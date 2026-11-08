package model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

class AnexoTest {
    @Test
    void validaLimitesNomesETipos() {
        String id = UUID.randomUUID().toString();
        for (String nome : new String[] {"../a.txt", "C:\\a.txt", "a.exe", "a/b.txt", "a\n.txt"})
            assertThrows(
                    IllegalArgumentException.class, () -> new Anexo(id, nome, 4, "text/plain"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Anexo(id, "a.txt", Anexo.LIMITE + 1L, "text/plain"));
        assertThrows(IllegalArgumentException.class, () -> new Anexo(id, "a.png", 4, "text/plain"));
        assertTrue(new Anexo(id, "a.PNG", 4, "image/png").imagem());
    }

    @Test
    void anexoAtravessaProtocoloESobreviveAoId() {
        var a = new Anexo(UUID.randomUUID().toString(), "Aula de redes.pdf", 5, "application/pdf");
        var m =
                new Mensagem(
                        1L,
                        new Usuario("Ana"),
                        "📎 Aula",
                        LocalDateTime.now(),
                        FormatoMensagem.TEXTO,
                        null,
                        a);
        assertEquals(m, protocol.MensagensProtocolo.ler(protocol.MensagensProtocolo.de(m)));
        assertEquals(a, m.comId(2L).anexo());
    }

    @Test
    void historicoDoAssistenteAnteriorContinuaValido() {
        var antiga =
                new Mensagem(
                        1L,
                        new Usuario("Conexo IA", "robot"),
                        "## Resposta anterior",
                        LocalDateTime.now(),
                        FormatoMensagem.MARKDOWN_IA);
        assertEquals("Conexo IA", antiga.remetente().nome());
        assertEquals(
                "Orkut IA", Mensagem.respostaIA("Olá", LocalDateTime.now()).remetente().nome());
    }
}
