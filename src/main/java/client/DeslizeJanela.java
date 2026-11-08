package client;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/** Maximização nativa; minimização com deslize curto e fade, sem alterar as dimensões. */
final class DeslizeJanela {
    private final Stage janela;
    private final Node conteudo;
    private final Runnable antesDeMinimizar;
    private final ReadOnlyBooleanWrapper maximizada = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper animando = new ReadOnlyBooleanWrapper();
    private FadeTransition efeito;
    private AnimationTimer quadro;
    private Rectangle2D normal;
    private boolean encerrando;
    private MiniMovimento mini;
    private boolean minimizando, retornando, ocultaPeloApp, moverAoMinimizar;
    private double yRetorno;

    DeslizeJanela(Stage janela, Runnable antesDeMinimizar) {
        this(janela, null, antesDeMinimizar);
    }

    DeslizeJanela(Stage janela, Node conteudo, Runnable antesDeMinimizar) {
        this.janela = janela;
        this.conteudo = conteudo;
        this.antesDeMinimizar = antesDeMinimizar;
        maximizada.set(janela.isMaximized());
        janela.addEventHandler(
                WindowEvent.WINDOW_SHOWN,
                e -> {
                    if (!janela.isMaximized()) guardarTamanhoNormal();
                });
        janela.maximizedProperty()
                .addListener((o, a, b) -> {
                    maximizada.set(b);
                    revelarDepoisDoLayout();
                });
        janela.iconifiedProperty()
                .addListener((o, a, b) -> {
                    cancelar();
                    if (encerrando) return;
                    if (b) {
                        if (minimizando) {
                            interromperMini(false);
                            ocultaPeloApp = true;
                            janela.setOpacity(0);
                            if (moverAoMinimizar && !janela.isMaximized()) janela.setY(yRetorno);
                        }
                        if (!ocultaPeloApp) {
                            antesDeMinimizar.run();
                            janela.setOpacity(1);
                        }
                    } else if (ocultaPeloApp) {
                        ocultaPeloApp = false;
                        reaparecer();
                    } else {
                        janela.setOpacity(1);
                        revelarDepoisDoLayout();
                    }
                });
    }

    boolean maximizada() {
        return janela.isMaximized();
    }

    boolean animando() {
        return animando.get();
    }

    boolean interagivel() {
        return !encerrando && !minimizando && !janela.isIconified();
    }

    ReadOnlyBooleanProperty maximizadaProperty() {
        return maximizada.getReadOnlyProperty();
    }

    ReadOnlyBooleanProperty animandoProperty() {
        return animando.getReadOnlyProperty();
    }

    void alternarTamanho() {
        if (!interagivel()) return;
        interromperMini(true);
        if (!janela.isMaximized()) guardarTamanhoNormal();
        cancelar();
        // Uma única solicitação nativa, sem reposicionar/redimensionar o Stage a cada quadro.
        janela.setMaximized(!janela.isMaximized());
    }

    void minimizar() {
        if (!interagivel()) return;
        double inicioY = janela.getY(), opacidade = janela.getOpacity();
        double base = retornando ? yRetorno : inicioY;
        antesDeMinimizar.run();
        cancelar();
        interromperMini(false);
        yRetorno = base;
        moverAoMinimizar = !janela.isMaximized() && Double.isFinite(inicioY);
        minimizando = true;
        animarMini(
                inicioY,
                inicioY + 32,
                opacidade,
                0,
                moverAoMinimizar,
                240,
                () -> {
                    minimizando = false;
                    ocultaPeloApp = true;
                    janela.setIconified(true);
                    if (moverAoMinimizar && !janela.isMaximized()) janela.setY(yRetorno);
                });
    }

    private void reaparecer() {
        yRetorno = janela.getY();
        retornando = true;
        boolean mover = !janela.isMaximized() && Double.isFinite(yRetorno);
        if (mover) janela.setY(yRetorno + 28);
        janela.setOpacity(0);
        animarMini(yRetorno + 28, yRetorno, 0, 1, mover, 300, () -> retornando = false);
    }

    void prepararArraste(double ponteiroX, double ponteiroY, double proporcaoHorizontal) {
        if (!interagivel()) return;
        cancelar();
        interromperMini(true);
        if (janela.isMaximized()) {
            janela.setMaximized(false);
            cancelar();
            // Reposiciona somente por interação direta do mouse, uma vez ao iniciar o arraste.
            if (normal != null) {
                janela.setWidth(normal.getWidth());
                janela.setHeight(normal.getHeight());
                janela.setX(
                        ponteiroX
                                - normal.getWidth()
                                        * Math.max(0, Math.min(1, proporcaoHorizontal)));
                janela.setY(ponteiroY - 18);
            }
        }
    }

