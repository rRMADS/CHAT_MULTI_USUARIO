package service;

import ia.*;
import model.*;
import protocol.*;
import support.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static protocol.Evento.Tipo.*;

class IntegracaoIATest {
    private static final Sala ESTUDOS=new Sala("estudos","Estudos");
    private static final class Cenario implements AutoCloseable {
        final InMemoryMensagemRepository geral=new InMemoryMensagemRepository(), estudos=new InMemoryMensagemRepository();
        final SalaService salas;
        final FakeConexao ana=new FakeConexao("Ana"),bruno=new FakeConexao("Bruno"),carla=new FakeConexao("Carla");
        Cenario(ClienteIA api) {
            salas=new SalaService(Map.of(Sala.GERAL,chat(geral,Sala.GERAL),ESTUDOS,chat(estudos,ESTUDOS)),new AssistenteIA(api));
            salas.conectar(ana); salas.conectar(bruno); salas.conectar(carla); salas.trocarSala(carla,"estudos"); ana.limpar(); bruno.limpar(); carla.limpar();
        }
        static ChatService chat(InMemoryMensagemRepository r,Sala s) { return new ChatService(r,new ClientRegistry(),Clock.systemUTC(),50,s); }
        public void close() { salas.close(); }
    }
    private static List<Evento> eventos(FakeConexao c,Evento.Tipo tipo) { return c.recebidos.stream().filter(e->e.tipo()==tipo).toList(); }
    private static void aguardar(FakeConexao c,Evento.Tipo tipo) throws Exception {
        long fim=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(eventos(c,tipo).isEmpty() && System.nanoTime()<fim) Thread.sleep(10);
        assertFalse(eventos(c,tipo).isEmpty(),"Não recebeu "+tipo);
    }
    @Test void privadaNaoEPublicadaNemSalva() throws Exception {
        try(var c=new Cenario(p->"Resposta privada")) {
            c.salas.executar(c.ana,"/ia privado Minha pergunta"); aguardar(c.ana,IA_RESPOSTA);
            assertEquals("privado",eventos(c.ana,IA_RESPOSTA).get(0).campo(0));
            assertTrue(c.bruno.recebidos.isEmpty()); assertTrue(c.carla.recebidos.isEmpty()); assertTrue(c.geral.buscarUltimas(50).isEmpty());
        }
    }
    @Test void publicaPerguntaERespostaSomenteNaSalaAtualComEmojiDoBot() throws Exception {
        try(var c=new Cenario(p->"Resposta pública\nSegunda linha\t "+"texto ".repeat(160))) {
            c.salas.executar(c.ana,"/ia sala Minha pergunta"); aguardar(c.ana,IA_RESPOSTA);
            var mensagens=c.geral.buscarUltimas(50); assertEquals("Pergunta à IA: Minha pergunta",mensagens.get(0).conteudo());
            assertEquals(2,mensagens.size());
            assertEquals("Resposta pública\nSegunda linha\t "+"texto ".repeat(160).stripTrailing(),mensagens.get(1).conteudo());
            assertTrue(mensagens.get(1).conteudo().length()>500); assertEquals(FormatoMensagem.MARKDOWN_IA,mensagens.get(1).formato());
            assertEquals("MARKDOWN_IA",eventos(c.bruno,MENSAGEM).get(1).campo(5));
            assertEquals("robot",mensagens.get(1).remetente().emoji().id());
            assertEquals(eventos(c.ana,MENSAGEM),eventos(c.bruno,MENSAGEM)); assertTrue(c.carla.recebidos.isEmpty()); assertTrue(c.estudos.buscarUltimas(50).isEmpty());
        }
    }
    @Test void consultaLentaNaoBloqueiaChatERecusaSegundaPerguntaDaMesmaPessoa() throws Exception {
        CountDownLatch iniciou=new CountDownLatch(1),liberar=new CountDownLatch(1);
        try(var c=new Cenario(p->{iniciou.countDown(); liberar.await(); return "Pronto";})) {
            c.salas.executar(c.ana,"/ia privado Oi"); assertTrue(iniciou.await(2,TimeUnit.SECONDS));
            c.salas.executar(c.ana,"/ia sala Outra"); assertEquals(1,eventos(c.ana,IA_ERRO).size());
            c.salas.executar(c.bruno,"Chat continua"); assertEquals("Chat continua",c.geral.buscarUltimas(1).get(0).conteudo());
            liberar.countDown(); aguardar(c.ana,IA_RESPOSTA);
        } finally { liberar.countDown(); }
    }
    @Test void limparDuranteConsultaImpedeRespostaPublicaAtrasada() throws Exception {
        CountDownLatch iniciou=new CountDownLatch(1),liberar=new CountDownLatch(1);
        try(var c=new Cenario(p->{iniciou.countDown(); liberar.await(); return "Resposta atrasada";})) {
            c.salas.executar(c.ana,"/ia sala Oi"); assertTrue(iniciou.await(2,TimeUnit.SECONDS));
            c.salas.executar(c.bruno,"/limpar"); c.salas.executar(c.bruno,"/limpar confirmar"); liberar.countDown(); aguardar(c.ana,IA_ERRO);
            assertTrue(c.geral.buscarUltimas(50).isEmpty()); assertTrue(eventos(c.ana,IA_RESPOSTA).isEmpty());
        } finally { liberar.countDown(); }
    }
    @Test void sairDaSalaCancelaConsultaESemRespostaEmSalaErrada() throws Exception {
        CountDownLatch iniciou=new CountDownLatch(1),cancelou=new CountDownLatch(1);
        try(var c=new Cenario(p->{iniciou.countDown(); try {new CountDownLatch(1).await();} catch(InterruptedException e) { cancelou.countDown(); throw e; } return "Não deve publicar";})) {
            c.salas.executar(c.ana,"/ia sala Oi"); assertTrue(iniciou.await(2,TimeUnit.SECONDS)); c.salas.trocarSala(c.ana,"estudos");
            assertTrue(cancelou.await(2,TimeUnit.SECONDS)); assertTrue(c.estudos.buscarUltimas(50).isEmpty()); assertEquals(1,c.geral.buscarUltimas(50).size());
            assertTrue(eventos(c.ana,IA_RESPOSTA).isEmpty());
        }
    }
    @Test void semChaveNaoConsultaNemPublicaPergunta() {
        try(var c=new Cenario(null)) {
            c.salas.executar(c.ana,"/ia sala Oi"); assertEquals(1,eventos(c.ana,IA_ERRO).size()); assertTrue(c.bruno.recebidos.isEmpty()); assertTrue(c.geral.buscarUltimas(50).isEmpty());
        }
    }
    @Test void falhaNoProvedorNaoViraRespostaDeChat() throws Exception {
        try(var c=new Cenario(p->{ throw new java.io.IOException("detalhe interno"); })) {
            c.salas.executar(c.ana,"/ia privado Oi"); aguardar(c.ana,IA_ERRO); assertFalse(eventos(c.ana,IA_ERRO).get(0).campo(0).contains("interno"));
            assertTrue(c.geral.buscarUltimas(50).isEmpty()); assertTrue(eventos(c.ana,IA_RESPOSTA).isEmpty());
        }
    }
    @Test void respostaCompletaChegaAoHistoricoEBuscaSemPerderLinhas() throws Exception {
        String markdown="## Receita\n\n1. **Primeiro passo**\n2. Segundo passo\n\n| Item | Quantidade |\n| --- | --- |\n| Limão | 2 |\n\n```java\nSystem.out.println(\"Olá\");\n```";
        try(var c=new Cenario(p->markdown)) {
            c.salas.executar(c.ana,"/ia sala Receita"); aguardar(c.ana,IA_RESPOSTA);
            c.bruno.limpar(); c.salas.executar(c.bruno,"/history 50");
            var resposta=eventos(c.bruno,MENSAGEM).get(1); assertEquals(markdown,resposta.campo(2)); assertEquals("MARKDOWN_IA",resposta.campo(5));
            c.bruno.limpar(); c.salas.executar(c.bruno,"/buscar Limão"); assertEquals(markdown,eventos(c.bruno,MENSAGEM).get(0).campo(2));
            assertEquals(resposta,Protocolo.decodificar(Protocolo.codificar(resposta)));
        }
    }
}
