package client;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;

/** Efeitos visuais executados exclusivamente na thread do JavaFX. */
final class Animacoes {
    private final List<Animation> ciclos = new ArrayList<>();
    private Animation troca;

    void prepararTela() {
        ciclos.forEach(Animation::stop);
        ciclos.clear();
        if (troca != null) troca.stop();
    }

    void trocar(StackPane palco, Parent tela) {
        Node anterior = palco.getChildren().isEmpty() ? null : palco.getChildren().get(palco.getChildren().size() - 1);
        palco.getChildren().setAll(tela);
        var entrada = entrada(tela, 0, 12, 0);
        if (anterior != null) {
            anterior.setMouseTransparent(true);
            palco.getChildren().add(0, anterior);
            var saida = new FadeTransition(Duration.millis(160), anterior);
            saida.setToValue(0);
            var transicao = new ParallelTransition(saida, entrada);
            transicao.setOnFinished(e -> palco.getChildren().remove(anterior));
            troca = transicao;
        } else troca = entrada;
        troca.play();
    }

    void aparecer(Node node, double x, double y, double atraso) { entrada(node, x, y, atraso).play(); }

    private ParallelTransition entrada(Node node, double x, double y, double atraso) {
        var fade = new FadeTransition(Duration.millis(280), node);
        node.setOpacity(0); fade.setFromValue(0); fade.setToValue(1);
        var mover = new TranslateTransition(Duration.millis(320), node);
        node.setTranslateX(x); node.setTranslateY(y);
        mover.setFromX(x); mover.setToX(0); mover.setFromY(y); mover.setToY(0);
        mover.setInterpolator(Interpolator.EASE_OUT);
        var transicao = new ParallelTransition(fade, mover);
        transicao.setDelay(Duration.millis(atraso));
        return transicao;
    }

    void flutuar(Node node, double distancia, double segundos, double atraso) {
        var mover = new TranslateTransition(Duration.seconds(segundos), node);
        mover.setFromY(0); mover.setToY(distancia); mover.setAutoReverse(true);
        mover.setCycleCount(Animation.INDEFINITE); mover.setInterpolator(Interpolator.EASE_BOTH);
        mover.setDelay(Duration.millis(atraso)); ciclos.add(mover); mover.play();
    }

    Animation pulsar(Node node) {
        var fade = new FadeTransition(Duration.seconds(1.7), node);
        fade.setFromValue(.45); fade.setToValue(1); fade.setAutoReverse(true);
        fade.setCycleCount(Animation.INDEFINITE); ciclos.add(fade); fade.play(); return fade;
    }

    void prepararBotao(Button botao) {
        var escala = new ScaleTransition(Duration.millis(140), botao);
        var mover = new TranslateTransition(Duration.millis(140), botao);
        botao.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> ajustar(botao, escala, mover, 1.015, -1.5));
        botao.addEventHandler(MouseEvent.MOUSE_EXITED, e -> ajustar(botao, escala, mover, 1, 0));
        botao.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> ajustar(botao, escala, mover, .98, 0));
        botao.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> ajustar(botao, escala, mover, botao.isHover() ? 1.015 : 1, botao.isHover() ? -1.5 : 0));
    }

    private void ajustar(Button botao, ScaleTransition escala, TranslateTransition mover, double tamanho, double y) {
        escala.stop(); mover.stop();
        escala.setFromX(botao.getScaleX()); escala.setFromY(botao.getScaleY());
        escala.setToX(tamanho); escala.setToY(tamanho);
        mover.setFromY(botao.getTranslateY()); mover.setToY(y); escala.play(); mover.play();
    }
}
