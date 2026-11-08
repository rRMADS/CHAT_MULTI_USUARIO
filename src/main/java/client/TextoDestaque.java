package client;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.regex.Pattern;

final class TextoDestaque {
    private TextoDestaque() {}

    static Node criar(String conteudo, String termo) {
        if (termo == null || termo.isBlank()) {
            Label label = Componentes.label(conteudo, "message-body");
            label.setWrapText(true);
            label.setMinWidth(0);
            label.setMaxWidth(580);
            return label;
        }
        TextFlow fluxo = new TextFlow();
        fluxo.setMinWidth(0);
        fluxo.setMaxWidth(580);
        fluxo.getStyleClass().add("search-text");
        var matcher =
                Pattern.compile(
                                Pattern.quote(termo),
                                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(conteudo);
        int anterior = 0;
        while (matcher.find()) {
            texto(fluxo, conteudo.substring(anterior, matcher.start()));
            Label destaque = new Label(matcher.group());
            destaque.setWrapText(true);
            destaque.setMinWidth(0);
            destaque.maxWidthProperty().bind(fluxo.widthProperty());
            destaque.getStyleClass().add("search-match");
            fluxo.getChildren().add(destaque);
            anterior = matcher.end();
        }
        texto(fluxo, conteudo.substring(anterior));
        return fluxo;
    }

    private static void texto(TextFlow fluxo, String valor) {
        if (!valor.isEmpty()) {
            Text t = new Text(valor);
            t.getStyleClass().add("search-fragment");
            fluxo.getChildren().add(t);
        }
    }

    static void markdown(javafx.scene.Node raiz, String termo) {
        if (termo == null || termo.isBlank()) return;
        destacar(
                raiz,
                Pattern.compile(
                        Pattern.quote(termo), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    private static void destacar(javafx.scene.Node node, Pattern padrao) {
        if (node instanceof TextFlow fluxo) {
            var novos = new java.util.ArrayList<javafx.scene.Node>();
            for (var filho : fluxo.getChildren()) {
                if (!(filho instanceof Text original)) {
                    novos.add(filho);
                    continue;
                }
                var m = padrao.matcher(original.getText());
                int anterior = 0;
                while (m.find()) {
                    fragmento(
                            novos,
                            original,
                            original.getText().substring(anterior, m.start()),
                            false);
                    fragmento(novos, original, m.group(), true);
                    anterior = m.end();
                }
                fragmento(novos, original, original.getText().substring(anterior), false);
            }
            fluxo.getChildren().setAll(novos);
        } else if (node instanceof javafx.scene.control.TextArea area) {
            var m = padrao.matcher(area.getText());
            if (m.find()) area.selectRange(m.start(), m.end());
        } else if (node instanceof javafx.scene.control.ScrollPane scroll) {
            destacar(scroll.getContent(), padrao);
        } else if (node instanceof javafx.scene.Parent pai) {
            for (var filho : pai.getChildrenUnmodifiable()) destacar(filho, padrao);
        }
    }

    private static void fragmento(
            java.util.List<javafx.scene.Node> destino,
            Text original,
            String valor,
            boolean destaque) {
        if (valor.isEmpty()) return;
        Text texto = new Text(valor);
        texto.getStyleClass().setAll(original.getStyleClass());
        texto.setStyle(original.getStyle());
        if (destaque) {
            texto.getStyleClass().add("md-search-match");
            texto.setUnderline(true);
        }
        destino.add(texto);
    }
}
