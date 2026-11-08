package client;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;

import javafx.application.Application;
import javafx.scene.Parent;

import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.util.*;

/** Preferência local: nunca é enviada ao servidor. */
final class Temas {
    enum Tema {
        ROXO("Roxo"),
        CLARO("Claro"),
        ESCURO("Escuro");
        private final String nome;

        Tema(String nome) {
            this.nome = nome;
        }

        @Override
        public String toString() {
            return nome;
        }
    }

    private final Path arquivo;
    private Tema atual = Tema.ROXO;
    private boolean inicializado;
    private final List<WeakReference<Parent>> raizes = new ArrayList<>();
    private final List<WeakReference<SeletorTema>> seletores = new ArrayList<>();

    Temas() {
        this(Path.of(System.getProperty("conexo.preferencias", "cliente.properties")));
    }

    Temas(Path arquivo) {
        this.arquivo = arquivo;
        try (var leitor = Files.newBufferedReader(arquivo)) {
            Properties p = new Properties();
            p.load(leitor);
            atual = Tema.valueOf(p.getProperty("tema", "ROXO"));
        } catch (IOException | IllegalArgumentException ignored) {
        }
    }

    Tema atual() {
        return atual;
    }

    void registrar(Parent raiz) {
        raizes.add(new WeakReference<>(raiz));
        aplicar(raiz);
    }

    private void aplicar(Parent raiz) {
        raiz.getStyleClass().removeAll("theme-purple", "theme-light", "theme-dark");
        raiz.getStyleClass()
                .add(
                        switch (atual) {
                            case ROXO -> "theme-purple";
                            case CLARO -> "theme-light";
                            case ESCURO -> "theme-dark";
                        });
    }

    void selecionar(Tema tema) {
        Objects.requireNonNull(tema);
        if (inicializado && atual == tema) return;
        inicializado = true;
        atual = tema;
        Application.setUserAgentStylesheet(
                (tema == Tema.ESCURO ? new PrimerDark() : new PrimerLight())
                        .getUserAgentStylesheet());
        raizes.removeIf(r -> r.get() == null);
        raizes.forEach(r -> {
            if (r.get() != null) aplicar(r.get());
        });
        seletores.removeIf(r -> r.get() == null);
        seletores.forEach(r -> {
            if (r.get() != null) r.get().atualizar(tema, true);
        });
        Properties p = new Properties();
        p.setProperty("tema", tema.name());
        try (var escritor = Files.newBufferedWriter(arquivo)) {
            p.store(escritor, "Preferencia de tema do Conexo");
        } catch (IOException ignored) {
            /* Não impedir a conversa se a pasta for somente leitura. */
        }
    }

    SeletorTema seletor(boolean lateral) {
        SeletorTema c = new SeletorTema(this, lateral);
        c.setAccessibleText("Escolher tema do programa");
        seletores.add(new WeakReference<>(c));
        return c;
    }
}
