package client;

import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.util.Comparator;
import java.util.function.Function;

/** Anima a geometria do Stage; o conteúdo continua cobrindo toda a janela. */
final class DeslizeJanela {
    private static final Interpolator SUAVE=Interpolator.SPLINE(.22,.8,.2,1);
    private final Stage janela;
    private final Runnable antesDeMinimizar;
    private final Function<Rectangle2D,Rectangle2D> areaUtil;
    private final ReadOnlyBooleanWrapper maximizada=new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper animando=new ReadOnlyBooleanWrapper();
    private Movimento movimento;
    private Rectangle2D normal,destino,retomar;
    private boolean minimizando,retornando,ocultaPeloPrograma,encerrando;

    DeslizeJanela(Stage janela,Runnable antesDeMinimizar) { this(janela,antesDeMinimizar,DeslizeJanela::areaDoMonitor); }
    DeslizeJanela(Stage janela,Runnable antesDeMinimizar,Function<Rectangle2D,Rectangle2D> areaUtil) {
        this.janela=janela; this.antesDeMinimizar=antesDeMinimizar; this.areaUtil=areaUtil;
        normal=limites();
        maximizada.set(janela.isMaximized());
        janela.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWN,e->{ if(!maximizada() && !animando()) normal=limites(); });
        janela.iconifiedProperty().addListener((o,a,b)->{ if(!b && ocultaPeloPrograma && !encerrando) reaparecer(); });
        // Mantém os controles coerentes caso o sistema operacional altere o estado nativo.
        janela.maximizedProperty().addListener((o,a,b)->{ if(!encerrando) maximizada.set(b); });
    }
    boolean maximizada() { return maximizada.get(); }
    boolean animando() { return animando.get(); }
    boolean interagivel() { return !encerrando && !minimizando && !ocultaPeloPrograma; }
    ReadOnlyBooleanProperty maximizadaProperty() { return maximizada.getReadOnlyProperty(); }
    ReadOnlyBooleanProperty animandoProperty() { return animando.getReadOnlyProperty(); }

    void alternarTamanho() {
        if(encerrando || minimizando || janela.isIconified()) return;
        Rectangle2D atual=limites(); boolean ampliar=!maximizada();
        if(ampliar && (!animando() || retornando)) normal=retornando && retomar!=null ? retomar : atual;
        Rectangle2D alvo=ampliar ? areaUtil.apply(atual) : normal!=null ? normal : atual;
        interromperInteracao(); liberarEstadoNativo(atual); maximizada.set(ampliar); destino=alvo;
        mover(atual,alvo,janela.getOpacity(),janela.getOpacity(),500,()->{});
    }
    void minimizar() {
        if(encerrando || minimizando || janela.isIconified()) return;
        antesDeMinimizar.run();
        Rectangle2D atual=limites();
        retomar=retornando && retomar!=null ? retomar : animando() && destino!=null ? destino : atual;
        cancelar(); minimizando=true;
        Rectangle2D abaixo=new Rectangle2D(atual.getMinX(),atual.getMinY()+32,atual.getWidth(),atual.getHeight());
        mover(atual,abaixo,janela.getOpacity(),0,240,()->{
            minimizando=false; ocultaPeloPrograma=true;
            janela.setIconified(true); aplicar(retomar);
            // A opacidade permanece zerada enquanto estiver na barra de tarefas.
        });
    }
    private void reaparecer() {
        ocultaPeloPrograma=false;
        Rectangle2D alvo=retomar!=null ? retomar : limites();
        Rectangle2D inicio=new Rectangle2D(alvo.getMinX(),alvo.getMinY()+28,alvo.getWidth(),alvo.getHeight());
        aplicar(inicio); janela.setOpacity(0); retornando=true;
        mover(inicio,alvo,0,1,300,()->retornando=false);
    }
    /** O mouse assume a janela no quadro atual, sem disputar com uma transição pendente. */
    void prepararArraste(double ponteiroX,double ponteiroY,double proporcaoHorizontal) {
        if(encerrando || minimizando) return;
        Rectangle2D atual=limites(); interromperInteracao();
        if(maximizada()) {
            Rectangle2D anterior=normal!=null ? normal : atual;
            Rectangle2D alvo=new Rectangle2D(ponteiroX-anterior.getWidth()*Math.max(0,Math.min(1,proporcaoHorizontal)),ponteiroY-18,anterior.getWidth(),anterior.getHeight());
            liberarEstadoNativo(alvo); maximizada.set(false); aplicar(alvo);
        }
        destino=null;
    }
    void prepararRedimensionamento() {
        if(encerrando || minimizando) return;
        interromperInteracao(); destino=null;
    }
    void encerrar() { encerrando=true; ocultaPeloPrograma=false; cancelar(); }

    private void mover(Rectangle2D inicio,Rectangle2D fim,double opacidadeInicial,double opacidadeFinal,int duracao,Runnable concluido) {
        Movimento novo=new Movimento(inicio,fim,opacidadeInicial,opacidadeFinal,duracao); movimento=novo; animando.set(true);
        novo.setOnFinished(e->{
            if(movimento!=novo || encerrando) return;
            movimento=null; animando.set(false); aplicar(fim); if(opacidadeInicial!=opacidadeFinal) janela.setOpacity(opacidadeFinal); concluido.run();
        }); novo.play();
    }
    private void cancelar() {
        Movimento anterior=movimento; movimento=null;
        if(anterior!=null) anterior.stop(); animando.set(false); retornando=false;
    }
    private void interromperInteracao() { boolean estavaVoltando=retornando; cancelar(); if(estavaVoltando) janela.setOpacity(1); }
    private void liberarEstadoNativo(Rectangle2D atual) {
        if(janela.isMaximized()) { janela.setMaximized(false); aplicar(atual); }
    }
    private Rectangle2D limites() {
        double largura=Double.isFinite(janela.getWidth()) && janela.getWidth()>0 ? janela.getWidth() : 1000;
        double altura=Double.isFinite(janela.getHeight()) && janela.getHeight()>0 ? janela.getHeight() : 700;
        return new Rectangle2D(Double.isFinite(janela.getX()) ? janela.getX() : 0,Double.isFinite(janela.getY()) ? janela.getY() : 0,largura,altura);
    }
    private void aplicar(Rectangle2D r) {
        janela.setX(r.getMinX()); janela.setY(r.getMinY()); janela.setWidth(r.getWidth()); janela.setHeight(r.getHeight());
    }
    private static Rectangle2D areaDoMonitor(Rectangle2D janela) {
        return Screen.getScreens().stream().filter(tela -> intersecao(janela,tela.getBounds())>0).max(Comparator.comparingDouble(tela -> intersecao(janela,tela.getBounds())))
                .orElse(Screen.getPrimary()).getVisualBounds();
    }
    private static double intersecao(Rectangle2D a,Rectangle2D b) {
        return Math.max(0,Math.min(a.getMaxX(),b.getMaxX())-Math.max(a.getMinX(),b.getMinX()))
                *Math.max(0,Math.min(a.getMaxY(),b.getMaxY())-Math.max(a.getMinY(),b.getMinY()));
    }
    private final class Movimento extends Transition {
        private final Rectangle2D inicio,fim; private final double opacidadeInicial,opacidadeFinal;
        Movimento(Rectangle2D inicio,Rectangle2D fim,double opacidadeInicial,double opacidadeFinal,int duracao) {
            this.inicio=inicio; this.fim=fim; this.opacidadeInicial=opacidadeInicial; this.opacidadeFinal=opacidadeFinal;
            setCycleDuration(Duration.millis(duracao)); setInterpolator(SUAVE);
        }
        @Override protected void interpolate(double t) {
            if(movimento!=this || encerrando) return;
            aplicar(new Rectangle2D(entre(inicio.getMinX(),fim.getMinX(),t),entre(inicio.getMinY(),fim.getMinY(),t),
                    entre(inicio.getWidth(),fim.getWidth(),t),entre(inicio.getHeight(),fim.getHeight(),t)));
            if(opacidadeInicial!=opacidadeFinal) janela.setOpacity(entre(opacidadeInicial,opacidadeFinal,t));
        }
        private double entre(double a,double b,double t) { return a+(b-a)*t; }
    }
}
