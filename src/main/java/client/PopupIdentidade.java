package client;

import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import javafx.util.Duration;
import model.*;

/** O botão só é liberado após a escolha explícita de um emoji e um apelido válido. */
final class PopupIdentidade extends Dialog<Usuario> {
    private EmojiIdentificacao escolhido;
    private final TextField apelido;
    private final ButtonType entrar=new ButtonType("Entrar no chat",ButtonBar.ButtonData.OK_DONE);
    PopupIdentidade(Window janela, Temas temas, String nome) {
        initOwner(janela); setTitle("Sua identidade no Conexo"); setResizable(false);
        DialogPane painel=getDialogPane(); painel.getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm());
        temas.registrar(painel); painel.getStyleClass().add("identity-dialog");
        painel.getButtonTypes().addAll(ButtonType.CANCEL,entrar);
        Label titulo=new Label("Como você quer aparecer?"); titulo.getStyleClass().add("chat-title");
        Label descricao=new Label("Escolha um emoji para identificar você na conversa."); descricao.getStyleClass().add("muted");
        StackPane destaque=new StackPane(); destaque.getStyleClass().add("identity-card"); destaque.setPrefSize(240,170); destaque.setMaxWidth(240);
        Label convite=new Label("Escolha seu emoji"); convite.getStyleClass().add("muted"); destaque.getChildren().add(convite);
        apelido=new TextField(nome); apelido.setPromptText("Seu apelido"); apelido.setId("identidade-apelido");
        Label erro=new Label("O emoji é obrigatório para entrar."); erro.getStyleClass().add("small-muted"); erro.setWrapText(true);
        TilePane opcoes=new TilePane(8,8); opcoes.setPrefColumns(6); opcoes.setAlignment(Pos.CENTER);
        ToggleGroup grupo=new ToggleGroup();
        for(var emoji:EmojiIdentificacao.values()) {
            ToggleButton b=new ToggleButton(); b.setGraphic(new EmojiAvatar(emoji,40)); b.setToggleGroup(grupo);
            b.getStyleClass().add("emoji-choice"); b.setTooltip(new Tooltip(emoji.nome())); b.setAccessibleText(emoji.nome());
            b.setOnAction(e -> {
                if(!b.isSelected()) { b.setSelected(true); return; }
                escolhido=emoji; EmojiAvatar avatar=new EmojiAvatar(emoji,150); destaque.getChildren().setAll(avatar);
                ScaleTransition animacao=new ScaleTransition(Duration.millis(320),avatar); animacao.setFromX(.86); animacao.setFromY(.86); animacao.setToX(1); animacao.setToY(1); animacao.setInterpolator(Animacoes.SUAVE); animacao.play();
                validar(erro);
            });
        opcoes.getChildren().add(b);
        }
        VBox conteudo=new VBox(12,titulo,descricao,destaque,opcoes,new Label("Seu apelido"),apelido,erro);
        conteudo.setAlignment(Pos.CENTER); conteudo.setPrefWidth(440); conteudo.setPadding(new Insets(10)); painel.setContent(conteudo);
        painel.lookupButton(entrar).getStyleClass().add("primary"); painel.lookupButton(entrar).setDisable(true);
        apelido.textProperty().addListener((o,a,b) -> validar(erro));
        setResultConverter(botao -> botao==entrar ? new Usuario(apelido.getText(),escolhido.id()) : null);
    }
    private void validar(Label erro) {
        try { new Usuario(apelido.getText(),escolhido==null ? "" : escolhido.id()); erro.setText("Seu emoji aparecerá nas mensagens e nos participantes."); getDialogPane().lookupButton(entrar).setDisable(false); }
        catch(IllegalArgumentException e) { erro.setText(e.getMessage()); getDialogPane().lookupButton(entrar).setDisable(true); }
    }
}
