package client;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.lang.ref.WeakReference;

/** Preferência local: nunca é enviada ao servidor. */
final class Temas {
    enum Tema { ROXO("Roxo"), CLARO("Claro"), ESCURO("Escuro");
        private final String nome; Tema(String nome) { this.nome=nome; }
        @Override public String toString() { return nome; }
    }
    private final Path arquivo;
    private Tema atual = Tema.ROXO;
    private final List<WeakReference<Parent>> raizes = new ArrayList<>();
    private final List<WeakReference<ComboBox<Tema>>> seletores = new ArrayList<>();
    Temas() { this(Path.of(System.getProperty("conexo.preferencias", "cliente.properties"))); }
    Temas(Path arquivo) {
        this.arquivo=arquivo;
        try (var leitor=Files.newBufferedReader(arquivo)) {
            Properties p=new Properties(); p.load(leitor); atual=Tema.valueOf(p.getProperty("tema", "ROXO"));
        } catch(IOException | IllegalArgumentException ignored) { }
    }
    Tema atual() { return atual; }
    void registrar(Parent raiz) { raizes.add(new WeakReference<>(raiz)); aplicar(raiz); }
    private void aplicar(Parent raiz) {
        raiz.getStyleClass().removeAll("theme-purple", "theme-light", "theme-dark");
        raiz.getStyleClass().add(switch(atual) { case ROXO -> "theme-purple"; case CLARO -> "theme-light"; case ESCURO -> "theme-dark"; });
    }
    void selecionar(Tema tema) {
        atual=Objects.requireNonNull(tema);
        Application.setUserAgentStylesheet((tema==Tema.ESCURO ? new PrimerDark() : new PrimerLight()).getUserAgentStylesheet());
        raizes.removeIf(r -> r.get()==null); raizes.forEach(r -> { if(r.get()!=null) aplicar(r.get()); });
        seletores.removeIf(r -> r.get()==null); seletores.forEach(r -> { if(r.get()!=null && r.get().getValue()!=tema) r.get().setValue(tema); });
        Properties p=new Properties(); p.setProperty("tema", tema.name());
        try (var escritor=Files.newBufferedWriter(arquivo)) { p.store(escritor,"Preferencia de tema do Conexo"); }
        catch(IOException ignored) { /* Não impedir a conversa se a pasta for somente leitura. */ }
    }
    ComboBox<Tema> seletor(boolean lateral) {
        ComboBox<Tema> c=new ComboBox<>(); c.getItems().setAll(Tema.values()); c.setValue(atual);
        c.setAccessibleText("Tema do programa"); c.setMaxWidth(Double.MAX_VALUE);
        c.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Tema item,boolean vazio) { super.updateItem(item,vazio); setText(vazio || item==null ? null : "Tema · "+item); }
        });
        if(lateral) c.getStyleClass().add("room-picker");
        seletores.add(new WeakReference<>(c));
        c.setOnAction(e -> { if(c.getValue()!=null && c.getValue()!=atual) selecionar(c.getValue()); }); return c;
    }
}