    void prepararRedimensionamento() {
        cancelar();
        interromperMini(true);
    }

    void encerrar() {
        encerrando = true;
        ocultaPeloApp = false;
        cancelar();
        interromperMini(false);
    }

    private Node alvo() {
        return conteudo != null
                ? conteudo
                : janela.getScene() != null ? janela.getScene().getRoot() : null;
    }

    private void guardarTamanhoNormal() {
        if (Double.isFinite(janela.getX())
                && Double.isFinite(janela.getY())
                && janela.getWidth() > 0
                && janela.getHeight() > 0)
            normal =
                    new Rectangle2D(
                            janela.getX(), janela.getY(), janela.getWidth(), janela.getHeight());
    }

    private void revelarDepoisDoLayout() {
        cancelar();
        if (encerrando || janela.isIconified() || minimizando || ocultaPeloApp) return;
        if (retornando) interromperMini(true);
        animando.set(true);
        quadro =
                new AnimationTimer() {
                    private int pulsos;

                    @Override
                    public void handle(long agora) {
                        if (quadro != this || encerrando || janela.isIconified()) {
                            stop();
                            return;
                        }
                        if (++pulsos < 2) return;
                        stop();
                        quadro = null;
                        Node no = alvo();
                        if (no == null) {
                            animando.set(false);
                            return;
                        }
                        // Não translada nem escala a raiz: evita faixas, tremores e letras
                        // esticadas.
                        efeito = new FadeTransition(Duration.millis(160), no);
                        efeito.setFromValue(.96);
                        efeito.setToValue(1);
                        efeito.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
                        efeito.setOnFinished(e -> {
                            efeito = null;
                            animando.set(false);
                        });
                        efeito.play();
                    }
                };
        quadro.start();
    }

    private void cancelar() {
        if (quadro != null) {
            quadro.stop();
            quadro = null;
        }
        if (efeito != null) {
            efeito.stop();
            efeito = null;
        }
        Node no = alvo();
        if (no != null) no.setOpacity(1);
        animando.set(false);
    }

    private void interromperMini(boolean normalizar) {
        MiniMovimento anterior = mini;
        mini = null;
        boolean voltando = retornando;
        retornando = false;
        minimizando = false;
        if (anterior != null) anterior.stop();
        if (normalizar && voltando) {
            if (!janela.isMaximized() && Double.isFinite(yRetorno)) janela.setY(yRetorno);
            janela.setOpacity(1);
        }
        if (anterior != null) animando.set(false);
    }

    private void animarMini(
            double inicioY,
            double fimY,
            double inicioAlpha,
            double fimAlpha,
            boolean mover,
            int ms,
            Runnable fim) {
        MiniMovimento novo = new MiniMovimento(inicioY, fimY, inicioAlpha, fimAlpha, mover, ms);
        mini = novo;
        animando.set(true);
        novo.setOnFinished(e -> {
            if (mini != novo || encerrando) return;
            mini = null;
            animando.set(false);
            if (mover && !janela.isMaximized()) janela.setY(fimY);
            janela.setOpacity(fimAlpha);
            fim.run();
        });
        novo.play();
    }

    private final class MiniMovimento extends Transition {
        private final double inicioY, fimY, inicioAlpha, fimAlpha;
        private final boolean mover;

        MiniMovimento(
                double inicioY,
                double fimY,
                double inicioAlpha,
                double fimAlpha,
                boolean mover,
                int ms) {
            this.inicioY = inicioY;
            this.fimY = fimY;
            this.inicioAlpha = inicioAlpha;
            this.fimAlpha = fimAlpha;
            this.mover = mover;
            setCycleDuration(Duration.millis(ms));
            setInterpolator(Interpolator.SPLINE(.22, .8, .2, 1));
        }

        @Override
        protected void interpolate(double t) {
            if (mini != this || encerrando) return;
            if (mover && !janela.isMaximized()) janela.setY(inicioY + (fimY - inicioY) * t);
            janela.setOpacity(inicioAlpha + (fimAlpha - inicioAlpha) * t);
        }
    }
}
