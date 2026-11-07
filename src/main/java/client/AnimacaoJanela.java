package client;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Anima a janela inteira sem alterar seus limites nem competir com as trocas de sala. */
final class AnimacaoJanela {
    private static final Interpolator ABRIR = Interpolator.SPLINE(.4, 0, .2, 1);
    private static final Interpolator FECHAR = Interpolator.SPLINE(.4, 0, .6, 1);
    private Stage janela;
    private Node conteudo;
    private Timeline transicao;
    private AnimationTimer primeiroQuadro;
    private boolean encerrando;

    void preparar(Stage janela, Node conteudo) {
        parar(); this.janela = janela; this.conteudo = conteudo; encerrando = false;
        // Executado antes de show(): não deixa um quadro opaco piscar na abertura.
        janela.setOpacity(0); conteudo.setTranslateY(0);
    }

    void abrir() {
        if (encerrando || janela == null) return;
        if (primeiroQuadro != null) primeiroQuadro.stop();
        primeiroQuadro = new AnimationTimer() {
            @Override public void handle(long agora) {
                stop(); primeiroQuadro = null;
                if (encerrando) return;
                transicao = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(janela.opacityProperty(), janela.getOpacity())),
                        new KeyFrame(Duration.millis(850), new KeyValue(janela.opacityProperty(), 1, ABRIR)));
                transicao.setOnFinished(e -> transicao = null); transicao.play();
            }
        };
        // O primeiro pulso permite que CSS, fontes e layout estejam prontos para a revelação.
        primeiroQuadro.start();
    }

    void fechar(Runnable concluido) {
        if (encerrando) return;
        encerrando = true; parar();
        if (janela == null) { concluido.run(); return; }
        // Parte dos valores atuais, inclusive se o usuário fechar durante a abertura.
        transicao = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(janela.opacityProperty(), janela.getOpacity())),
                new KeyFrame(Duration.millis(300), new KeyValue(janela.opacityProperty(), 0, FECHAR)));
        transicao.setOnFinished(e -> { transicao = null; concluido.run(); }); transicao.play();
    }

    void parar() {
        if (primeiroQuadro != null) { primeiroQuadro.stop(); primeiroQuadro = null; }
        if (transicao != null) { transicao.stop(); transicao = null; }
    }
}
