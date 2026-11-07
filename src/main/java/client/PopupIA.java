package client;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.function.Consumer;

final class PopupIA extends Dialog<Void> {
    private final Button enviar=new Button("Perguntar à IA");
    private final TextArea resposta=new TextArea();
    private final Label estado=new Label();
    private final ComboBox<String> alcance=new ComboBox<>();
    private final TextArea pergunta=new TextArea();
    private boolean pendente;
    private final boolean disponivel;
    PopupIA(Window janela,Temas temas,boolean disponivel,String sala,Consumer<String> perguntar) {
        this.disponivel=disponivel;
        initOwner(janela); setTitle("Conexo IA"); setResizable(true);
        DialogPane painel=getDialogPane(); painel.getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm()); temas.registrar(painel);
        painel.getButtonTypes().add(ButtonType.CLOSE);
        Label titulo=new Label("Conexo IA"); titulo.getStyleClass().add("chat-title"); titulo.setGraphic(new EmojiAvatar(model.EmojiIdentificacao.ROBOT,52));
        alcance.getItems().setAll("Privado · só você","Na sala · "+sala); alcance.getSelectionModel().selectFirst(); alcance.setMaxWidth(Double.MAX_VALUE);
        Label aviso=new Label(); aviso.setWrapText(true); aviso.setMaxWidth(Double.MAX_VALUE); aviso.setMinHeight(Region.USE_PREF_SIZE); aviso.setId("ia-visibilidade"); aviso.getStyleClass().add("ai-notice");
        Runnable atualizar=() -> aviso.setText(alcance.getSelectionModel().getSelectedIndex()==0
                ? "Só você verá a resposta. Sua pergunta será enviada à Groq. O histórico da sala não é enviado."
                : "A pergunta e a resposta ficarão visíveis e salvas na sala "+sala+". Sua pergunta será enviada à Groq, sem o histórico.");
        atualizar.run(); alcance.setOnAction(e -> atualizar.run());
        pergunta.setPromptText("O que você quer perguntar?"); pergunta.setWrapText(true); pergunta.setPrefRowCount(3);
        pergunta.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().length()<=450 ? c : null));
        resposta.setEditable(false); resposta.setWrapText(true); resposta.setPrefRowCount(9); resposta.setPromptText("A resposta aparecerá aqui."); VBox.setVgrow(resposta,Priority.ALWAYS);
        estado.setWrapText(true); estado.setMinHeight(Region.USE_PREF_SIZE); estado.getStyleClass().add("small-muted");
        estado.setText(disponivel ? "Escolha quem pode ver a resposta antes de enviar." : "A IA ainda não foi ativada pelo responsável pelo servidor. O chat continua disponível.");
        enviar.getStyleClass().add("primary"); enviar.setDisable(true);
        pergunta.textProperty().addListener((o,a,b) -> enviar.setDisable(!disponivel || pendente || b.isBlank()));
        enviar.setOnAction(e -> {
            iniciou(); perguntar.accept("/ia "+(alcance.getSelectionModel().getSelectedIndex()==0 ? "privado " : "sala ")+pergunta.getText().replace('\n',' ').replace('\r',' ').strip());
        });
        Label rodape=new Label("Respostas geradas por IA podem conter erros. Confira informações importantes."); rodape.getStyleClass().add("small-muted"); rodape.setWrapText(true); rodape.setMinHeight(Region.USE_PREF_SIZE);
        resposta.setPrefHeight(180); resposta.setMinHeight(150);
        VBox conteudo=new VBox(12,titulo,alcance,aviso,pergunta,enviar,estado,resposta,rodape); conteudo.setPrefWidth(560); conteudo.setPadding(new Insets(8));
        ScrollPane rolagem=new ScrollPane(conteudo); rolagem.setFitToWidth(true); rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); rolagem.getStyleClass().add("message-scroll");
        rolagem.setPrefViewportWidth(560); rolagem.setPrefViewportHeight(Math.min(620,javafx.stage.Screen.getPrimary().getVisualBounds().getHeight()-200)); painel.setContent(rolagem);
        // Não bloqueia a janela do chat durante a consulta.
        initModality(javafx.stage.Modality.NONE);
    }
    void iniciou() { pendente=true; enviar.setDisable(true); alcance.setDisable(true); pergunta.setDisable(true); estado.setText("Conexo IA está preparando a resposta…"); }
    void respondeu(String texto,boolean erro) { pendente=false; resposta.setText(texto); estado.setText(erro ? "Não foi possível concluir a pergunta." : "Resposta recebida."); alcance.setDisable(false); pergunta.setDisable(false); enviar.setDisable(!disponivel || pergunta.getText().isBlank()); }
}
