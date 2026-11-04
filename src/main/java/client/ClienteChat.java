package client;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.shape.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import protocol.Evento;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ClienteChat extends Application {
    private Stage janela;
    private Scene cena;
    private ConexaoChat conexao;
    private String apelido, endereco;
    private boolean conectado, carregandoHistorico, animacoes = true;
    private final Set<String> ids = new LinkedHashSet<>();
    private TextField nome, host, porta, busca, texto;
    private Label erroLogin, aviso, digitando, titulo, subtitulo, contador, online, estadoConexao;
    private Node vazio;
    private Button conectar;
    private VBox mensagens, participantes;
    private ScrollPane rolagem;
    private ComboBox<String> destino;
    private final PauseTransition expirarDigitacao = new PauseTransition(Duration.seconds(3));
    private long ultimaDigitacao;

    @Override public void start(Stage stage) {
        janela = stage; janela.setTitle("Conexo · Chat multiusuário");
        cena = new Scene(new StackPane(), 1180, 780);
        cena.getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm());
        janela.setScene(cena); janela.setMinWidth(960); janela.setMinHeight(680);
        mostrarLogin(""); janela.show();
    }
    private Label label(String texto, String estilo) { Label l = new Label(texto); l.getStyleClass().add(estilo); return l; }
    private Button botao(String texto, String estilo) { Button b = new Button(texto); b.getStyleClass().add(estilo); b.setCursor(Cursor.HAND); return b; }
    private void trocar(Parent raiz) { cena.setRoot(raiz); animar(raiz); }
    private void animar(Node node) {
        if (!animacoes) return;
        FadeTransition fade = new FadeTransition(Duration.millis(230), node); fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition deslizar = new TranslateTransition(Duration.millis(230), node); deslizar.setFromY(9); deslizar.setToY(0);
        deslizar.setInterpolator(Interpolator.EASE_OUT); new ParallelTransition(fade, deslizar).play();
    }
    private void mostrarLogin(String mensagem) {
        conectado = false;
        VBox apresentacao = new VBox(24); apresentacao.getStyleClass().add("intro"); apresentacao.setPrefWidth(510);
        Label marca = label("●  conexo", "brand");
        Label selo = label("UM ESPAÇO PARA SE CONECTAR", "eyebrow");
        Label chamada = label("Boas conversas\ncomeçam com\numa conexão.", "hero");
        Label descricao = label("Perto ou longe, na mesma conversa.\nSeu chat em tempo real, simples e acolhedor.", "intro-copy");
        Pane desenho = ilustracao(); VBox.setVgrow(desenho, Priority.ALWAYS);
        Label rodape = label("JAVA + SOCKETS TCP     /     FEITO PARA CONVERSAR", "intro-footer");
        apresentacao.getChildren().addAll(marca, selo, chamada, descricao, desenho, rodape);

        VBox formulario = new VBox(13); formulario.getStyleClass().add("login-form"); formulario.setMaxWidth(430);
        Label passo = label("OLÁ, QUE BOM TER VOCÊ AQUI", "eyebrow-green");
        Label heading = label("Entre na conversa", "login-title");
        Label explicacao = label("Escolha seu apelido e o endereço do servidor.\nCada pessoa entra pelo próprio computador.", "muted");
        nome = new TextField(apelido == null ? "" : apelido); nome.setPromptText("Como você quer ser chamado?"); nome.setId("apelido");
        host = new TextField("127.0.0.1"); host.setPromptText("Ex.: 192.168.1.10"); host.setId("ip");
        porta = new TextField("5050"); porta.setId("porta"); porta.setPrefWidth(100); porta.setMaxWidth(110);
        VBox campoHost = new VBox(7, label("IP ou nome do servidor", "field-label"), host); HBox.setHgrow(campoHost, Priority.ALWAYS);
        HBox rede = new HBox(12, campoHost, new VBox(7, label("Porta", "field-label"), porta));
        erroLogin = label(mensagem, "error"); erroLogin.setWrapText(true); erroLogin.setMinHeight(42);
        conectar = botao("Conectar ao chat  →", "primary"); conectar.setMaxWidth(Double.MAX_VALUE); conectar.setDefaultButton(true); conectar.setId("conectar");
        conectar.setOnAction(e -> conectar()); nome.setOnAction(e -> conectar());
        Label dica = label("No mesmo computador, use 127.0.0.1.\nEm outra máquina, use o IP exibido pelo servidor.", "small-muted"); dica.setWrapText(true);
        CheckBox movimento = new CheckBox("Animações suaves"); movimento.setSelected(animacoes); movimento.setOnAction(e -> animacoes = movimento.isSelected());
        Label privacidade = label("Rede local · Histórico público salvo no servidor", "small-muted");
        formulario.getChildren().addAll(passo, heading, explicacao, espaco(8), label("Seu apelido", "field-label"), nome, rede, erroLogin, conectar, dica, espaco(6), movimento, privacidade);
        StackPane direita = new StackPane(formulario); direita.setPadding(new Insets(40)); HBox.setHgrow(direita, Priority.ALWAYS);
        HBox raiz = new HBox(apresentacao, direita); raiz.getStyleClass().add("login-root"); trocar(raiz);
        Platform.runLater(nome::requestFocus);
    }
    private Region espaco(double altura) { Region r = new Region(); r.setMinHeight(altura); return r; }
    private Pane ilustracao() {
        Pane pane = new Pane(); pane.setMinHeight(90); pane.setPrefHeight(150);
        Circle halo = new Circle(190, 85, 72, Color.web("#214c49")); halo.setOpacity(.6);
        Line linha = new Line(82, 108, 287, 47); linha.setStroke(Color.web("#6b9e84")); linha.setStrokeWidth(2); linha.getStrokeDashArray().addAll(5.0, 7.0);
        Label a = label("  Olá!  ", "illustration-bubble"); a.relocate(48, 90);
        Label b = label("  Que bom te ver por aqui.  ", "illustration-bubble-alt"); b.relocate(155, 25);
        Circle ponto = new Circle(296, 108, 7, Color.web("#bbeb9e"));
        pane.getChildren().addAll(halo, linha, a, b, ponto); return pane;
    }
    private void conectar() {
        if (conexao != null) return;
        String usuario = nome.getText().strip(); String ip = host.getText().strip(); int numero;
        if (!usuario.matches("[\\p{L}\\p{N}_-]{1,30}")) { erroLogin.setText("Use um apelido de 1 a 30 letras, números, _ ou -, sem espaços."); return; }
        try { numero = Integer.parseInt(porta.getText().strip()); if (numero < 1 || numero > 65535) throw new NumberFormatException(); }
        catch (NumberFormatException e) { erroLogin.setText("A porta deve ser um número entre 1 e 65535."); return; }
        if (ip.isBlank()) { erroLogin.setText("Informe o IP do servidor."); return; }
        apelido = usuario; endereco = ip + ":" + numero;
        conectar.setDisable(true); conectar.setText("Conectando…"); erroLogin.setText("");
        ConexaoChat[] sessao = new ConexaoChat[1];
        sessao[0] = new ConexaoChat(evento -> Platform.runLater(() -> { if (conexao == sessao[0]) receber(evento); }),
                motivo -> Platform.runLater(() -> { if (conexao == sessao[0]) desconectado(motivo); }));
        conexao = sessao[0]; conexao.conectar(ip, numero, usuario);
    }
    private void mostrarChat() {
        ids.clear();
        BorderPane raiz = new BorderPane(); raiz.getStyleClass().add("chat-root");
        VBox lateral = new VBox(14); lateral.getStyleClass().add("sidebar"); lateral.setPrefWidth(245); lateral.setMinWidth(220);
        Label marca = label("●  conexo", "brand"); marca.setPadding(new Insets(0, 0, 24, 0));
        Button geral = botao("#   Conversa geral", "channel"); geral.setMaxWidth(Double.MAX_VALUE); geral.setOnAction(e -> { destino.setValue("Todos"); comando("/history 50"); });
        online = label("PARTICIPANTES", "sidebar-label"); participantes = new VBox(6);
        ScrollPane lista = new ScrollPane(participantes); lista.getStyleClass().add("people-scroll"); lista.setFitToWidth(true); VBox.setVgrow(lista, Priority.ALWAYS);
        Label voce = label(apelido + "  ·  você", "sidebar-name");
        estadoConexao = label("●  Conectado\n" + endereco, "connection"); estadoConexao.setWrapText(true);
        Button copiar = botao("Copiar endereço", "sidebar-button"); copiar.setOnAction(e -> { ClipboardContent c = new ClipboardContent(); c.putString(endereco); Clipboard.getSystemClipboard().setContent(c); aviso.setText("Endereço copiado. Compartilhe com quem está na mesma rede."); });
        Button sair = botao("Sair do chat  ↗", "sidebar-button"); sair.setOnAction(e -> sair());
        lateral.getChildren().addAll(marca, label("SEU ESPAÇO", "sidebar-label"), geral, espaco(12), online, lista, voce, estadoConexao, copiar, sair);
        raiz.setLeft(lateral);

        VBox principal = new VBox();
        titulo = label("Conversa geral", "chat-title"); subtitulo = label("Um lugar para compartilhar ideias e se encontrar.", "muted");
        VBox titulos = new VBox(5, titulo, subtitulo); HBox.setHgrow(titulos, Priority.ALWAYS);
        CheckBox animar = new CheckBox("Animações"); animar.setSelected(animacoes); animar.setOnAction(e -> animacoes = animar.isSelected());
        HBox header = new HBox(12, titulos, animar); header.getStyleClass().add("chat-header"); header.setAlignment(Pos.CENTER_LEFT);
        busca = new TextField(); busca.setPromptText("Buscar no histórico público…"); busca.setId("busca"); HBox.setHgrow(busca, Priority.ALWAYS);
        Button pesquisar = botao("Buscar", "secondary"); pesquisar.setOnAction(e -> buscar()); busca.setOnAction(e -> buscar());
        Button voltar = botao("Últimas mensagens", "secondary"); voltar.setOnAction(e -> { busca.clear(); comando("/history 50"); });
        HBox ferramentas = new HBox(9, busca, pesquisar, voltar); ferramentas.getStyleClass().add("toolbar");
        mensagens = new VBox(14); mensagens.getStyleClass().add("messages");
        rolagem = new ScrollPane(mensagens); rolagem.setFitToWidth(true); rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); rolagem.getStyleClass().add("message-scroll"); VBox.setVgrow(rolagem, Priority.ALWAYS);
        digitando = label("", "typing"); expirarDigitacao.setOnFinished(e -> digitando.setText(""));
        aviso = label("Públicas ficam no histórico. Privadas duram apenas esta sessão.", "small-muted"); aviso.setWrapText(true);
        destino = new ComboBox<>(); destino.getItems().add("Todos"); destino.setValue("Todos"); destino.setPrefWidth(160); destino.setAccessibleText("Destinatário da mensagem");
        destino.setOnAction(e -> { texto.setPromptText("Todos".equals(destino.getValue()) ? "Escreva uma mensagem…" : "Mensagem privada para " + destino.getValue() + "…"); texto.requestFocus(); });
        texto = new TextField(); texto.setPromptText("Escreva uma mensagem…"); texto.setId("mensagem"); HBox.setHgrow(texto, Priority.ALWAYS); texto.setOnAction(e -> enviar());
        contador = label("0 / 500", "counter");
        texto.textProperty().addListener((obs, antes, depois) -> {
            contador.setText(depois.length() + " / 500"); contador.getStyleClass().remove("over-limit");
            if (depois.length() > 500) contador.getStyleClass().add("over-limit");
            long agora = System.nanoTime();
            if (!depois.isBlank() && "Todos".equals(destino.getValue()) && agora - ultimaDigitacao > 1_500_000_000L && conexao != null) { ultimaDigitacao = agora; conexao.enviar("/typing"); }
        });
        Button enviar = botao("Enviar  ↑", "primary"); enviar.setOnAction(e -> enviar()); enviar.setId("enviar");
        HBox composicao = new HBox(10, destino, texto, enviar); composicao.setAlignment(Pos.CENTER_LEFT);
        Label ajuda = label("Enter para enviar  ·  /help para ver os comandos", "small-muted"); Region respiro = new Region(); HBox.setHgrow(respiro, Priority.ALWAYS);
        HBox legenda = new HBox(ajuda, respiro, contador);
        VBox rodape = new VBox(9, digitando, aviso, composicao, legenda); rodape.getStyleClass().add("composer");
        principal.getChildren().addAll(header, ferramentas, rolagem, rodape); raiz.setCenter(principal);
        trocar(raiz); janela.setTitle("Conexo · " + apelido + " · " + endereco); Platform.runLater(texto::requestFocus);
    }
    private void receber(Evento evento) {
        switch (evento.tipo()) {
            case BEM_VINDO -> { conectado = true; mostrarChat(); }
            case ERRO -> {
                if (conectado) { aviso.setText(evento.campo(0)); aviso.getStyleClass().add("error"); }
                else { erroLogin.setText(evento.campo(0)); ConexaoChat anterior = conexao; conexao = null; anterior.close(); conectar.setDisable(false); conectar.setText("Conectar ao chat  →"); }
            }
            case HISTORICO_INICIO -> { carregandoHistorico = true; mensagens.getChildren().clear(); ids.clear(); vazio = null; subtitulo.setText(evento.campo(0)); }
            case HISTORICO_FIM -> {
                carregandoHistorico = false;
                if (mensagens.getChildren().isEmpty()) {
                    sistema("Ainda não há mensagens por aqui. Que tal começar a conversa?"); vazio = mensagens.getChildren().get(0);
                }
                rolar();
            }
            case MENSAGEM -> {
                if (!ids.add(evento.campo(0))) break;
                if (ids.size() > 1000) ids.remove(ids.iterator().next());
                mensagem(evento.campo(1), evento.campo(2), evento.campo(3), null);
                digitando.setText("");
            }
            case PRIVADA -> mensagem(evento.campo(0), evento.campo(2), evento.campo(3), evento.campo(1));
            case SISTEMA -> sistema(evento.campo(0));
            case USUARIOS -> {
                participantes.getChildren().clear(); online.setText("PARTICIPANTES  ·  " + evento.campos().size());
                String selecionado = destino.getValue(); destino.getItems().setAll("Todos");
                for (String usuario : evento.campos()) {
                    boolean souEu = usuario.equalsIgnoreCase(apelido);
                    Button pessoa = botao("●   " + usuario + (souEu ? "  (você)" : ""), "person"); pessoa.setMaxWidth(Double.MAX_VALUE);
                    pessoa.setOnAction(e -> destino.setValue(souEu ? "Todos" : usuario)); participantes.getChildren().add(pessoa);
                    if (!souEu) destino.getItems().add(usuario);
                }
                destino.setValue(destino.getItems().contains(selecionado) ? selecionado : "Todos");
            }
            case DIGITANDO -> { digitando.setText(evento.campo(0) + " está digitando…"); expirarDigitacao.playFromStart(); }
            case PONG -> { }
        }
    }
    private void mensagem(String remetente, String conteudo, String data, String privadoPara) {
        if (vazio != null) { mensagens.getChildren().remove(vazio); vazio = null; }
        boolean minha = remetente.equalsIgnoreCase(apelido);
        String hora = LocalDateTime.parse(data).format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"));
        String cabecalho = (minha ? "Você" : remetente) + "   ·   " + hora;
        if (privadoPara != null) cabecalho += "   ·   Privada " + (minha ? "para " + privadoPara : "para você");
        Label meta = label(cabecalho, "message-meta");
        Label corpo = label(conteudo, "message-body"); corpo.setWrapText(true); corpo.setMaxWidth(520);
        VBox bolha = new VBox(7, meta, corpo); bolha.getStyleClass().add(minha ? "bubble-own" : "bubble"); bolha.setMaxWidth(560);
        if (privadoPara != null) bolha.getStyleClass().add("bubble-private");
        ContextMenu menu = new ContextMenu(); MenuItem copiar = new MenuItem("Copiar mensagem");
        copiar.setOnAction(e -> { ClipboardContent c = new ClipboardContent(); c.putString(conteudo); Clipboard.getSystemClipboard().setContent(c); }); menu.getItems().add(copiar);
        bolha.setOnContextMenuRequested(e -> menu.show(bolha, e.getScreenX(), e.getScreenY()));
        HBox linha = new HBox(10); linha.setAlignment(minha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if (!minha) { Label avatar = label(remetente.substring(0, 1).toUpperCase(), "avatar"); linha.getChildren().add(avatar); }
        linha.getChildren().add(bolha); adicionar(linha);
    }
    private void sistema(String texto) { Label l = label(texto, "system-message"); l.setWrapText(true); l.setMaxWidth(Double.MAX_VALUE); l.setAlignment(Pos.CENTER); adicionar(l); }
    private void adicionar(Node item) {
        boolean noFim = rolagem.getVvalue() > .93 || mensagens.getChildren().size() < 5;
        mensagens.getChildren().add(item);
        if (mensagens.getChildren().size() > 500) mensagens.getChildren().remove(0);
        if (!carregandoHistorico) { animar(item); if (noFim) rolar(); }
    }
    private void rolar() { Platform.runLater(() -> rolagem.setVvalue(1)); }
    private void buscar() {
        if (conexao == null) return;
        String termo = busca.getText().strip();
        if (termo.isBlank() || termo.length() > 100) { aviso.setText("Digite de 1 a 100 caracteres para buscar no histórico."); return; }
        conexao.enviar("/buscar " + termo);
    }
    private void enviar() {
        if (conexao == null || !conectado) return;
        String mensagem = texto.getText().strip();
        if (mensagem.isBlank() || mensagem.length() > 500 || mensagem.chars().anyMatch(Character::isISOControl)) {
            aviso.setText("Escreva uma mensagem de 1 a 500 caracteres, em uma linha."); return;
        }
        String para = destino.getValue();
        if (mensagem.startsWith("/")) conexao.enviar(mensagem);
        else conexao.enviar("Todos".equals(para) ? mensagem : "/msg " + para + " " + mensagem);
        texto.clear(); aviso.getStyleClass().removeIf("error"::equals);
        aviso.setText("Todos".equals(para) ? "Mensagens públicas ficam salvas no servidor." : "Mensagem privada · disponível apenas nesta sessão.");
    }
    private void desconectado(String motivo) {
        conexao = null;
        if (conectado) {
            conectado = false; texto.setDisable(true); destino.setDisable(true); busca.setDisable(true);
            estadoConexao.setText("○  Desconectado\n" + endereco); participantes.getChildren().clear(); online.setText("VOCÊ ESTÁ OFFLINE");
            aviso.setText(motivo); aviso.getStyleClass().add("error"); sistema("Você está offline. Use “Sair do chat” para conectar novamente.");
        } else { erroLogin.setText(motivo); conectar.setDisable(false); conectar.setText("Conectar ao chat  →"); }
    }
    private void comando(String texto) { if (conexao != null) conexao.enviar(texto); }
    private void sair() { ConexaoChat anterior = conexao; conexao = null; if (anterior != null) anterior.close(); expirarDigitacao.stop(); mostrarLogin(""); janela.setTitle("Conexo · Chat multiusuário"); }
    @Override public void stop() { if (conexao != null) conexao.close(); }
    public static void main(String[] args) { launch(args); }
}
