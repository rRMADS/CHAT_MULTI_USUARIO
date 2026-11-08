package client;

import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import protocol.CatalogoComandos;

import java.util.Locale;

/** Preenche o campo; nenhum comando é executado ao escolher uma sugestão. */
final class AjudaComandos extends VBox {
    private final ListView<CatalogoComandos.Item> lista = new ListView<>();
    private final TextField campo;
    private boolean suspensa;

    AjudaComandos(TextField campo) {
        super(6);
        this.campo = campo;
        setId("ajuda-comandos");
        getStyleClass().add("command-suggestions");
        Label titulo = new Label("Comandos · escolha para preencher · Esc para recolher");
        titulo.getStyleClass().add("small-muted");
        titulo.setWrapText(true);
        lista.setId("lista-comandos");
        lista.setPrefHeight(180);
        lista.setMinHeight(160);
        lista.setMaxHeight(180);
        lista.setFixedCellSize(-1);
        lista.setCellFactory(v ->
                new ListCell<>() {
                    private final Label nome = new Label(), descricao = new Label();
                    private final VBox detalhe = new VBox(3, nome, descricao);

                    {
                        setMinWidth(0);
                        prefWidthProperty().bind(lista.widthProperty().subtract(20));
                        detalhe.setMinWidth(0);
                        nome.setWrapText(true);
                        descricao.setWrapText(true);
                        nome.getStyleClass().add("message-author");
                        descricao.getStyleClass().add("small-muted");
                        nome.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                        descricao.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                    }

                    @Override
                    protected void updateItem(CatalogoComandos.Item item, boolean vazia) {
                        super.updateItem(item, vazia);
                        setText(null);
                        if (vazia || item == null) setGraphic(null);
                        else {
                            nome.setText(item.comando());
                            descricao.setText(item.descricao());
                            setGraphic(detalhe);
                        }
                    }
                });
        lista.setOnMouseClicked(e -> {
            if (lista.getSelectionModel().getSelectedItem() != null) escolher();
        });
        lista.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                escolher();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                recolher();
                campo.requestFocus();
                e.consume();
            }
        });
        campo.addEventFilter(
                javafx.scene.input.KeyEvent.KEY_PRESSED,
                e -> {
                    if (e.getCode() == KeyCode.ESCAPE && isVisible()) {
                        recolher();
                        e.consume();
                    } else if (e.getCode() == KeyCode.DOWN && isVisible()) {
                        lista.requestFocus();
                        lista.getSelectionModel().selectFirst();
                        e.consume();
                    }
                });
        campo.textProperty()
                .addListener((o, a, b) -> {
                    suspensa = false;
                    atualizar(b);
                });
        getChildren().addAll(titulo, lista);
        recolher();
    }

    private void atualizar(String texto) {
        String t = texto.stripLeading().toLowerCase(Locale.ROOT);
        boolean comando = t.startsWith("/") && !t.contains(" ") && !suspensa;
        lista.getItems()
                .setAll(
                        CatalogoComandos.TODOS.stream()
                                .filter(i -> t.equals("/") || i.comando().contains(t))
                                .toList());
        setVisible(comando && !lista.getItems().isEmpty());
        setManaged(isVisible());
    }

    private void escolher() {
        var item = lista.getSelectionModel().getSelectedItem();
        if (item == null) return;
        campo.setText(item.preencher());
        campo.positionCaret(campo.getLength());
        recolher();
        campo.requestFocus();
    }

    void abrir() {
        suspensa = false;
        lista.getItems().setAll(CatalogoComandos.TODOS);
        setVisible(true);
        setManaged(true);
        campo.requestFocus();
    }

    void recolher() {
        suspensa = true;
        setVisible(false);
        setManaged(false);
    }
}
