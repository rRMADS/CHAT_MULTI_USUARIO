package client;

import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Ícones vetoriais com tamanho consistente e cores definidas pelo tema. */
final class Icones {
    private Icones() { }

    static FontIcon criar(Feather desenho, int tamanho) {
        FontIcon icone = new FontIcon(desenho);
        icone.setIconSize(tamanho);
        // Preserva a família de fonte que o Ikonli já colocou no estilo inline.
        icone.setStyle(icone.getStyle() + "; -fx-icon-size: " + tamanho + "px;");
        icone.setMouseTransparent(true);
        icone.getStyleClass().add("app-icon");
        return icone;
    }
}
