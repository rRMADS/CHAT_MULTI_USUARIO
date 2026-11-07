package client;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;

/** A janela pode ser menor que o conteúdo: os controles continuam acessíveis por rolagem. */
final class ViewportAdaptavel extends ScrollPane {
    ViewportAdaptavel(Region conteudo,double larguraMinima,double alturaMinima) {
        super(conteudo); setFitToWidth(true); setFitToHeight(true); setMinSize(0,0); setPrefSize(1280,850);
        conteudo.setMinSize(larguraMinima,alturaMinima); conteudo.setMaxSize(Double.MAX_VALUE,Double.MAX_VALUE);
        getStyleClass().add("adaptive-viewport"); setPannable(false);
    }
}
