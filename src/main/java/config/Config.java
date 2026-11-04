package config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Properties;
public record Config(String bind, int porta, String banco, String usuarioBanco, String senhaBanco, int historico, int maxClientes) {
    public Config {
        if (porta < 0 || porta > 65535 || historico < 1 || historico > 200 || maxClientes < 1 || maxClientes > 500)
            throw new IllegalArgumentException("Configuração inválida: confira porta, histórico e limite de clientes.");
    }
    public static Config carregar() throws IOException {
        Properties p = new Properties(); Path arquivo = Path.of("chat.properties");
        if (Files.exists(arquivo)) try (var in = Files.newInputStream(arquivo)) { p.load(in); }
        return new Config(p.getProperty("server.bind", "0.0.0.0"), Integer.parseInt(p.getProperty("server.port", "5050")),
                p.getProperty("db.url", "jdbc:h2:./data/chat"), p.getProperty("db.user", "sa"), p.getProperty("db.password", ""),
                Integer.parseInt(p.getProperty("history.limit", "50")), Integer.parseInt(p.getProperty("server.maxClients", "100")));
    }
}
