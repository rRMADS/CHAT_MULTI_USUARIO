package service;

import model.*;
import protocol.*;
import support.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocol.Evento.Tipo.*;

class IdentidadeTest {
    @Test void handshakeExigeEmojiConhecidoERecusaClienteAntigo() {
        var u=new Usuario("Ana Silva","unicorn"); assertEquals(EmojiIdentificacao.UNICORN,Protocolo.identificar(Protocolo.apresentar(u)).emoji());
        for(String linha:new String[]{"Ana",Protocolo.codificar(Evento.de(APRESENTACAO,"Ana")),Protocolo.codificar(Evento.de(APRESENTACAO,"Ana","")),Protocolo.codificar(Evento.de(APRESENTACAO,"Ana","https://imagem"))})
            assertThrows(IllegalArgumentException.class,()->Protocolo.identificar(linha));
    }
    @Test void emojiNaoPermiteDuplicarApelidoOuImitarAssistente() {
        var r=new ClientRegistry(); r.adicionar(new FakeConexao("Ana","cat"));
        assertThrows(IllegalArgumentException.class,()->r.adicionar(new FakeConexao("ana","fox")));
        assertThrows(IllegalArgumentException.class,()->r.adicionar(new FakeConexao("Conexo IA","robot")));
    }
    @Test void perfilMensagemPrivadaEHistoricoTransportamEmojiEscolhido() {
        var chat=new ChatService(new InMemoryMensagemRepository(),new ClientRegistry(),Clock.systemUTC(),50);
        var ana=new FakeConexao("Ana","unicorn"); var b=new FakeConexao("Bruno","fox"); chat.conectar(ana); chat.conectar(b);
        assertTrue(b.recebidos.stream().anyMatch(e->e.tipo()==PERFIS && e.campos().contains("unicorn")));
        ana.limpar(); b.limpar(); chat.enviarMensagem(ana,"Olá"); chat.enviarPrivada(ana,"Bruno","Privada");
        assertEquals("unicorn",b.recebidos.stream().filter(e->e.tipo()==MENSAGEM).findFirst().orElseThrow().campo(4));
        assertEquals("unicorn",b.recebidos.stream().filter(e->e.tipo()==PRIVADA).findFirst().orElseThrow().campo(4));
        b.limpar(); chat.buscarHistorico(b,50); assertEquals("unicorn",b.recebidos.stream().filter(e->e.tipo()==MENSAGEM).findFirst().orElseThrow().campo(4));
    }
}
