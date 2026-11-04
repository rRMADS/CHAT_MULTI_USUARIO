package client;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/** Um balão por participante, com pontos defasados e sem texto do rascunho. */
final class BalaoDigitacao extends HBox {
    private final ParallelTransition pontos = new ParallelTransition();

    BalaoDigitacao(String usuario, Node avatar) {
        super(12); setAlignment(Pos.TOP_LEFT);
        Label nome = new Label(usuario); nome.getStyleClass().add("message-author");
        nome.setWrapText(true); nome.setMaxWidth(360);
        Label estado = new Label("está digitando"); estado.getStyleClass().add("typing-state");
        HBox indicador = new HBox(6); indicador.setAlignment(Pos.CENTER_LEFT); indicador.setMinHeight(20);
        for (int i = 0; i < 3; i++) {
            Circle ponto = new Circle(3.5, Color.web("#598568")); indicador.getChildren().add(ponto);
            Timeline onda = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(ponto.translateYProperty(), 0), new KeyValue(ponto.opacityProperty(), .45)),
                    new KeyFrame(Duration.millis(220), new KeyValue(ponto.translateYProperty(), -4, Animacoes.SUAVE), new KeyValue(ponto.opacityProperty(), 1)),
                    new KeyFrame(Duration.millis(440), new KeyValue(ponto.translateYProperty(), 0, Animacoes.SUAVE), new KeyValue(ponto.opacityProperty(), .45)),
                    new KeyFrame(Duration.millis(920)));
            onda.setDelay(Duration.millis(i*130)); onda.setCycleCount(Animation.INDEFINITE); pontos.getChildren().add(onda);
        }
        HBox atividade = new HBox(14, indicador, estado); atividade.setAlignment(Pos.CENTER_LEFT);
        VBox bolha = new VBox(6, nome, atividade); bolha.getStyleClass().add("typing-bubble");
        getChildren().addAll(avatar, bolha); setAccessibleText(usuario + " está digitando");
    }

    void iniciar() { pontos.play(); }
    void parar() { pontos.stop(); }
}
