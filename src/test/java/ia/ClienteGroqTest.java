package ia;

import com.sun.net.httpserver.HttpServer;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ClienteGroqTest {
    private HttpServer servidor;
    @AfterEach void parar() { if(servidor!=null) servidor.stop(0); }
    private ClienteGroq cliente(int status,String corpo,AtomicReference<String> pedido,AtomicReference<String> auth) throws Exception {
        servidor=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        servidor.createContext("/chat/completions",e->{
            pedido.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)); auth.set(e.getRequestHeaders().getFirst("Authorization"));
            byte[] b=corpo.getBytes(StandardCharsets.UTF_8); e.sendResponseHeaders(status,b.length); try(var out=e.getResponseBody()) { out.write(b); }
        }); servidor.start(); return new ClienteGroq(URI.create("http://127.0.0.1:"+servidor.getAddress().getPort()+"/chat/completions"),"chave-ficticia-de-teste","modelo-teste");
    }
    @Test void enviaSomentePerguntaERecebeUnicodeSemVazarChaveNoCorpo() throws Exception {
        var pedido=new AtomicReference<String>(); var auth=new AtomicReference<String>();
        var c=cliente(200,"{\"choices\":[{\"message\":{\"content\":\"Olá! 🦄\"}}]}",pedido,auth);
        assertEquals("Olá! 🦄",c.responder("Explique Java")); assertEquals("Bearer chave-ficticia-de-teste",auth.get());
        var json=JsonParser.parseString(pedido.get()).getAsJsonObject(); assertEquals(2,json.getAsJsonArray("messages").size());
        assertEquals("Explique Java",json.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString());
        assertFalse(pedido.get().contains("chave-ficticia")); assertEquals("modelo-teste",json.get("model").getAsString());
    }
    @Test void limiteDaApiRecebeErroAmigavelSemExporCorpo() throws Exception {
        var c=cliente(429,"segredo do provedor",new AtomicReference<>(),new AtomicReference<>());
        var e=assertThrows(FalhaIA.class,()->c.responder("Oi")); assertTrue(e.getMessage().contains("limite")); assertFalse(e.getMessage().contains("segredo"));
    }
    @Test void respostaInvalidaNaoApareceComoSucesso() throws Exception {
        var c=cliente(200,"{\"choices\":[]}",new AtomicReference<>(),new AtomicReference<>());
        assertTrue(assertThrows(FalhaIA.class,()->c.responder("Oi")).getMessage().contains("inválida"));
    }
    @Test void corpoExcessivoEInterrompido() throws Exception {
        var c=cliente(200,"x".repeat(140000),new AtomicReference<>(),new AtomicReference<>());
        assertThrows(java.io.IOException.class,()->c.responder("Oi"));
    }
    @Test void chaveVemDoAmbienteEConfiguracaoNaoAExibe(@TempDir Path pasta) throws Exception {
        Path p=pasta.resolve("chat.properties"); Files.writeString(p,"ai.enabled=true\nai.model=modelo-personalizado\n");
        var config=ConfigIA.carregar(p,Map.of("GROQ_API_KEY","segredo")); assertTrue(config.disponivel()); assertFalse(config.toString().contains("segredo"));
        assertFalse(ConfigIA.carregar(p,Map.of()).disponivel()); assertEquals("modelo-personalizado",config.modelo());
    }
}
