package client;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.util.function.DoubleConsumer;

/** Recolhe a lateral em janelas estreitas, preservando os mesmos controles e a conexão. */
final class TelaChatAdaptavel extends StackPane {
    private final BorderPane base;
    private final ScrollPane lateral;
    private final Region sombra=new Region();
    private final Button abrir;
    private final DoubleConsumer painel;
    private boolean compacto,aberto;
    private TranslateTransition movimento;
    TelaChatAdaptavel(BorderPane base,VBox conteudoLateral,Button abrir,DoubleConsumer painel) {
        this.base=base; this.abrir=abrir; this.painel=painel;
        lateral=new ScrollPane(conteudoLateral); lateral.setFitToWidth(true); lateral.setFitToHeight(true); lateral.setMinWidth(0); lateral.setPrefWidth(278); lateral.setMaxWidth(278);
        conteudoLateral.setMinWidth(0); conteudoLateral.setMinHeight(Region.USE_PREF_SIZE); lateral.getStyleClass().add("sidebar-scroll");
        getStyleClass().add("chat-shell"); setMinSize(300,360); base.setMinSize(0,0); sombra.getStyleClass().add("drawer-shade"); sombra.setOnMouseClicked(e -> fechar());
        getChildren().add(base); base.setRight(lateral); abrir.setOnAction(e -> { if(aberto) fechar(); else mostrar(); });
        abrir.setAccessibleText("Abrir salas e participantes");
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,e -> { if(aberto && e.getCode()==javafx.scene.input.KeyCode.ESCAPE) { fechar(); e.consume(); } });
        widthProperty().addListener((o,a,b)->adaptar()); adaptar();
    }
    private void adaptar() {
        boolean menor=getWidth()>0 && getWidth()<880;
        if(menor!=compacto) {
            compacto=menor; fechar(); base.setRight(null); getChildren().remove(lateral);
            if(compacto) { lateral.setVisible(false); StackPane.setAlignment(lateral,Pos.CENTER_RIGHT); getChildren().add(lateral); }
            else { lateral.setVisible(true); lateral.setTranslateX(0); base.setRight(lateral); }
        }
        abrir.setVisible(compacto); abrir.setManaged(compacto); painel.accept(compacto ? 0 : 278);
        pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("compact"),getWidth()<620);
    }
    private void mostrar() {
        if(!compacto) return; if(movimento!=null) movimento.stop(); aberto=true;
        if(!getChildren().contains(sombra)) getChildren().add(getChildren().indexOf(lateral),sombra);
        lateral.setVisible(true); lateral.setTranslateX(lateral.getWidth());
        movimento=new TranslateTransition(Duration.millis(240),lateral); movimento.setToX(0); movimento.setInterpolator(Animacoes.SUAVE); movimento.play();
    }
    private void fechar() {
        boolean tinhaAberto=aberto; aberto=false; if(movimento!=null) movimento.stop(); getChildren().remove(sombra);
        if(compacto) lateral.setVisible(false); lateral.setTranslateX(0);
        if(tinhaAberto) abrir.requestFocus();
    }
}
