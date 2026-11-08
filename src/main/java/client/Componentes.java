package client;

import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Region;

import org.kordamp.ikonli.feather.Feather;

/** Elementos visuais compartilhados, com as mesmas cores e interações em todas as telas. */
final class Componentes {
    private Componentes() {}

    static Label label(String texto, String estilo) {
        Label label = new Label(texto);
        label.getStyleClass().add(estilo);
        return label;
    }

    static Button botao(Animacoes animacoes, String texto, String estilo) {
        Button botao = new Button(texto);
        botao.getStyleClass().add(estilo);
        botao.setCursor(Cursor.HAND);
        animacoes.prepararBotao(botao);
        return botao;
    }

    static Button botao(Animacoes animacoes, String texto, String estilo, Feather icone) {
        Button botao = botao(animacoes, texto, estilo);
        botao.setGraphic(Icones.criar(icone, 18));
        botao.setGraphicTextGap(9);
        return botao;
    }

    static Label marca() {
        Label marca = label("conexo", "brand");
        marca.setGraphic(Icones.criar(Feather.MESSAGE_CIRCLE, 30));
        marca.setGraphicTextGap(12);
        return marca;
    }

    static Region espaco(double altura) {
        Region espaco = new Region();
        espaco.setMinHeight(altura);
        return espaco;
    }

    static void copiarTexto(String texto) {
        ClipboardContent conteudo = new ClipboardContent();
        conteudo.putString(texto);
        Clipboard.getSystemClipboard().setContent(conteudo);
    }
}
