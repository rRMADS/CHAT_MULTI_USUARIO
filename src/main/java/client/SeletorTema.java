package client;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import java.util.EnumMap;

/** Três opções visíveis, navegáveis por teclado, com um destaque deslizante. */
final class SeletorTema extends StackPane {
    private final Region destaque=new Region();
    private final HBox opcoes=new HBox(3);
    private final EnumMap<Temas.Tema,ToggleButton> botoes=new EnumMap<>(Temas.Tema.class);
    private Temas.Tema selecionado;
    private Timeline movimento;
    private double ultimaLargura=-1;

    SeletorTema(Temas temas,boolean lateral) {
        getStyleClass().add("theme-chooser"); if(lateral) getStyleClass().add("theme-chooser-side");
        setMinWidth(0); setPrefHeight(72); setMaxWidth(Double.MAX_VALUE);
        destaque.getStyleClass().add("theme-highlight"); destaque.setManaged(false); destaque.setMouseTransparent(true);
        opcoes.setAlignment(Pos.CENTER); ToggleGroup grupo=new ToggleGroup();
        for(var tema:Temas.Tema.values()) {
            ToggleButton b=new ToggleButton(tema.toString()); b.setToggleGroup(grupo); b.setContentDisplay(ContentDisplay.TOP);
            b.setGraphic(Icones.criar(switch(tema) { case ROXO -> Feather.STAR; case CLARO -> Feather.SUN; case ESCURO -> Feather.MOON; },19));
            b.getStyleClass().add("theme-option"); b.setId("tema-"+tema.name().toLowerCase()); b.setAccessibleText("Tema "+tema);
            b.setMinWidth(0); b.setPrefWidth(100); b.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(b,Priority.ALWAYS);
            b.setOnAction(e -> { if(tema!=temas.atual()) temas.selecionar(tema); else atualizar(tema,false); });
            var icone=b.getGraphic(); var hover=new ScaleTransition(Duration.millis(180),icone); hover.setInterpolator(Animacoes.SUAVE);
            b.setOnMouseEntered(e -> { hover.stop(); hover.setToX(1.15); hover.setToY(1.15); hover.play(); });
            b.setOnMouseExited(e -> { hover.stop(); hover.setToX(1); hover.setToY(1); hover.play(); });
            b.sceneProperty().addListener((o,a,n) -> { if(n==null) hover.stop(); });
            botoes.put(tema,b); opcoes.getChildren().add(b);
        }
        getChildren().addAll(destaque,opcoes); atualizar(temas.atual(),false);
        sceneProperty().addListener((o,a,n) -> { if(n==null && movimento!=null) movimento.stop(); });
    }
    void atualizar(Temas.Tema tema,boolean animar) {
        selecionado=tema; botoes.forEach((t,b)->b.setSelected(t==tema));
        if(getWidth()>0) { applyCss(); opcoes.layout(); posicionar(animar); } else requestLayout();
    }
    private void posicionar(boolean animar) {
        if(movimento!=null) movimento.stop();
        ToggleButton b=botoes.get(selecionado); double x=opcoes.getLayoutX()+b.getLayoutX();
        destaque.resizeRelocate(0,opcoes.getLayoutY(),b.getWidth(),opcoes.getHeight());
        if(animar) {
            movimento=new Timeline(new KeyFrame(Duration.millis(280),new KeyValue(destaque.translateXProperty(),x,Animacoes.SUAVE)));
            movimento.play();
        } else destaque.setTranslateX(x);
    }
    @Override protected void layoutChildren() {
        super.layoutChildren();
        if(Math.abs(getWidth()-ultimaLargura)>.1) { ultimaLargura=getWidth(); opcoes.layout(); posicionar(false); }
    }
}
