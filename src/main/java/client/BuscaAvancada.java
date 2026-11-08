package client;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import model.FiltroBusca;

import java.util.List;

final class BuscaAvancada extends Dialog<FiltroBusca> {
    BuscaAvancada(Window dono, Temas temas, String termo, List<String> pessoas) {
        initOwner(dono);
        setTitle("Buscar mensagens");
        setResizable(true);
        var painel = getDialogPane();
        painel.getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm());
        temas.registrar(painel);
        TextField texto = new TextField(termo);
        texto.setPromptText("Palavra ou trecho (opcional)");
        texto.setId("filtro-texto");
        ComboBox<String> autor = new ComboBox<>();
        autor.setEditable(true);
        autor.getItems().addAll(pessoas);
        autor.setPromptText("Qualquer participante ou apelido antigo");
        autor.setMaxWidth(Double.MAX_VALUE);
        autor.setId("filtro-autor");
        DatePicker inicio = new DatePicker(), fim = new DatePicker();
        inicio.setEditable(false);
        fim.setEditable(false);
        inicio.setMaxWidth(Double.MAX_VALUE);
        fim.setMaxWidth(Double.MAX_VALUE);
        Button limparDatas = new Button("Limpar datas");
        limparDatas.setOnAction(e -> {
            inicio.setValue(null);
            fim.setValue(null);
        });
        Label erro = new Label();
        erro.getStyleClass().add("error");
        erro.setWrapText(true);
        VBox conteudo =
                new VBox(
                        10,
                        new Label("Texto"),
                        texto,
                        new Label("Participante (opcional)"),
                        autor,
                        new Label("A partir de"),
                        inicio,
                        new Label("Até (inclui o dia escolhido)"),
                        fim,
                        limparDatas,
                        erro);
        conteudo.setPrefWidth(430);
        painel.setContent(conteudo);
        ButtonType buscar = new ButtonType("Buscar", ButtonBar.ButtonData.OK_DONE);
        painel.getButtonTypes().addAll(ButtonType.CANCEL, buscar);
        java.util.function.Supplier<FiltroBusca> filtro =
                () ->
                        new FiltroBusca(
                                texto.getText(),
                                autor.getEditor().getText(),
                                inicio.getValue(),
                                fim.getValue());
        painel.lookupButton(buscar)
                .addEventFilter(
                        javafx.event.ActionEvent.ACTION,
                        e -> {
                            try {
                                filtro.get();
                            } catch (IllegalArgumentException x) {
                                erro.setText(x.getMessage());
                                e.consume();
                            }
                        });
        setResultConverter(b -> b == buscar ? filtro.get() : null);
    }
}
