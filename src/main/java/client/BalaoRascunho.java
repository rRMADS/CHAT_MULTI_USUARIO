package client;

import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** Prévia exclusivamente local: nenhum conteúdo deste componente é enviado à rede. */
final class BalaoRascunho extends Region {
    private final Label titulo = new Label("Você · rascunho");
    private final Label corpo = new Label();
    private final Label dica = new Label("Prévia · só você vê · Enter para enviar");
    private final VBox bolha = new VBox(7, titulo, corpo, dica);
    private final DoubleProperty largura = new SimpleDoubleProperty(280);
    private Timeline ajuste;
    private boolean ativo;

    BalaoRascunho() {
        setId("previa-rascunho");
        getStyleClass().add("draft-preview");
        titulo.getStyleClass().add("message-author");
        titulo.setWrapText(true);
        corpo.getStyleClass().add("message-body");
        corpo.setWrapText(true);
        corpo.setMinWidth(0);
        dica.getStyleClass().add("draft-caption");
        dica.setWrapText(true);
        bolha.getStyleClass().addAll("bubble-own", "draft-bubble");
        bolha.setAlignment(Pos.TOP_LEFT);
        bolha.setManaged(false);
        getChildren().add(bolha);
        Rectangle recorte = new Rectangle();
        recorte.widthProperty().bind(widthProperty());
        recorte.heightProperty().bind(heightProperty());
        setClip(recorte);
        largura.addListener((obs, antes, depois) -> requestLayout());
        widthProperty()
                .addListener((obs, antes, depois) -> {
                    if (ativo) dimensionar();
                });
        setMinHeight(USE_PREF_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        limpar();
    }

    void atualizar(String texto, String destinatario, boolean permitido) {
        boolean mostrar = permitido && !texto.isBlank() && !texto.stripLeading().startsWith("/");
        if (!mostrar) {
            ocultar();
            return;
        }
        boolean nova = !ativo;
        ativo = true;
        titulo.setText(
                "Todos".equals(destinatario)
                        ? "Você · rascunho"
                        : "Você · rascunho para " + destinatario);
        corpo.setText(texto);
        bolha.getStyleClass().remove("bubble-private");
        if (!"Todos".equals(destinatario)) bolha.getStyleClass().add("bubble-private");
        if (nova) {
            setManaged(true);
            setVisible(true);
            setOpacity(0);
            setTranslateY(6);
        }
        dimensionar();
    }

    private void dimensionar() {
        if (ajuste != null) ajuste.stop();
        bolha.applyCss();
        double limite = Math.max(180, Math.min(620, getWidth() > 0 ? getWidth() : 620));
        double destino = Math.min(limite, Math.max(285, corpo.prefWidth(-1) + 40));
        double altura = bolha.prefHeight(destino);
        ajuste =
                new Timeline(
                        new KeyFrame(
                                Duration.millis(150),
                                new KeyValue(largura, destino, Animacoes.SUAVE),
                                new KeyValue(prefHeightProperty(), altura, Animacoes.SUAVE),
                                new KeyValue(opacityProperty(), 1, Animacoes.SUAVE),
                                new KeyValue(translateYProperty(), 0, Animacoes.SUAVE)));
        ajuste.play();
    }

    private void ocultar() {
        if (!ativo) return;
        ativo = false;
        if (ajuste != null) ajuste.stop();
        ajuste =
                new Timeline(
                        new KeyFrame(
                                Duration.millis(120),
                                new KeyValue(prefHeightProperty(), 0, Animacoes.SUAVE),
                                new KeyValue(opacityProperty(), 0)));
        ajuste.setOnFinished(e -> {
            setManaged(false);
            setVisible(false);
            corpo.setText("");
        });
        ajuste.play();
    }

    void limpar() {
        ativo = false;
        if (ajuste != null) ajuste.stop();
        corpo.setText("");
        setManaged(false);
        setVisible(false);
        setPrefHeight(0);
        setOpacity(0);
    }

    void parar() {
        if (ajuste != null) ajuste.stop();
    }

    @Override
    protected void layoutChildren() {
        double w = Math.min(getWidth(), largura.get());
        bolha.resizeRelocate(getWidth() - w, 0, w, bolha.prefHeight(w));
    }

    @Override
    protected double computeMinWidth(double height) {
        return 0;
    }

    @Override
    protected double computePrefWidth(double height) {
        return 620;
    }
}
