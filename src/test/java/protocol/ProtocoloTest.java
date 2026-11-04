package protocol;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProtocoloTest {
    @Test void preservaAcentosSeparadoresEEmoji() { var e = Evento.de(Evento.Tipo.MENSAGEM, "1", "João", "Olá! 🙂\ttexto", "2026-11-04T12:30:00"); assertEquals(e, Protocolo.decodificar(Protocolo.codificar(e))); }
    @Test void preservaEventosSemCampos() { var e = Evento.de(Evento.Tipo.PONG); assertEquals(e, Protocolo.decodificar(Protocolo.codificar(e))); }
    @Test void rejeitaEventoDesconhecido() { assertThrows(IllegalArgumentException.class, () -> Protocolo.decodificar("DESCONHECIDO")); }
    @Test void respostaDeComandoPreservaTituloELinhas() {
        var resposta = Evento.resposta("Ajuda", "/help — ajuda\n/join cafe — entrar no Café");
        assertEquals(resposta, Protocolo.decodificar(Protocolo.codificar(resposta)));
        assertEquals("Ajuda", resposta.campo(1));
    }
}
