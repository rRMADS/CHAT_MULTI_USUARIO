package client;

import static client.Componentes.label;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;

/** A prévia exige uma escolha explícita de alcance antes de enviar os trechos. */
final class ResumoSelecaoDialog extends Dialog<String> {
    ResumoSelecaoDialog(
            Window janela,
            Temas temas,
            String salaAtualNome,
            List<RecursosConversa.Selecionada> selecionadas) {
        if (selecionadas.isEmpty() || selecionadas.size() > 10)
            throw new IllegalArgumentException("Selecione de 1 a 10 mensagens.");
        initOwner(janela);
        setTitle("Resumir mensagens selecionadas");
        setResizable(true);
        var p = getDialogPane();
        temas.registrar(p);
        p.getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm());
        Label explicacao =
                label(
                        "Somente os textos abaixo serão enviados à IA. Anexos não são lidos. Mensagens longas usam os primeiros 1.000 caracteres.",
                        "muted");
        explicacao.setWrapText(true);
        VBox lista = new VBox(10);
        for (var m : selecionadas) {
            String texto =
                    m.texto().length() > 1000
                            ? m.texto().substring(0, 1000) + "… [trecho abreviado]"
                            : m.texto();
            Label t = new Label(texto);
            t.setWrapText(true);
            t.setMaxWidth(470);
            VBox caixa = new VBox(5, label(m.autor() + " · #" + m.id(), "message-author"), t);
            caixa.getStyleClass().add("message-quote");
            lista.getChildren().add(caixa);
        }
        ScrollPane scroll = new ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(
                Math.min(
                        300, javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() - 290));
        ComboBox<String> alcance = new ComboBox<>();
        alcance.getItems().addAll("Privado · só você", "Na sala · " + salaAtualNome);
        alcance.getSelectionModel().selectFirst();
        alcance.setMaxWidth(Double.MAX_VALUE);
        VBox conteudo =
                new VBox(12, explicacao, scroll, new Label("Onde exibir o resumo?"), alcance);
        conteudo.setPrefWidth(490);
        p.setContent(conteudo);
        ButtonType pedir = new ButtonType("Gerar resumo", ButtonBar.ButtonData.OK_DONE);
        p.getButtonTypes().addAll(ButtonType.CANCEL, pedir);
        setResultConverter(b ->
                b == pedir
                        ? (alcance.getSelectionModel().getSelectedIndex() == 0 ? "privado" : "sala")
                        : null);
    }
}
