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
import protocol.ComandoChat;
import model.Usuario;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;

public final class ClienteChat extends Application {
    private Stage janela;
    private Scene cena;
    private ConexaoChat conexao;
    private String apelido, endereco;
    private String salaAtualId = "geral", salaAtualNome = "Geral";
    private record ResumoSala(String id, String nome, int usuarios) {
        @Override public String toString() { return nome + " · " + usuarios + (usuarios == 1 ? " pessoa" : " pessoas"); }
    }
    private final List<ResumoSala> salasDisponiveis = new ArrayList<>();
    private ComboBox<ResumoSala> escolherSala;
    private boolean atualizandoSalas, trocandoSala;
    private boolean conectado, carregandoHistorico, atualizandoParticipantes;
    private final Animacoes animacoes = new Animacoes();
    private StackPane palco;
    private final Set<String> ids = new LinkedHashSet<>();
    private TextField nome, host, porta, busca, texto;
    private Label erroLogin, aviso, digitando, titulo, subtitulo, contador, online, estadoConexao, totalOnline;
    private Node vazio;
    private Button conectar, enviarMensagem, novasMensagens;
    private VBox mensagens, participantes;
    private ScrollPane rolagem;
    private ComboBox<String> destino;
    private HBox indicadorDigitacao, composicao;
    private ParallelTransition pontosDigitacao;
    private FadeTransition fadeDigitacao;
    private Animation pulsoConexao;
    private Circle pontoConexao;
    private Timeline rolagemSuave;
    private final PauseTransition expirarDigitacao = new PauseTransition(Duration.seconds(3));
    private long ultimaDigitacao;

