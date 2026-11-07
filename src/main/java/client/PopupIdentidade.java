package client;

import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import javafx.util.Duration;
import model.*;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** O botão só é liberado após a escolha explícita de um emoji e um apelido válido. */
final class PopupIdentidade extends Dialog<Usuario> {
    private EmojiIdentificacao escolhido;
    private final TextField apelido;
    private final ButtonType entrar=new ButtonType("Entrar no chat",ButtonBar.ButtonData.OK_DONE);
    private final Map<EmojiIdentificacao,ToggleButton> botoes=new LinkedHashMap<>();
    private static String comparar(String texto) { return Normalizer.normalize(texto,Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT); }
    PopupIdentidade(Window janela, Temas temas, String nome) {
        initOwner(janela); setTitle("Sua identidade no Conexo"); setResizable(true);
        DialogPane painel=getDialogPane(); painel.getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm());
        temas.registrar(painel); painel.getStyleClass().add("identity-dialog");
        painel.getButtonTypes().addAll(ButtonType.CANCEL,entrar);
        Label titulo=new Label("Como você quer aparecer?"); titulo.getStyleClass().add("chat-title");
        Label descricao=new Label("Escolha um emoji para identificar você na conversa."); descricao.getStyleClass().add("muted");
        StackPane destaque=new StackPane(); destaque.getStyleClass().add("identity-card"); destaque.setPrefSize(220,140); destaque.setMinHeight(140); destaque.setMaxWidth(220);
        Label convite=new Label("Escolha seu emoji"); convite.getStyleClass().add("muted"); destaque.getChildren().add(convite);
        apelido=new TextField(nome); apelido.setPromptText("Seu apelido"); apelido.setId("identidade-apelido");
        Label erro=new Label("O emoji é obrigatório para entrar."); erro.getStyleClass().add("small-muted"); erro.setWrapText(true);
        TilePane opcoes=new TilePane(8,8); opcoes.setPrefColumns(6); opcoes.setAlignment(Pos.CENTER);
        TextField busca=new TextField(); busca.setPromptText("Buscar emoji…"); busca.setId("buscar-emoji"); busca.setMinWidth(0); HBox.setHgrow(busca,Priority.ALWAYS);
        ComboBox<String> categoria=new ComboBox<>(); categoria.getItems().add("Todos"); for(var item:EmojiIdentificacao.Categoria.values()) categoria.getItems().add(item.toString()); categoria.setValue("Todos"); categoria.setAccessibleText("Categoria dos emojis"); categoria.setId("categoria-emoji"); categoria.setPrefWidth(130);
        Label quantidade=new Label(); quantidade.getStyleClass().add("small-muted");
        Label escolha=new Label("Seu perfil começa com um emoji"); escolha.getStyleClass().add("small-muted");
        ToggleGroup grupo=new ToggleGroup();
        for(var emoji:EmojiIdentificacao.values()) {
            ToggleButton b=new ToggleButton(); b.setGraphic(new EmojiAvatar(emoji,40)); b.setToggleGroup(grupo);
            b.getStyleClass().add("emoji-choice"); b.setTooltip(new Tooltip(emoji.nome())); b.setAccessibleText(emoji.nome());
            b.setOnAction(e -> {
                if(!b.isSelected()) { b.setSelected(true); return; }
                escolhido=emoji; EmojiAvatar avatar=new EmojiAvatar(emoji,125); destaque.getChildren().setAll(avatar); escolha.setText("Sua escolha: "+emoji.nome());
                ScaleTransition animacao=new ScaleTransition(Duration.millis(320),avatar); animacao.setFromX(.86); animacao.setFromY(.86); animacao.setToX(1); animacao.setToY(1); animacao.setInterpolator(Animacoes.SUAVE); animacao.play();
                validar(erro);
            });
            botoes.put(emoji,b);
        }
        Runnable filtrar=()->{
            String termo=comparar(busca.getText().strip()); opcoes.getChildren().clear();
            botoes.forEach((emoji,b)->{ if(("Todos".equals(categoria.getValue()) || emoji.categoria().toString().equals(categoria.getValue())) && comparar(emoji.nome()+" "+emoji.id()).contains(termo)) opcoes.getChildren().add(b); });
            quantidade.setText(opcoes.getChildren().isEmpty() ? "Nenhum emoji encontrado. Tente outro nome." : opcoes.getChildren().size()+" de "+EmojiIdentificacao.values().length+" emojis");
        };
        busca.textProperty().addListener((o,a,b)->filtrar.run()); categoria.setOnAction(e->filtrar.run()); filtrar.run();
        ScrollPane grade=new ScrollPane(opcoes); grade.setFitToWidth(true); grade.setPrefViewportHeight(176); grade.setMinHeight(120); grade.setMaxHeight(190); grade.getStyleClass().add("emoji-grid-scroll"); grade.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox filtros=new HBox(8,busca,categoria); filtros.setAlignment(Pos.CENTER_LEFT);
        VBox conteudo=new VBox(10,titulo,descricao,destaque,escolha,new Label("Seu apelido"),apelido,filtros,quantidade,grade,erro);
        conteudo.setAlignment(Pos.CENTER); conteudo.setPrefWidth(460); conteudo.setPadding(new Insets(8));
        ScrollPane rolagem=new ScrollPane(conteudo); rolagem.setFitToWidth(true); rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); rolagem.getStyleClass().add("message-scroll"); rolagem.setPrefViewportWidth(460); rolagem.setPrefViewportHeight(Math.min(620,javafx.stage.Screen.getPrimary().getVisualBounds().getHeight()-180)); painel.setContent(rolagem);
        painel.lookupButton(entrar).getStyleClass().add("primary"); painel.lookupButton(entrar).setDisable(true);
        apelido.textProperty().addListener((o,a,b) -> validar(erro));
        setResultConverter(botao -> botao==entrar ? new Usuario(apelido.getText(),escolhido.id()) : null);
    }
    private void validar(Label erro) {
        try { new Usuario(apelido.getText(),escolhido==null ? "" : escolhido.id()); erro.setText("Seu emoji aparecerá nas mensagens e nos participantes."); getDialogPane().lookupButton(entrar).setDisable(false); }
        catch(IllegalArgumentException e) { erro.setText(e.getMessage()); getDialogPane().lookupButton(entrar).setDisable(true); }
    }
}
