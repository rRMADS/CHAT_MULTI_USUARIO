package client;

import animatefx.animation.FadeIn;
import animatefx.animation.FadeOut;
import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Efeitos visuais executados exclusivamente na thread do JavaFX. */
final class Animacoes {
    static final Interpolator SUAVE = Interpolator.SPLINE(.2, .8, .2, 1);
    private final Set<Animation> ativos = Collections.newSetFromMap(new IdentityHashMap<>());
    // Controles removidos da lista de participantes não ficam retidos até a próxima sala.
    private final Set<Animation> interacoes = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Node, Animation> entradas = new IdentityHashMap<>();
    private Animation troca;
    private Stage janelaAbrindo;
    private Animation abertura;

    void prepararTela() {
        ativos.forEach(Animation::stop);
        ativos.clear(); entradas.clear();
        interacoes.forEach(Animation::stop); interacoes.clear();
        if (abertura != null) { abertura.stop(); janelaAbrindo.setOpacity(1); abertura = null; janelaAbrindo = null; }
        if (troca != null) troca.stop();
        troca = null;
    }

    void trocar(StackPane palco, Parent tela) {
        if (troca != null) troca.stop();
        palco.getChildren().setAll(tela);
        troca = entrada(tela, 0, 6, 0);
        troca.play();
    }

    void aparecer(Node node, double x, double y, double atraso) {
        Animation anterior = entradas.remove(node);
        if (anterior != null) { anterior.stop(); ativos.remove(anterior); }
        var animacao = entrada(node, x, y, atraso);
        entradas.put(node, animacao);
        animacao.setOnFinished(e -> { ativos.remove(animacao); entradas.remove(node, animacao); });
        ativos.add(animacao); animacao.play();
    }

    void sumir(Node node, Runnable concluido) {
        Animation anterior = entradas.remove(node);
        if (anterior != null) { anterior.stop(); ativos.remove(anterior); }
        var fade = new FadeTransition(Duration.millis(130), node); fade.setToValue(0);
        entradas.put(node, fade); ativos.add(fade);
        fade.setOnFinished(e -> { ativos.remove(fade); entradas.remove(node, fade); concluido.run(); }); fade.play();
    }

    private ParallelTransition entrada(Node node, double x, double y, double atraso) {
        var fade = new FadeIn(node).setSpeed(1000.0 / 190).getTimeline();
        node.setOpacity(0);
        var mover = new TranslateTransition(Duration.millis(250), node);
        node.setTranslateX(x); node.setTranslateY(y);
        mover.setFromX(x); mover.setToX(0); mover.setFromY(y); mover.setToY(0);
        mover.setInterpolator(SUAVE);
        var transicao = new ParallelTransition(fade, mover);
        transicao.setDelay(Duration.millis(atraso));
        return transicao;
    }

    void flutuar(Node node, double distancia, double segundos, double atraso) {
        var mover = new TranslateTransition(Duration.seconds(segundos), node);
        mover.setFromY(0); mover.setToY(distancia); mover.setAutoReverse(true);
        mover.setCycleCount(Animation.INDEFINITE); mover.setInterpolator(Interpolator.EASE_BOTH);
        mover.setDelay(Duration.millis(atraso)); ativos.add(mover); mover.play();
    }

    Animation pulsar(Node node) {
        var fade = new FadeTransition(Duration.seconds(2.2), node);
        fade.setFromValue(.72); fade.setToValue(1); fade.setAutoReverse(true);
        fade.setCycleCount(Animation.INDEFINITE); ativos.add(fade); fade.play(); return fade;
    }

    void abrirJanela(Stage janela) {
        janela.setOpacity(0);
        janelaAbrindo = janela;
        abertura = new Timeline(new KeyFrame(Duration.millis(220), new KeyValue(janela.opacityProperty(), 1, Interpolator.EASE_OUT)));
        abertura.setOnFinished(e -> { abertura = null; janelaAbrindo = null; }); abertura.play();
    }

    void fechar(Node raiz, Runnable concluido) {
        var fade = new FadeOut(raiz).setSpeed(5).getTimeline();
        var escala = new ScaleTransition(Duration.millis(200), raiz);
        escala.setToX(.98); escala.setToY(.98); escala.setInterpolator(Interpolator.EASE_IN);
        var saida = new ParallelTransition(fade, escala);
        saida.setOnFinished(e -> { ativos.remove(saida); concluido.run(); });
        ativos.add(saida); saida.play();
    }

    void envio(Node icone) {
        Animation anterior = entradas.remove(icone);
        if (anterior != null) { anterior.stop(); ativos.remove(anterior); }
        var voo = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(icone.translateXProperty(), 0), new KeyValue(icone.translateYProperty(), 0), new KeyValue(icone.opacityProperty(), 1)),
            new KeyFrame(Duration.millis(100), new KeyValue(icone.translateXProperty(), 7), new KeyValue(icone.translateYProperty(), -7), new KeyValue(icone.opacityProperty(), 0)),
            new KeyFrame(Duration.millis(101), new KeyValue(icone.translateXProperty(), -4), new KeyValue(icone.translateYProperty(), 4), new KeyValue(icone.opacityProperty(), 0)),
            new KeyFrame(Duration.millis(280), new KeyValue(icone.translateXProperty(), 0, Interpolator.EASE_OUT), new KeyValue(icone.translateYProperty(), 0, Interpolator.EASE_OUT), new KeyValue(icone.opacityProperty(), 1)));
        entradas.put(icone, voo);
        voo.setOnFinished(e -> { ativos.remove(voo); entradas.remove(icone, voo); });
        ativos.add(voo); voo.play();
    }

    void prepararBotao(Button botao) {
        var escala = new ScaleTransition(Duration.millis(140), botao);
        interacoes.add(escala);
        escala.setInterpolator(SUAVE);
        botao.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> ajustar(botao, escala, 1));
        botao.addEventHandler(MouseEvent.MOUSE_EXITED, e -> ajustar(botao, escala, 1));
        botao.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> ajustar(botao, escala, .985));
        botao.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> ajustar(botao, escala, 1));
    }

    private void ajustar(Button botao, ScaleTransition escala, double tamanho) {
        escala.stop();
        escala.setFromX(botao.getScaleX()); escala.setFromY(botao.getScaleY());
        escala.setToX(tamanho); escala.setToY(tamanho);
        escala.play();
    }
}
