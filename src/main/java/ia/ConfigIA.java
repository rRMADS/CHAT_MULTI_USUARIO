package ia;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.net.URI;

public record ConfigIA(boolean habilitada, String chave, String modelo) {
    public static ConfigIA carregar(Path arquivo, Map<String,String> ambiente) throws IOException {
        Properties p=new Properties();
        if(Files.exists(arquivo)) try(var r=new PushbackReader(Files.newBufferedReader(arquivo),1)) {
            int primeiro=r.read(); if(primeiro!=-1 && primeiro!='\uFEFF') r.unread(primeiro); p.load(r);
        }
        return new ConfigIA(Boolean.parseBoolean(p.getProperty("ai.enabled","false")),
                ambiente.getOrDefault("GROQ_API_KEY",""),p.getProperty("ai.model","openai/gpt-oss-20b"));
    }
    public boolean disponivel() { return habilitada && chave!=null && !chave.isBlank(); }
    public AssistenteIA criar() {
        return new AssistenteIA(disponivel() ? new ClienteGroq(URI.create("https://api.groq.com/openai/v1/chat/completions"),chave,modelo) : null);
    }
    @Override public String toString() { return "ConfigIA[habilitada="+habilitada+", chave=oculta, modelo="+modelo+"]"; }
}