    @Override public void start(Stage stage) {
        janela = stage; janela.setTitle("Conexo · Chat multiusuário");
        palco = new StackPane(); cena = new Scene(palco, 1220, 810);
        cena.getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm());
        janela.setScene(cena); janela.setMinWidth(1000); janela.setMinHeight(740);
        mostrarLogin(""); janela.show();
    }
    private Label label(String texto, String estilo) { Label l = new Label(texto); l.getStyleClass().add(estilo); return l; }
    private Button botao(String texto, String estilo) { Button b = new Button(texto); b.getStyleClass().add(estilo); b.setCursor(Cursor.HAND); animacoes.prepararBotao(b); return b; }
    private void trocar(Parent raiz) { animacoes.trocar(palco, raiz); }
    private void prepararTela() {
        animacoes.prepararTela(); expirarDigitacao.stop();
        if (pontosDigitacao != null) pontosDigitacao.stop();
        if (fadeDigitacao != null) fadeDigitacao.stop();
        if (rolagemSuave != null) rolagemSuave.stop();
    }
    private void mostrarLogin(String mensagem) {
        prepararTela();
        conectado = false;
        salasDisponiveis.clear(); trocandoSala = false;
        VBox apresentacao = new VBox(20); apresentacao.getStyleClass().add("intro"); apresentacao.setPrefWidth(490); apresentacao.setMinWidth(430);
        Label marca = label("●  conexo", "brand");
        Label selo = label("UM ESPAÇO PARA SE CONECTAR", "eyebrow");
        Label chamada = label("Boas conversas\ncomeçam com\numa conexão.", "hero");
        Label descricao = label("Perto ou longe, na mesma conversa.\nSeu chat em tempo real, simples e acolhedor.", "intro-copy");
        Pane desenho = ilustracao(); VBox.setVgrow(desenho, Priority.ALWAYS);
        Label rodape = label("Seu espaço para trocar ideias.", "intro-footer");
        apresentacao.getChildren().addAll(marca, selo, chamada, descricao, desenho, rodape);

        VBox formulario = new VBox(13); formulario.getStyleClass().add("login-form"); formulario.setMaxWidth(480); formulario.setMaxHeight(Region.USE_PREF_SIZE);
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
        Label privacidade = label("Várias salas · Troque de conversa depois de entrar", "small-muted");
        formulario.getChildren().addAll(passo, heading, explicacao, espaco(8), label("Seu apelido", "field-label"), nome, rede, erroLogin, conectar, dica, espaco(6), privacidade);
        StackPane direita = new StackPane(formulario); direita.setPadding(new Insets(28)); HBox.setHgrow(direita, Priority.ALWAYS);
        HBox raiz = new HBox(apresentacao, direita); raiz.getStyleClass().add("login-root"); trocar(raiz);
        animacoes.aparecer(chamada, 0, 18, 70); animacoes.aparecer(descricao, 0, 14, 150); animacoes.aparecer(formulario, 0, 18, 100);
        Platform.runLater(nome::requestFocus);
    }
    private Region espaco(double altura) { Region r = new Region(); r.setMinHeight(altura); return r; }
    private Pane ilustracao() {
        Pane pane = new Pane(); pane.setMinHeight(175); pane.setPrefHeight(195);
        Circle halo = new Circle(180, 80, 78, Color.web("#316459")); halo.setOpacity(.4);
        Circle orbita = new Circle(180, 80, 103, Color.TRANSPARENT); orbita.setStroke(Color.web("#436c60")); orbita.getStrokeDashArray().addAll(3.0, 9.0);
        Label a = label("Olá, que bom te ver!", "illustration-bubble"); a.relocate(18, 105);
        Label b = label("Vamos conversar?", "illustration-bubble-alt"); b.relocate(150, 18);
        Label c = label("●  Ideias que aproximam", "illustration-caption"); c.relocate(80, 158);
        Circle ponto = new Circle(294, 118, 6, Color.web("#bbeb9e"));
        pane.getChildren().addAll(halo, orbita, a, b, ponto, c);
        animacoes.flutuar(a, -7, 2.8, 0); animacoes.flutuar(b, 6, 3.4, 350); animacoes.pulsar(ponto);
        return pane;
    }
    private void conectar() {
        if (conexao != null) return;
        String usuario; String ip = host.getText().strip(); int numero;
        try { usuario = new Usuario(nome.getText()).nome(); }
        catch (IllegalArgumentException e) { erroLogin.setText(e.getMessage()); return; }
        try { numero = Integer.parseInt(porta.getText().strip()); if (numero < 1 || numero > 65535) throw new NumberFormatException(); }
        catch (NumberFormatException e) { erroLogin.setText("A porta deve ser um número entre 1 e 65535."); return; }
        if (ip.isBlank()) { erroLogin.setText("Informe o IP do servidor."); return; }
        apelido = usuario; endereco = ip + ":" + numero;
        conectar.setDisable(true); conectar.setText("Conectando…"); erroLogin.setText("");
        ProgressIndicator progresso = new ProgressIndicator(); progresso.setMaxSize(17, 17); progresso.setPrefSize(17, 17); conectar.setGraphic(progresso);
        ConexaoChat[] sessao = new ConexaoChat[1];
        sessao[0] = new ConexaoChat(evento -> Platform.runLater(() -> { if (conexao == sessao[0]) receber(evento); }),
                motivo -> Platform.runLater(() -> { if (conexao == sessao[0]) desconectado(motivo); }));
        conexao = sessao[0]; conexao.conectar(ip, numero, usuario);
    }
    private void mostrarChat() {
        prepararTela();
        ids.clear();
        BorderPane raiz = new BorderPane(); raiz.getStyleClass().add("chat-root");
        VBox lateral = new VBox(14); lateral.getStyleClass().add("sidebar"); lateral.setPrefWidth(258); lateral.setMinWidth(230);
        Label marca = label("●  conexo", "brand"); marca.setPadding(new Insets(0, 0, 24, 0));
        escolherSala = new ComboBox<>(); escolherSala.setMaxWidth(Double.MAX_VALUE); escolherSala.setPromptText("Carregando salas…"); escolherSala.setId("salas"); escolherSala.setAccessibleText("Escolher sala do servidor"); escolherSala.getStyleClass().add("room-picker");
        escolherSala.setOnAction(e -> { if (!atualizandoSalas) trocarSala(); });
        preencherSalas();
        Button geral = botao("#   " + salaAtualNome, "channel"); geral.setMaxWidth(Double.MAX_VALUE); geral.setOnAction(e -> { destino.setValue("Todos"); comando("/history 50"); });
        online = label("PARTICIPANTES", "sidebar-label"); participantes = new VBox(6);
        ScrollPane lista = new ScrollPane(participantes); lista.getStyleClass().add("people-scroll"); lista.setFitToWidth(true); VBox.setVgrow(lista, Priority.ALWAYS);
        Label voce = label(apelido + "  ·  você", "sidebar-name");
        estadoConexao = label("●  Conectado\n" + endereco + "\nSala: " + salaAtualNome, "connection"); estadoConexao.setWrapText(true);
        Button copiar = botao("Copiar endereço", "sidebar-button"); copiar.setOnAction(e -> { ClipboardContent c = new ClipboardContent(); c.putString(endereco); Clipboard.getSystemClipboard().setContent(c); aviso.setText("Endereço copiado. Entre nesse servidor e escolha a sala " + salaAtualNome + "."); });
        Button sair = botao("Sair do chat  ↗", "sidebar-button"); sair.setOnAction(e -> sair());
        lateral.getChildren().addAll(marca, label("SALAS DO SERVIDOR", "sidebar-label"), escolherSala, geral, espaco(6), online, lista, voce, estadoConexao, copiar, sair);
        raiz.setLeft(lateral);

        VBox principal = new VBox();
        titulo = label("Sala " + salaAtualNome, "chat-title"); subtitulo = label("Um lugar para compartilhar ideias e se encontrar.", "muted");
        VBox titulos = new VBox(5, titulo, subtitulo); HBox.setHgrow(titulos, Priority.ALWAYS);
        totalOnline = label("Conectado", "status-text"); pontoConexao = new Circle(4, Color.web("#397c54"));
        HBox status = new HBox(7, pontoConexao, totalOnline); status.setAlignment(Pos.CENTER); status.getStyleClass().add("status-pill");
        pulsoConexao = animacoes.pulsar(pontoConexao);
        HBox header = new HBox(12, titulos, status); header.getStyleClass().add("chat-header"); header.setAlignment(Pos.CENTER_LEFT);
        busca = new TextField(); busca.setPromptText("Buscar no histórico público…"); busca.setId("busca"); HBox.setHgrow(busca, Priority.ALWAYS);
        Button pesquisar = botao("Buscar", "secondary"); pesquisar.setOnAction(e -> buscar()); busca.setOnAction(e -> buscar());
        Button voltar = botao("Últimas mensagens", "secondary"); voltar.setOnAction(e -> { busca.clear(); comando("/history 50"); });
        HBox ferramentas = new HBox(9, busca, pesquisar, voltar); ferramentas.getStyleClass().add("toolbar");
        mensagens = new VBox(14); mensagens.getStyleClass().add("messages");
        rolagem = new ScrollPane(mensagens); rolagem.setFitToWidth(true); rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); rolagem.getStyleClass().add("message-scroll"); VBox.setVgrow(rolagem, Priority.ALWAYS);
        rolagem.setOnScroll(e -> { if (e.getDeltaY() > 0 && rolagemSuave != null) rolagemSuave.stop(); });
        novasMensagens = botao("↓  Novas mensagens", "new-messages"); novasMensagens.setVisible(false); novasMensagens.setOnAction(e -> rolar());
        StackPane conversa = new StackPane(rolagem, novasMensagens); VBox.setVgrow(conversa, Priority.ALWAYS);
        StackPane.setAlignment(novasMensagens, Pos.BOTTOM_RIGHT); StackPane.setMargin(novasMensagens, new Insets(0, 25, 18, 0));
        rolagem.vvalueProperty().addListener((obs, antes, depois) -> { if (depois.doubleValue() > .98) novasMensagens.setVisible(false); });
        digitando = label("", "typing"); indicadorDigitacao = criarIndicadorDigitacao(); expirarDigitacao.setOnFinished(e -> ocultarDigitacao());
        aviso = label("Públicas ficam no histórico. Privadas duram apenas esta sessão.", "small-muted"); aviso.setWrapText(true);
        destino = new ComboBox<>(); destino.getItems().add("Todos"); destino.setValue("Todos"); destino.setPrefWidth(160); destino.setAccessibleText("Destinatário da mensagem");
        texto = new TextField(); texto.setPromptText("Escreva uma mensagem…"); texto.setId("mensagem"); HBox.setHgrow(texto, Priority.ALWAYS); texto.setOnAction(e -> enviar());
        contador = label("0 / 500", "counter");
        enviarMensagem = botao("Enviar  ↑", "primary"); enviarMensagem.setOnAction(e -> enviar()); enviarMensagem.setId("enviar"); enviarMensagem.setDisable(true);
        texto.textProperty().addListener((obs, antes, depois) -> {
            contador.setText(depois.length() + " / 500"); contador.getStyleClass().remove("over-limit");
            if (depois.length() > 500) contador.getStyleClass().add("over-limit");
            enviarMensagem.setDisable(depois.isBlank() || depois.length() > 500 || !conectado);
            long agora = System.nanoTime();
            if (!depois.isBlank() && "Todos".equals(destino.getValue()) && agora - ultimaDigitacao > 1_500_000_000L && conexao != null) { ultimaDigitacao = agora; conexao.enviar("/typing"); }
        });
        composicao = new HBox(10, destino, texto, enviarMensagem); composicao.setAlignment(Pos.CENTER_LEFT); composicao.getStyleClass().add("compose-box");
        destino.setOnAction(e -> { if (!atualizandoParticipantes) atualizarDestino(); });
        Label ajuda = label("Enter para enviar  ·  / para ver ajuda", "small-muted"); Region respiro = new Region(); HBox.setHgrow(respiro, Priority.ALWAYS);
        Button ajudaComandos = botao("Comandos /", "command-help"); ajudaComandos.setOnAction(e -> comando("/help"));
        HBox legenda = new HBox(10, ajuda, respiro, ajudaComandos, contador); legenda.setAlignment(Pos.CENTER_LEFT);
        VBox rodape = new VBox(9, indicadorDigitacao, aviso, composicao, legenda); rodape.getStyleClass().add("composer");
        principal.getChildren().addAll(header, ferramentas, conversa, rodape); raiz.setCenter(principal);
        trocar(raiz); janela.setTitle("Conexo · " + salaAtualNome + " · " + apelido + " · " + endereco); Platform.runLater(texto::requestFocus);
        animacoes.aparecer(lateral, -14, 0, 30); animacoes.aparecer(header, 0, 10, 70); animacoes.aparecer(rodape, 0, 16, 130);
    }
    private void receber(Evento evento) {
        switch (evento.tipo()) {
            case BEM_VINDO -> {
                if (evento.campos().size() >= 3) { salaAtualId = evento.campo(1); salaAtualNome = evento.campo(2); }
                else { salaAtualId = "geral"; salaAtualNome = "Geral"; }
                trocandoSala = false; conectado = true; mostrarChat();
            }
            case ERRO -> {
                if (conectado) {
                    aviso.setText(evento.campo(0)); aviso.getStyleClass().add("error");
                    respostaComando("Não foi possível executar", evento.campo(0), true);
                    if (trocandoSala) {
                        trocandoSala = false; preencherSalas(); texto.setDisable(false); destino.setDisable(false);
                        enviarMensagem.setDisable(texto.getText().isBlank() || texto.getText().length() > 500);
                    }
                }
                else { erroLogin.setText(evento.campo(0)); animacoes.aparecer(erroLogin, 0, 4, 0); ConexaoChat anterior = conexao; conexao = null; anterior.close(); restaurarBotaoConectar(); }
            }
            case HISTORICO_INICIO -> { carregandoHistorico = true; mensagens.getChildren().clear(); ids.clear(); vazio = null; subtitulo.setText(evento.campo(0)); }
            case HISTORICO_FIM -> {
                carregandoHistorico = false;
                if (mensagens.getChildren().isEmpty()) {
                    sistema("Ainda não há mensagens por aqui. Que tal começar a conversa?"); vazio = mensagens.getChildren().get(0);
                }
                aviso.setText("Histórico carregado: " + evento.campo(0) + " mensagem(ns) na sala " + salaAtualNome + ".");
                animacoes.aparecer(mensagens, 0, 6, 0); rolar();
            }
            case MENSAGEM -> {
                if (!ids.add(evento.campo(0))) break;
                if (ids.size() > 1000) ids.remove(ids.iterator().next());
                mensagem(evento.campo(1), evento.campo(2), evento.campo(3), null);
                ocultarDigitacao();
            }
            case PRIVADA -> mensagem(evento.campo(0), evento.campo(2), evento.campo(3), evento.campo(1));
            case SISTEMA -> {
                if (evento.campos().size() >= 2) {
                    respostaComando(evento.campo(1), evento.campo(0), false);
                    aviso.setText("Resposta do comando exibida na conversa.");
                } else sistema(evento.campo(0));
            }
            case USUARIOS -> {
                participantes.getChildren().clear(); online.setText("PARTICIPANTES  ·  " + evento.campos().size());
                totalOnline.setText(evento.campos().size() == 1 ? "1 pessoa online" : evento.campos().size() + " pessoas online");
                atualizandoParticipantes = true;
                String selecionado = destino.getValue(); destino.getItems().setAll("Todos");
                for (String usuario : evento.campos()) {
                    boolean souEu = usuario.equalsIgnoreCase(apelido);
                    Button pessoa = botao("", "person"); pessoa.setMaxWidth(Double.MAX_VALUE); pessoa.setUserData(usuario); pessoa.setAccessibleText(usuario + (souEu ? ", você" : ", enviar mensagem privada"));
                    Label avatar = avatar(usuario); avatar.getStyleClass().add("person-avatar");
                    Label nomePessoa = label(usuario, "person-name"); nomePessoa.setMaxWidth(136); nomePessoa.setTextOverrun(OverrunStyle.ELLIPSIS);
                    VBox detalhes = new VBox(3, nomePessoa, label(souEu ? "Você" : "Disponível", "person-status"));
                    HBox pessoaVisual = new HBox(10, avatar, detalhes); pessoaVisual.setAlignment(Pos.CENTER_LEFT); pessoa.setGraphic(pessoaVisual);
                    pessoa.setOnAction(e -> destino.setValue(souEu ? "Todos" : usuario)); participantes.getChildren().add(pessoa);
                    animacoes.aparecer(pessoa, -8, 0, Math.min(participantes.getChildren().size() * 25, 200));
                    if (!souEu) destino.getItems().add(usuario);
                }
                destino.setValue(selecionado == null ? "Todos" : selecionado);
                if (selecionado != null && !destino.getItems().contains(selecionado)) {
                    aviso.setText(selecionado + " está offline. Escolha outra pessoa ou selecione Todos para conversar em público.");
                }
                atualizandoParticipantes = false; destacarDestino();
            }
            case DIGITANDO -> { mostrarDigitacao(evento.campo(0)); expirarDigitacao.playFromStart(); }
            case SALAS -> {
                salasDisponiveis.clear();
                for (int i = 1; i + 2 < evento.campos().size(); i += 3) {
                    salasDisponiveis.add(new ResumoSala(evento.campo(i), evento.campo(i + 1), Integer.parseInt(evento.campo(i + 2))));
                }
                preencherSalas();
            }
            case PONG -> { }
        }
    }
    private void mensagem(String remetente, String conteudo, String data, String privadoPara) {
        if (vazio != null) { mensagens.getChildren().remove(vazio); vazio = null; }
        boolean minha = remetente.equalsIgnoreCase(apelido);
        String hora = LocalDateTime.parse(data).format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"));
        Label autor = label(minha ? "Você" : remetente, "message-author");
        Label meta = label(hora, "message-meta");
        HBox cabecalho = new HBox(10, autor, meta); cabecalho.setAlignment(Pos.CENTER_LEFT);
        Label corpo = label(conteudo, "message-body"); corpo.setWrapText(true); corpo.setMaxWidth(520);
        VBox bolha = new VBox(8, cabecalho, corpo); bolha.getStyleClass().add(minha ? "bubble-own" : "bubble"); bolha.setMaxWidth(560);
        if (privadoPara != null) {
            bolha.getStyleClass().add("bubble-private");
            Label privada = label(minha ? "Privada para " + privadoPara : "Conversa privada · só para você", "private-label");
            bolha.getChildren().add(0, privada);
        }
        ContextMenu menu = new ContextMenu(); MenuItem copiar = new MenuItem("Copiar mensagem");
        copiar.setOnAction(e -> { ClipboardContent c = new ClipboardContent(); c.putString(conteudo); Clipboard.getSystemClipboard().setContent(c); }); menu.getItems().add(copiar);
        bolha.setOnContextMenuRequested(e -> menu.show(bolha, e.getScreenX(), e.getScreenY()));
        HBox linha = new HBox(10); linha.setAlignment(minha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if (!minha) linha.getChildren().add(avatar(remetente));
        linha.getChildren().add(bolha); linha.setUserData(minha ? 22.0 : -22.0); adicionar(linha);
    }
    private void sistema(String texto) { Label l = label(texto, "system-message"); l.setWrapText(true); l.setMaxWidth(Double.MAX_VALUE); l.setAlignment(Pos.CENTER); adicionar(l); }
    private void respostaComando(String titulo, String conteudo, boolean erro) {
        if (vazio != null) { mensagens.getChildren().remove(vazio); vazio = null; }
        Label cabecalho = label(titulo, "command-title"); Label corpo = label(conteudo, "command-body");
        corpo.setWrapText(true); corpo.setMaxWidth(560);
        VBox cartao = new VBox(10, cabecalho, corpo, label("Visível apenas para você", "small-muted"));
        cartao.getStyleClass().add("command-reply"); cartao.setMaxWidth(600);
        if (erro) cartao.getStyleClass().add("command-error");
        ContextMenu menu = new ContextMenu(); MenuItem copiar = new MenuItem("Copiar resposta");
        copiar.setOnAction(e -> { ClipboardContent c = new ClipboardContent(); c.putString(conteudo); Clipboard.getSystemClipboard().setContent(c); }); menu.getItems().add(copiar);
        cartao.setOnContextMenuRequested(e -> menu.show(cartao, e.getScreenX(), e.getScreenY()));
        HBox linha = new HBox(cartao); linha.setAlignment(Pos.CENTER_LEFT); adicionar(linha); rolar();
    }
    private void adicionar(Node item) {
        boolean noFim = rolagem.getVvalue() > .93 || mensagens.getChildren().size() < 5
                || rolagemSuave != null && rolagemSuave.getStatus() == Animation.Status.RUNNING;
        mensagens.getChildren().add(item);
        if (mensagens.getChildren().size() > 500) mensagens.getChildren().remove(0);
        if (!carregandoHistorico) {
            animacoes.aparecer(item, item.getUserData() instanceof Double x ? x : 0, 6, 0);
            if (noFim) rolar();
            else if (!novasMensagens.isVisible()) { novasMensagens.setVisible(true); animacoes.aparecer(novasMensagens, 0, 8, 0); }
        }
    }
    private void rolar() {
        VBox conversaAtual = mensagens;
        Platform.runLater(() -> {
            if (!conectado || conversaAtual != mensagens) return;
            if (rolagemSuave != null) rolagemSuave.stop();
            mensagens.applyCss(); mensagens.layout(); rolagem.layout();
            novasMensagens.setVisible(false);
            rolagemSuave = new Timeline(new KeyFrame(Duration.millis(260), new KeyValue(rolagem.vvalueProperty(), 1, Interpolator.EASE_BOTH)));
            rolagemSuave.play();
        });
    }
    private Label avatar(String usuario) {
        String[] partes = usuario.strip().split(" +");
        String iniciais = primeiraLetra(partes[0]) + (partes.length > 1 ? primeiraLetra(partes[partes.length - 1]) : "");
        Label avatar = label(iniciais.toUpperCase(Locale.ROOT), "avatar");
        avatar.getStyleClass().add("avatar-" + Math.floorMod(usuario.toLowerCase(Locale.ROOT).hashCode(), 4));
        return avatar;
    }
    private String primeiraLetra(String texto) { return texto.substring(0, texto.offsetByCodePoints(0, 1)); }
    private void preencherSalas() {
        atualizandoSalas = true;
        try {
            escolherSala.getItems().setAll(salasDisponiveis);
            escolherSala.getSelectionModel().select(salasDisponiveis.stream().filter(s -> s.id().equals(salaAtualId)).findFirst().orElse(null));
            escolherSala.setDisable(trocandoSala || salasDisponiveis.size() <= 1);
        } finally { atualizandoSalas = false; }
    }
    private void trocarSala() {
        ResumoSala proxima = escolherSala.getValue();
        if (conexao == null || !conectado || trocandoSala || proxima == null || proxima.id().equals(salaAtualId)) return;
        trocandoSala = true; escolherSala.setDisable(true); texto.setDisable(true); destino.setDisable(true); enviarMensagem.setDisable(true);
        aviso.getStyleClass().removeIf("error"::equals); aviso.setText("Entrando na sala " + proxima.nome() + "…");
        conexao.enviar("/join " + proxima.id());
    }
    private void atualizarDestino() {
        destacarDestino();
        boolean publica = "Todos".equals(destino.getValue());
        aviso.setText(publica ? "Públicas ficam no histórico. Privadas duram apenas esta sessão." : "Privada para " + destino.getValue() + " · só vocês dois recebem.");
        animacoes.aparecer(aviso, 0, 3, 0); texto.requestFocus();
    }
    private void destacarDestino() {
        boolean publica = destino.getValue() == null || "Todos".equals(destino.getValue());
        texto.setPromptText(publica ? "Compartilhe uma ideia…" : "Mensagem privada para " + destino.getValue() + "…");
        composicao.getStyleClass().remove("private-mode");
        if (!publica) composicao.getStyleClass().add("private-mode");
        for (Node node : participantes.getChildren()) {
            node.getStyleClass().remove("person-selected");
            if (!publica && destino.getValue().equals(node.getUserData())) node.getStyleClass().add("person-selected");
        }
    }
    private HBox criarIndicadorDigitacao() {
        HBox pontos = new HBox(4); pontos.setAlignment(Pos.CENTER_LEFT);
        pontosDigitacao = new ParallelTransition();
        for (int i = 0; i < 3; i++) {
            Circle ponto = new Circle(2.5, Color.web("#699375")); pontos.getChildren().add(ponto);
            var mover = new TranslateTransition(Duration.millis(340), ponto);
            mover.setFromY(1); mover.setToY(-3); mover.setAutoReverse(true); mover.setCycleCount(Animation.INDEFINITE); mover.setDelay(Duration.millis(i * 130));
            pontosDigitacao.getChildren().add(mover);
        }
        HBox painel = new HBox(10, pontos, digitando); painel.setAlignment(Pos.CENTER_LEFT); painel.setMinHeight(22); painel.setVisible(false);
        return painel;
    }
    private void mostrarDigitacao(String usuario) {
        if (fadeDigitacao != null) fadeDigitacao.stop();
        digitando.setText(usuario + " está digitando");
        boolean estavaVisivel = indicadorDigitacao.isVisible(); indicadorDigitacao.setVisible(true);
        if (!estavaVisivel) { indicadorDigitacao.setOpacity(0); pontosDigitacao.playFromStart(); }
        fadeDigitacao = new FadeTransition(Duration.millis(160), indicadorDigitacao); fadeDigitacao.setToValue(1); fadeDigitacao.play();
    }
    private void ocultarDigitacao() {
        expirarDigitacao.stop();
        if (indicadorDigitacao == null || !indicadorDigitacao.isVisible()) return;
        if (fadeDigitacao != null) fadeDigitacao.stop();
        fadeDigitacao = new FadeTransition(Duration.millis(160), indicadorDigitacao); fadeDigitacao.setToValue(0);
        fadeDigitacao.setOnFinished(e -> { indicadorDigitacao.setVisible(false); pontosDigitacao.stop(); }); fadeDigitacao.play();
    }
    private void restaurarBotaoConectar() { conectar.setDisable(false); conectar.setGraphic(null); conectar.setText("Conectar ao chat  →"); }
    private void buscar() {
        if (conexao == null) return;
        String termo = busca.getText().strip();
        if (termo.isBlank() || termo.length() > 100) { aviso.setText("Digite de 1 a 100 caracteres para buscar no histórico."); return; }
        conexao.enviar("/buscar " + termo);
    }
    private void enviar() {
        if (conexao == null || !conectado) return;
        String mensagem = ComandoChat.aparar(texto.getText());
        if (mensagem.isBlank() || mensagem.length() > 500 || mensagem.chars().anyMatch(Character::isISOControl)) {
            aviso.setText("Escreva uma mensagem de 1 a 500 caracteres, em uma linha."); return;
        }
        String para = destino.getValue();
        var pedido = ComandoChat.ler(mensagem);
        if (pedido.isPresent()) conexao.enviar(pedido.get().paraLinha());
        else conexao.enviar("Todos".equals(para) ? mensagem : "/msg \"" + para + "\" " + mensagem);
        texto.clear(); aviso.getStyleClass().removeIf("error"::equals);
        aviso.setText(pedido.isPresent() ? "Comando enviado: " + pedido.get().nome() : "Todos".equals(para) ? "Mensagens públicas ficam salvas no servidor." : "Mensagem privada · disponível apenas nesta sessão.");
    }
    private void desconectado(String motivo) {
        conexao = null;
        if (conectado) {
            conectado = false; texto.setDisable(true); destino.setDisable(true); busca.setDisable(true);
            enviarMensagem.setDisable(true); escolherSala.setDisable(true); trocandoSala = false; ocultarDigitacao(); pulsoConexao.stop(); pontoConexao.setFill(Color.web("#b97964")); totalOnline.setText("Offline");
            estadoConexao.setText("○  Desconectado\n" + endereco); participantes.getChildren().clear(); online.setText("VOCÊ ESTÁ OFFLINE");
            aviso.setText(motivo); aviso.getStyleClass().add("error"); sistema("Você está offline. Use “Sair do chat” para conectar novamente.");
        } else { erroLogin.setText(motivo); animacoes.aparecer(erroLogin, 0, 4, 0); restaurarBotaoConectar(); }
    }
    private void comando(String texto) { if (conexao != null) conexao.enviar(texto); }
    private void sair() { ConexaoChat anterior = conexao; conexao = null; if (anterior != null) anterior.close(); expirarDigitacao.stop(); mostrarLogin(""); janela.setTitle("Conexo · Chat multiusuário"); }
    @Override public void stop() { prepararTela(); if (conexao != null) conexao.close(); }
    public static void main(String[] args) { launch(args); }
}
