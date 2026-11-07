package client;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/** O Windows controla a geometria; a interface recebe apenas um acabamento visual discreto. */
final class DeslizeJanela {
    private final Stage janela;
    private final Node conteudo;
    private final Runnable antesDeMinimizar;
    private final ReadOnlyBooleanWrapper maximizada=new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper animando=new ReadOnlyBooleanWrapper();
    private FadeTransition efeito;
    private AnimationTimer quadro;
    private Rectangle2D normal;
    private boolean encerrando;

    DeslizeJanela(Stage janela,Runnable antesDeMinimizar) { this(janela,null,antesDeMinimizar); }
    DeslizeJanela(Stage janela,Node conteudo,Runnable antesDeMinimizar) {
        this.janela=janela; this.conteudo=conteudo; this.antesDeMinimizar=antesDeMinimizar;
        maximizada.set(janela.isMaximized());
        janela.addEventHandler(WindowEvent.WINDOW_SHOWN,e->{ if(!janela.isMaximized()) guardarTamanhoNormal(); });
        janela.maximizedProperty().addListener((o,a,b)->{ maximizada.set(b); revelarDepoisDoLayout(); });
        janela.iconifiedProperty().addListener((o,a,b)->{
            cancelar();
            if(!b && !encerrando) { janela.setOpacity(1); revelarDepoisDoLayout(); }
        });
    }
    boolean maximizada() { return janela.isMaximized(); }
    boolean animando() { return animando.get(); }
    boolean interagivel() { return !encerrando && !janela.isIconified(); }
    ReadOnlyBooleanProperty maximizadaProperty() { return maximizada.getReadOnlyProperty(); }
    ReadOnlyBooleanProperty animandoProperty() { return animando.getReadOnlyProperty(); }

    void alternarTamanho() {
        if(!interagivel()) return;
        if(!janela.isMaximized()) guardarTamanhoNormal();
        cancelar();
        // Uma única solicitação nativa, sem reposicionar/redimensionar o Stage a cada quadro.
        janela.setMaximized(!janela.isMaximized());
    }
    void minimizar() {
        if(!interagivel()) return;
        antesDeMinimizar.run(); cancelar();
        janela.setOpacity(1); janela.setIconified(true);
    }
    void prepararArraste(double ponteiroX,double ponteiroY,double proporcaoHorizontal) {
        if(!interagivel()) return;
        cancelar();
        if(janela.isMaximized()) {
            janela.setMaximized(false); cancelar();
            // Reposiciona somente por interação direta do mouse, uma vez ao iniciar o arraste.
            if(normal!=null) {
                janela.setWidth(normal.getWidth()); janela.setHeight(normal.getHeight());
                janela.setX(ponteiroX-normal.getWidth()*Math.max(0,Math.min(1,proporcaoHorizontal))); janela.setY(ponteiroY-18);
            }
        }
    }
    void prepararRedimensionamento() { cancelar(); }
    void encerrar() { encerrando=true; cancelar(); }

    private Node alvo() { return conteudo!=null ? conteudo : janela.getScene()!=null ? janela.getScene().getRoot() : null; }
    private void guardarTamanhoNormal() {
        if(Double.isFinite(janela.getX()) && Double.isFinite(janela.getY()) && janela.getWidth()>0 && janela.getHeight()>0)
            normal=new Rectangle2D(janela.getX(),janela.getY(),janela.getWidth(),janela.getHeight());
    }
    private void revelarDepoisDoLayout() {
        cancelar();
        if(encerrando || janela.isIconified()) return;
        animando.set(true);
        quadro=new AnimationTimer() {
            private int pulsos;
            @Override public void handle(long agora) {
                if(quadro!=this || encerrando || janela.isIconified()) { stop(); return; }
                if(++pulsos<2) return;
                stop(); quadro=null;
                Node no=alvo();
                if(no==null) { animando.set(false); return; }
                // Não translada nem escala a raiz: evita faixas, tremores e letras esticadas.
                efeito=new FadeTransition(Duration.millis(160),no); efeito.setFromValue(.96); efeito.setToValue(1);
                efeito.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
                efeito.setOnFinished(e->{efeito=null; animando.set(false);}); efeito.play();
            }
        }; quadro.start();
    }
    private void cancelar() {
        if(quadro!=null) { quadro.stop(); quadro=null; }
        if(efeito!=null) { efeito.stop(); efeito=null; }
        Node no=alvo(); if(no!=null) no.setOpacity(1);
        animando.set(false);
    }
}
