package client;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.*;

final class Notificacoes implements AutoCloseable {
    enum Modo {
        TODAS("Todas as mensagens"),
        MENCOES_PRIVADAS("Somente menções e privadas"),
        SILENCIOSO("Silencioso");
        private final String nome;

        Modo(String nome) {
            this.nome = nome;
        }

        @Override
        public String toString() {
            return nome;
        }
    }

    enum Tipo {
        PUBLICA,
        MENCAO,
        PRIVADA
    }

    private final PreferenciasCliente preferencias;

    Notificacoes() {
        this(new PreferenciasCliente());
    }

    Notificacoes(PreferenciasCliente preferencias) {
        this.preferencias = preferencias;
    }

    private java.awt.TrayIcon icone;

    private String chaveSala(String servidor, String sala) {
        return "avisos.sala."
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(servidor.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "."
                + sala;
    }

    Modo modo() {
        try {
            return Modo.valueOf(preferencias.ler("avisos.modo", "MENCOES_PRIVADAS"));
        } catch (IllegalArgumentException e) {
            return Modo.MENCOES_PRIVADAS;
        }
    }

    boolean permite(String servidor, String sala, Tipo tipo) {
        if (Boolean.parseBoolean(preferencias.ler(chaveSala(servidor, sala), "false")))
            return false;
        return modo() == Modo.TODAS || modo() == Modo.MENCOES_PRIVADAS && tipo != Tipo.PUBLICA;
    }

    void configurar(Stage dono, Temas temas, String servidor, Map<String, String> salas) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.initOwner(dono);
        dialogo.setTitle("Notificações");
        var p = dialogo.getDialogPane();
        temas.registrar(p);
        p.getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm());
        ComboBox<Modo> modo = new ComboBox<>();
        modo.getItems().setAll(Modo.values());
        modo.setValue(modo());
        modo.setMaxWidth(Double.MAX_VALUE);
        CheckBox desktop = new CheckBox("Mostrar avisos do Windows em segundo plano");
        desktop.setWrapText(true);
        desktop.setSelected(Boolean.parseBoolean(preferencias.ler("avisos.desktop", "true")));
        VBox conteudo =
                new VBox(
                        12,
                        new Label("Quando você quer receber avisos?"),
                        modo,
                        desktop,
                        new Label("Silenciar salas específicas:"));
        conteudo.setPrefWidth(430);
        Map<String, CheckBox> caixas = new LinkedHashMap<>();
        salas.forEach((id, nome) -> {
            CheckBox c = new CheckBox(nome);
            c.setSelected(Boolean.parseBoolean(preferencias.ler(chaveSala(servidor, id), "false")));
            caixas.put(id, c);
            conteudo.getChildren().add(c);
        });
        ScrollPane scroll = new ScrollPane(conteudo);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(
                Math.min(
                        430, javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() - 200));
        p.setContent(scroll);
        p.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialogo.showAndWait()
                .filter(ButtonType.OK::equals)
                .ifPresent(b -> {
                    Map<String, String> valores = new HashMap<>();
                    valores.put("avisos.modo", modo.getValue().name());
                    valores.put("avisos.desktop", Boolean.toString(desktop.isSelected()));
                    caixas.forEach((id, c) ->
                            valores.put(chaveSala(servidor, id), Boolean.toString(c.isSelected())));
                    preferencias.salvar(valores);
                });
    }

    void desktop(Stage janela, String titulo, String texto) {
        if (!janela.isShowing()
                || janela.isFocused() && !janela.isIconified()
                || !Boolean.parseBoolean(preferencias.ler("avisos.desktop", "true"))) return;
        try {
            if (!java.awt.SystemTray.isSupported()) return;
            if (icone == null) {
                var imagem =
                        new java.awt.image.BufferedImage(
                                32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = imagem.createGraphics();
                g.setColor(new java.awt.Color(124, 58, 237));
                g.fillOval(1, 1, 30, 30);
                g.setColor(java.awt.Color.WHITE);
                g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 23));
                g.drawString("O", 7, 25);
                g.dispose();
                icone = new java.awt.TrayIcon(imagem, model.Marca.NOME);
                icone.setImageAutoSize(true);
                icone.addActionListener(e ->
                        javafx.application.Platform.runLater(() -> {
                            janela.setIconified(false);
                            janela.toFront();
                            janela.requestFocus();
                        }));
                java.awt.SystemTray.getSystemTray().add(icone);
            }
            icone.displayMessage(titulo, texto, java.awt.TrayIcon.MessageType.INFO);
        } catch (java.awt.AWTException | RuntimeException ignored) {
            /* O aviso dentro do chat continua disponível. */
        }
    }

    @Override
    public void close() {
        if (icone != null) {
            java.awt.SystemTray.getSystemTray().remove(icone);
            icone = null;
        }
    }
}
