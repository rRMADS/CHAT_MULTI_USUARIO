package client;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Compartilha o arquivo local sem sobrescrever opções de outros componentes. */
final class PreferenciasCliente {
    private final Path arquivo;

    PreferenciasCliente() {
        this(Path.of(System.getProperty("conexo.preferencias", "cliente.properties")));
    }

    PreferenciasCliente(Path arquivo) {
        this.arquivo = arquivo;
    }

    Properties carregar() {
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(arquivo)) {
            p.load(r);
        } catch (IOException ignored) {
        }
        return p;
    }

    String ler(String chave, String padrao) {
        return carregar().getProperty(chave, padrao);
    }

    void salvar(String chave, String valor) {
        salvar(Map.of(chave, valor));
    }

    void salvar(Map<String, String> atualizacoes) {
        Properties p = carregar();
        p.putAll(atualizacoes);
        try {
            Path pai = arquivo.toAbsolutePath().getParent();
            Files.createDirectories(pai);
            Path temporario = Files.createTempFile(pai, "orkut-preferencias-", ".tmp");
            try {
                try (var w = Files.newBufferedWriter(temporario)) {
                    p.store(w, "Preferencias locais do Orkut 2.0");
                }
                try {
                    Files.move(
                            temporario,
                            arquivo.toAbsolutePath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(
                            temporario,
                            arquivo.toAbsolutePath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporario);
            }
        } catch (IOException ignored) {
            /* Preferências não impedem o uso do chat. */
        }
    }
}
