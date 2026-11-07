package client;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import model.Citacao;
import model.Reacao;
import protocol.Evento;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/** Interações públicas de uma única sala. Privadas não recebem estes controles. */
final class RecursosConversa {
    private record Cartao(FlowPane reacoes,Button fixar) { }
    private final Map<Long,Cartao> cartoes=new LinkedHashMap<>();
    private final Map<Long,Evento> fixadas=new LinkedHashMap<>();
    private final Consumer<String> enviar;
    private final Runnable focar;
    private final LongConsumer localizar;
    private final Window janela;
    private final Temas temas;
    private Citacao resposta;
    private Dialog<Void> popup;
    final VBox citacaoEnvio=new VBox(6);
    final Button avisos=new Button("Fixadas · 0");

    RecursosConversa(Window janela,Temas temas,Consumer<String> enviar,Runnable focar,LongConsumer localizar) {
        this.janela=janela; this.temas=temas; this.enviar=enviar; this.focar=focar; this.localizar=localizar;
        citacaoEnvio.setId("citacao-envio"); cancelarResposta();
        avisos.getStyleClass().add("secondary"); avisos.setId("mensagens-fixadas"); avisos.setTooltip(new Tooltip("Avisos e mensagens fixadas desta sala")); avisos.setOnAction(e->abrirFixadas());
    }
    Citacao resposta() { return resposta; }
    void responder(Citacao citacao) {
        resposta=citacao; Button cancelar=new Button("Cancelar resposta"); cancelar.getStyleClass().add("message-action"); cancelar.setOnAction(e->cancelarResposta());
        citacaoEnvio.getChildren().setAll(citacao(citacao),cancelar); citacaoEnvio.setVisible(true); citacaoEnvio.setManaged(true); focar.run();
    }
    void cancelarResposta() { resposta=null; citacaoEnvio.getChildren().clear(); citacaoEnvio.setVisible(false); citacaoEnvio.setManaged(false); }
    VBox citacao(Citacao c) {
        Label autor=new Label("↩ "+c.autor()+" · #"+c.id()); autor.getStyleClass().add("message-author");
        Label resumo=new Label(c.texto()); resumo.setWrapText(true); resumo.setMinWidth(0); resumo.setMaxWidth(540); resumo.getStyleClass().add("small-muted");
        VBox caixa=new VBox(4,autor,resumo); caixa.setMinWidth(0); caixa.getStyleClass().add("message-quote"); caixa.setOnMouseClicked(e->localizar.accept(c.id()));
        caixa.setAccessibleText("Resposta a "+c.autor()+": "+c.texto()); return caixa;
    }
    void registrar(long id,String autor,String texto,VBox bolha) {
        Button responder=new Button("Responder"); responder.getStyleClass().add("message-action"); responder.setOnAction(e->{ enviar.accept("/typing stop"); responder(new Citacao(id,autor,texto)); });
        MenuButton reagir=new MenuButton("Reagir"); reagir.getStyleClass().add("message-action");
        for(String emoji:Reacao.PERMITIDAS) { var item=new MenuItem(emoji); item.setOnAction(e->enviar.accept("/reagir "+id+" "+emoji)); reagir.getItems().add(item); }
        Button fixar=new Button(); fixar.getStyleClass().add("message-action"); fixar.setOnAction(e->enviar.accept((fixadas.containsKey(id) ? "/desfixar " : "/fixar ")+id));
        FlowPane acoes=new FlowPane(6,4,responder,reagir,fixar); acoes.setAlignment(Pos.CENTER_LEFT);
        FlowPane reacoes=new FlowPane(6,4); reacoes.setVisible(false); reacoes.setManaged(false); reacoes.setId("reacoes-"+id);
        cartoes.put(id,new Cartao(reacoes,fixar)); atualizarFixar(id); bolha.getChildren().addAll(reacoes,acoes);
    }
    void esquecer(long id) { cartoes.remove(id); }
    void limparCartoes() { cartoes.clear(); }
    void reacoes(Evento evento) {
        long id=Long.parseLong(evento.campo(0)); var cartao=cartoes.get(id); if(cartao==null) return;
        FlowPane grupo=cartao.reacoes(); grupo.getChildren().clear();
        for(int i=1;i+2<evento.campos().size();i+=3) {
            String emoji=evento.campo(i); boolean minha=Boolean.parseBoolean(evento.campo(i+2));
            Button b=new Button(emoji+" "+evento.campo(i+1)); b.getStyleClass().add("reaction-chip"); if(minha) b.getStyleClass().add("reaction-own");
            b.setTooltip(new Tooltip(minha ? "Remover sua reação" : "Adicionar sua reação")); b.setAccessibleText(emoji+", "+evento.campo(i+1)+" reações"+(minha ? ", você reagiu" : ""));
            b.setOnAction(e->enviar.accept("/reagir "+id+" "+emoji)); grupo.getChildren().add(b);
        }
        grupo.setVisible(!grupo.getChildren().isEmpty()); grupo.setManaged(grupo.isVisible());
    }
    void fixadas(Evento evento) {
        fixadas.clear();
        for(int i=0;i+5<evento.campos().size();i+=6) { var m=new Evento(Evento.Tipo.MENSAGEM,evento.campos().subList(i,i+6)); fixadas.put(Long.parseLong(m.campo(0)),m); }
        avisos.setText("Fixadas · "+fixadas.size()); cartoes.keySet().forEach(this::atualizarFixar);
        if(popup!=null && popup.isShowing()) preencherPopup();
    }
    private void atualizarFixar(long id) { var c=cartoes.get(id); if(c!=null) c.fixar().setText(fixadas.containsKey(id) ? "Desafixar" : "Fixar"); }
    private void abrirFixadas() {
        if(popup!=null && popup.isShowing()) { popup.getDialogPane().getScene().getWindow().requestFocus(); return; }
        popup=new Dialog<>(); popup.initOwner(janela); popup.setTitle("Mensagens fixadas da sala"); popup.setResizable(true);
        popup.getDialogPane().getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm()); temas.registrar(popup.getDialogPane()); popup.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        preencherPopup(); popup.show();
    }
    private void preencherPopup() {
        VBox lista=new VBox(14); lista.setPrefWidth(500); lista.setMinWidth(0);
        if(fixadas.isEmpty()) { Label vazio=new Label("Nenhuma mensagem fixada. Use Fixar em uma mensagem pública para destacar um aviso."); vazio.setWrapText(true); lista.getChildren().add(vazio); }
        for(var m:fixadas.values()) {
            Label autor=new Label(m.campo(1)+" · #"+m.campo(0)); autor.getStyleClass().add("message-author");
            javafx.scene.Node corpo;
            if("MARKDOWN_IA".equals(m.campo(5))) corpo=new ConteudoMarkdown(m.campo(2));
            else { var l=new Label(m.campo(2)); l.setWrapText(true); l.setMaxWidth(480); corpo=l; }
            Button remover=new Button("Desafixar"); remover.setOnAction(e->enviar.accept("/desfixar "+m.campo(0)));
            VBox cartao=new VBox(10,autor,corpo,remover); cartao.getStyleClass().add("message-quote"); lista.getChildren().add(cartao);
        }
        ScrollPane scroll=new ScrollPane(lista); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(Math.min(400,javafx.stage.Screen.getPrimary().getVisualBounds().getHeight()-180)); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); popup.getDialogPane().setContent(scroll);
    }
    void fechar() { if(popup!=null) popup.close(); }
}
