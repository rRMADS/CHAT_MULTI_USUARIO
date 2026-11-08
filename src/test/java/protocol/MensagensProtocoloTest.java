package protocol;

import static org.junit.jupiter.api.Assertions.*;

import model.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class MensagensProtocoloTest {
    @Test
    void idaEVoltaPreservaCitadaIdentidadeFormatoEUnicode() {
        var m =
                new Mensagem(
                        12L,
                        new Usuario("Ana Silva", "dragon"),
                        "Olá! 💜",
                        LocalDateTime.now(),
                        FormatoMensagem.TEXTO,
                        new Citacao(1, "Bruno", "Original"));
        assertEquals(
                m,
                MensagensProtocolo.ler(
                        Protocolo.decodificar(Protocolo.codificar(MensagensProtocolo.de(m)))));
        var ia = Mensagem.respostaIA("## Título\n\n**Resposta**", LocalDateTime.now()).comId(13L);
        assertEquals(ia, MensagensProtocolo.ler(MensagensProtocolo.de(ia)));
    }

    @Test
    void aceitaFormatoAnteriorSemCitacaoERecusaEventoDiferente() {
        var m =
                MensagensProtocolo.ler(
                        Evento.de(
                                Evento.Tipo.MENSAGEM,
                                "1",
                                "Ana",
                                "Anterior",
                                "2026-11-07T12:00:00",
                                "smile"));
        assertNull(m.citacao());
        assertEquals(FormatoMensagem.TEXTO, m.formato());
        assertThrows(
                IllegalArgumentException.class,
                () -> MensagensProtocolo.ler(Evento.de(Evento.Tipo.PRIVADA, "Ana")));
    }
}
