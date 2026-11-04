package config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import model.Sala;
public record Config(String bind, int porta, String banco, String usuarioBanco, String senhaBanco, int historico, int maxClientes, List<Sala> salas) {
    public Config {
        if (porta < 0 || porta > 65535 || historico < 1 || historico > 200 || maxClientes < 1 || maxClientes > 500)
            throw new IllegalArgumentException("Configuração inválida: confira porta, histórico e limite de clientes.");
        if (salas == null || salas.isEmpty() || salas.size() > 20)
            throw new IllegalArgumentException("Configure de 1 a 20 salas em server.rooms.");
        salas = List.copyOf(salas);
        var ids = new HashSet<String>();
        for (var sala : salas) if (!ids.add(sala.id())) throw new IllegalArgumentException("Identificador de sala repetido: " + sala.id());
        if (!ids.contains(Sala.GERAL.id())) throw new IllegalArgumentException("Mantenha uma sala com identificador geral em server.rooms. O nome exibido pode ser alterado.");
    }
    public Config(String bind, int porta, String banco, String usuarioBanco, String senhaBanco, int historico, int maxClientes) {
        this(bind, porta, banco, usuarioBanco, senhaBanco, historico, maxClientes, Sala.padrao());
    }
    public static Config carregar() throws IOException {
        return carregar(Path.of("chat.properties"));
    }
    public static Config carregar(Path arquivo) throws IOException {
        Properties p = new Properties();
        if (Files.exists(arquivo)) try (var leitor = new PushbackReader(Files.newBufferedReader(arquivo, StandardCharsets.UTF_8), 1)) {
            int primeiro = leitor.read(); if (primeiro != -1 && primeiro != '\uFEFF') leitor.unread(primeiro);
            p.load(leitor);
        }
        return new Config(p.getProperty("server.bind", "0.0.0.0"), Integer.parseInt(p.getProperty("server.port", "5050")),
                p.getProperty("db.url", "jdbc:h2:./data/chat"), p.getProperty("db.user", "sa"), p.getProperty("db.password", ""),
                Integer.parseInt(p.getProperty("history.limit", "50")), Integer.parseInt(p.getProperty("server.maxClients", "100")),
                p.containsKey("server.rooms") ? lerSalas(p.getProperty("server.rooms")) : Sala.padrao());
    }
    private static List<Sala> lerSalas(String configuracao) {
        var salas = new ArrayList<Sala>();
        for (var item : configuracao.split(";", -1)) {
            if (item.isBlank()) continue;
            String[] partes = item.split(":", 2);
            if (partes.length != 2) throw new IllegalArgumentException("Use server.rooms=geral:Geral;estudos:Estudos (identificador:nome). ");
            salas.add(new Sala(partes[0].strip().toLowerCase(Locale.ROOT), partes[1].strip()));
        }
        return salas;
    }
}
