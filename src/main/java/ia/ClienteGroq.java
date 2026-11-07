package ia;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;

/** A chave fica exclusivamente no processo do servidor. Não envia histórico da sala. */
public final class ClienteGroq implements ClienteIA {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final URI endpoint;
    private final String chave,modelo;
    public ClienteGroq(URI endpoint,String chave,String modelo) {
        this.endpoint=endpoint; this.chave=chave; this.modelo=modelo;
    }
    @Override public String responder(String pergunta) throws Exception {
        JsonObject corpo=new JsonObject(); corpo.addProperty("model",modelo); corpo.addProperty("max_completion_tokens",2048); corpo.addProperty("stream",false);
        JsonArray mensagens=new JsonArray();
        mensagens.add(mensagem("system","Você é Conexo IA, assistente de um chat estudantil. Responda em português, de forma clara e concisa. Você só recebe a pergunta enviada; não tem acesso ao histórico nem aos participantes. Não invente acesso a ferramentas ou dados ao vivo. Limite a resposta a 6000 caracteres."));
        mensagens.add(mensagem("user",pergunta)); corpo.add("messages",mensagens);
        var pedido=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(25))
                .header("Authorization","Bearer "+chave).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString(),StandardCharsets.UTF_8)).build();
        // O handler recebe no máximo 128 KiB e mantém o timeout da requisição até o fim do corpo.
        var resposta=http.send(pedido, info -> new CorpoLimitado(131072));
        int status=resposta.statusCode();
        if(status==401 || status==403) throw new FalhaIA("A IA não pôde autenticar. Peça ao responsável pelo servidor para conferir a ativação.");
        if(status==429) throw new FalhaIA("A IA atingiu o limite de uso. Aguarde um pouco e tente novamente.");
        if(status!=200) throw new FalhaIA("A IA está indisponível no momento. Tente novamente mais tarde.");
        try {
            JsonObject json=JsonParser.parseString(new String(resposta.body(),StandardCharsets.UTF_8)).getAsJsonObject();
            String texto=json.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString().strip();
            if(texto.isBlank()) throw new IllegalStateException();
            if(texto.length()>8000) texto=texto.substring(0,7990)+"\n[…]";
            return texto;
        } catch(RuntimeException e) { throw new FalhaIA("A IA retornou uma resposta inválida. Tente novamente."); }
    }
    private static JsonObject mensagem(String papel,String texto) { JsonObject m=new JsonObject(); m.addProperty("role",papel); m.addProperty("content",texto); return m; }
    /** Cancela corpos excessivos antes de acumulá-los em memória. */
    private static final class CorpoLimitado implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> resultado=new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        private final int limite; private java.util.concurrent.Flow.Subscription assinatura;
        CorpoLimitado(int limite) { this.limite=limite; }
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return resultado; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription s) { assinatura=s; s.request(1); }
        public void onNext(java.util.List<java.nio.ByteBuffer> blocos) {
            for(var b:blocos) { if(bytes.size()+b.remaining()>limite) { assinatura.cancel(); resultado.completeExceptionally(new java.io.IOException("Resposta excessiva")); return; }
                byte[] lote=new byte[b.remaining()]; b.get(lote); bytes.writeBytes(lote); }
            assinatura.request(1);
        }
        public void onError(Throwable t) { resultado.completeExceptionally(t); }
        public void onComplete() { resultado.complete(bytes.toByteArray()); }
    }
}
