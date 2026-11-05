package client;

import atlantafx.base.theme.PrimerLight;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.util.Duration;
import protocol.Evento;
import protocol.ComandoChat;
import model.Usuario;
import org.kordamp.ikonli.feather.Feather;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private boolean conectado, carregandoHistorico, atualizandoParticipantes, encerrando;
    private final Animacoes animacoes = new Animacoes();
    private final AnimacaoJanela animacaoJanela = new AnimacaoJanela();
    private StackPane palco;
    private final Set<String> ids = new LinkedHashSet<>();
    private TextField nome, host, porta, busca, texto;
    private Label erroLogin, aviso, titulo, subtitulo, contador, online, estadoConexao, totalOnline;
    private Node vazio;
    private Button conectar, enviarMensagem, novasMensagens;
    private VBox mensagens, participantes, fluxoConversa, digitacoesRemotas;
    private BalaoRascunho previa;
    private record DigitacaoAtiva(BalaoDigitacao balao, PauseTransition expirar) { }
    private final Map<String, DigitacaoAtiva> digitacoes = new LinkedHashMap<>();
    private ScrollPane rolagem;
    private ComboBox<String> destino;
    private HBox composicao;
    private Animation pulsoConexao;
    private Circle pontoConexao;
    private Timeline rolagemSuave;
    private final PauseTransition pausaDigitacaoLocal = new PauseTransition(Duration.seconds(2.2));
    private boolean digitacaoEnviada, rolagemAgendada;
    private long ultimaDigitacao;

    @Override public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        janela = stage; janela.setTitle("Conexo · Chat multiusuário");
        var area = Screen.getPrimary().getVisualBounds();
        palco = new StackPane(); cena = new Scene(palco, Math.min(1280, area.getWidth()-48), Math.min(850, area.getHeight()-70));
        cena.getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm());
        janela.setScene(cena); janela.setMinWidth(Math.min(1040, area.getWidth()-24)); janela.setMinHeight(Math.min(760, area.getHeight()-40));
        pausaDigitacaoLocal.setOnFinished(e -> pararDigitacaoLocal());
        cena.addEventFilter(InputEvent.ANY, e -> { if (encerrando) e.consume(); });
        janela.setOnCloseRequest(e -> { e.consume(); fecharJanela(); });
        mostrarLogin(""); palco.applyCss(); palco.layout();
        animacaoJanela.preparar(janela, palco); janela.show(); animacaoJanela.abrir();
    }
    private Label label(String texto, String estilo) { Label l = new Label(texto); l.getStyleClass().add(estilo); return l; }
    private Button botao(String texto, String estilo) { Button b = new Button(texto); b.getStyleClass().add(estilo); b.setCursor(Cursor.HAND); animacoes.prepararBotao(b); return b; }
    private Button botao(String texto, String estilo, Feather icone) {
        Button b = botao(texto, estilo); b.setGraphic(Icones.criar(icone, 18)); b.setGraphicTextGap(9); return b;
    }
    private Label marca() {
        Label marca = label("conexo", "brand"); marca.setGraphic(Icones.criar(Feather.MESSAGE_CIRCLE, 30)); marca.setGraphicTextGap(12); return marca;
    }
    private void trocar(Parent raiz) {
        if (janela.isShowing()) animacoes.trocar(palco, raiz);
        else palco.getChildren().setAll(raiz);
    }
    private void prepararTela() {
        pararEfeitos(); limparDigitacoes(); if (previa != null) previa.limpar();
    }
    private void pararEfeitos() {
        animacoes.prepararTela(); pausaDigitacaoLocal.stop(); digitacaoEnviada = false;
        digitacoes.values().forEach(d -> { d.expirar().stop(); d.balao().parar(); });
        if (previa != null) previa.parar();
        rolagemAgendada = false;
        if (rolagemSuave != null) rolagemSuave.stop();
    }
    private void mostrarLogin(String mensagem) {
        prepararTela();
        conectado = false;
        salasDisponiveis.clear(); trocandoSala = false;
        VBox apresentacao = new VBox(18); apresentacao.getStyleClass().add("intro"); apresentacao.setPrefWidth(470); apresentacao.setMinWidth(410);
        Label marca = marca();
        Label selo = label("UM ESPAÇO PARA SE CONECTAR", "eyebrow");
        Label chamada = label("Boas conversas\ncomeçam com\numa conexão.", "hero");
        Label descricao = label("Na mesma rede, na mesma conversa.\nSeu chat em tempo real, simples e acolhedor.", "intro-copy");
        Pane desenho = ilustracao(); VBox.setVgrow(desenho, Priority.ALWAYS);
        Label rodape = label("Seu espaço para trocar ideias.", "intro-footer");
        apresentacao.getChildren().addAll(marca, selo, chamada, descricao, desenho, rodape);

        VBox formulario = new VBox(12); formulario.getStyleClass().add("login-form"); formulario.setMaxWidth(540); formulario.setMaxHeight(Region.USE_PREF_SIZE);
        Label passo = label("OLÁ, QUE BOM TER VOCÊ AQUI", "eyebrow-green");
        Label heading = label("Entre na conversa", "login-title");
        Label explicacao = label("Escolha seu apelido e o endereço do servidor.\nCada pessoa entra pelo próprio computador.", "muted");
        explicacao.setWrapText(true);
        nome = new TextField(apelido == null ? "" : apelido); nome.setPromptText("Como você quer ser chamado?"); nome.setId("apelido");
        host = new TextField("127.0.0.1"); host.setPromptText("Ex.: 192.168.1.10"); host.setId("ip");
        porta = new TextField("5050"); porta.setId("porta"); porta.setPrefWidth(100); porta.setMaxWidth(110);
        VBox campoHost = new VBox(7, label("IP ou nome do servidor", "field-label"), host); HBox.setHgrow(campoHost, Priority.ALWAYS);
        HBox rede = new HBox(12, campoHost, new VBox(7, label("Porta", "field-label"), porta));
        erroLogin = label(mensagem, "error"); erroLogin.setWrapText(true);
        erroLogin.managedProperty().bind(erroLogin.textProperty().isNotEmpty()); erroLogin.visibleProperty().bind(erroLogin.managedProperty());
        conectar = botao("Conectar ao chat", "primary", Feather.ARROW_RIGHT); conectar.setContentDisplay(ContentDisplay.RIGHT); conectar.setMaxWidth(Double.MAX_VALUE); conectar.setDefaultButton(true); conectar.setId("conectar");
        conectar.setOnAction(e -> conectar()); nome.setOnAction(e -> conectar());
        Label dica = label("No mesmo computador, use 127.0.0.1.\nEm outra máquina, use o IP exibido pelo servidor.", "small-muted"); dica.setWrapText(true);
        Label privacidade = label("Várias salas · Uma rede de conversas", "small-muted"); privacidade.setGraphic(Icones.criar(Feather.HASH, 14));
        formulario.getChildren().addAll(passo, heading, explicacao, espaco(8), label("Seu apelido", "field-label"), nome, rede, erroLogin, conectar, dica, espaco(6), privacidade);
        StackPane direita = new StackPane(formulario); direita.setPadding(new Insets(24)); HBox.setHgrow(direita, Priority.ALWAYS);
        HBox raiz = new HBox(apresentacao, direita); raiz.getStyleClass().add("login-root"); trocar(raiz);
        if (janela.isShowing()) animacoes.aparecer(formulario, 0, 8, 45);
        Platform.runLater(nome::requestFocus);
    }
    private Region espaco(double altura) { Region r = new Region(); r.setMinHeight(altura); return r; }
    private Pane ilustracao() {
        Pane pane = new Pane(); pane.setMinHeight(150); pane.setPrefHeight(180);
        Circle halo = new Circle(180, 80, 78, Color.web("#316459")); halo.setOpacity(.4);
        Circle orbita = new Circle(180, 80, 103, Color.TRANSPARENT); orbita.setStroke(Color.web("#436c60")); orbita.getStrokeDashArray().addAll(3.0, 9.0);
        Label a = label("Olá, que bom te ver!", "illustration-bubble"); a.relocate(18, 105);
        Label b = label("Vamos conversar?", "illustration-bubble-alt"); b.relocate(150, 18);
        Label c = label("●  Ideias que aproximam", "illustration-caption"); c.relocate(80, 158);
        Circle ponto = new Circle(294, 118, 6, Color.web("#bbeb9e"));
        pane.getChildren().addAll(halo, orbita, a, b, ponto, c);
        animacoes.flutuar(a, -3, 4.8, 0); animacoes.flutuar(b, 3, 5.4, 350); animacoes.pulsar(ponto);
        return pane;
    }
    private void conectar() {
        if (conexao != null || encerrando) return;
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
        sessao[0] = new ConexaoChat(evento -> Platform.runLater(() -> { if (!encerrando && conexao == sessao[0]) receber(evento); }),
                motivo -> Platform.runLater(() -> { if (!encerrando && conexao == sessao[0]) desconectado(motivo); }));
        conexao = sessao[0]; conexao.conectar(ip, numero, usuario);
    }
    private void mostrarChat() {
        prepararTela();
        ids.clear();
        BorderPane raiz = new BorderPane(); raiz.getStyleClass().add("chat-root");
        VBox lateral = new VBox(10); lateral.getStyleClass().add("sidebar"); lateral.setPrefWidth(278); lateral.setMinWidth(250);
        Label marca = marca(); marca.setPadding(new Insets(0, 0, 16, 0));
        escolherSala = new ComboBox<>(); escolherSala.setMaxWidth(Double.MAX_VALUE); escolherSala.setPromptText("Carregando salas…"); escolherSala.setId("salas"); escolherSala.setAccessibleText("Escolher sala do servidor"); escolherSala.getStyleClass().add("room-picker");
        escolherSala.setOnAction(e -> { if (!atualizandoSalas) trocarSala(); });
        preencherSalas();
        Button geral = botao(salaAtualNome, "channel", Feather.HASH); geral.setMaxWidth(Double.MAX_VALUE); geral.setOnAction(e -> { destino.setValue("Todos"); comando("/history 50"); });
        geral.setTooltip(new Tooltip("Mostrar as últimas mensagens da sala"));
        online = label("PARTICIPANTES", "sidebar-label"); participantes = new VBox(6);
        ScrollPane lista = new ScrollPane(participantes); lista.getStyleClass().add("people-scroll"); lista.setFitToWidth(true); VBox.setVgrow(lista, Priority.ALWAYS);
        Label voce = label(apelido + "  ·  você", "sidebar-name"); voce.setWrapText(true);
        estadoConexao = label("Conectado\n" + endereco + "\nSala: " + salaAtualNome, "connection"); estadoConexao.setGraphic(Icones.criar(Feather.WIFI, 15)); estadoConexao.setWrapText(true);
        Button copiar = botao("Copiar endereço", "sidebar-button", Feather.COPY); copiar.setOnAction(e -> { ClipboardContent c = new ClipboardContent(); c.putString(endereco); Clipboard.getSystemClipboard().setContent(c); aviso.setText("Endereço copiado. Entre nesse servidor e escolha a sala " + salaAtualNome + "."); animacoes.aparecer(aviso, 0, 4, 0); });
        Button sair = botao("Sair do chat", "sidebar-button", Feather.LOG_OUT); sair.setOnAction(e -> sair());
        lateral.getChildren().addAll(marca, label("SALAS DO SERVIDOR", "sidebar-label"), escolherSala, geral, espaco(6), online, lista, voce, estadoConexao, copiar, sair);
        raiz.setLeft(lateral);

        VBox principal = new VBox();
        titulo = label("Sala " + salaAtualNome, "chat-title"); subtitulo = label("Um lugar para compartilhar ideias e se encontrar.", "muted");
        subtitulo.setWrapText(true);
        VBox titulos = new VBox(5, titulo, subtitulo); HBox.setHgrow(titulos, Priority.ALWAYS);
        totalOnline = label("Conectado", "status-text"); pontoConexao = new Circle(4, Color.web("#397c54"));
        HBox status = new HBox(7, pontoConexao, totalOnline); status.setAlignment(Pos.CENTER); status.getStyleClass().add("status-pill");
        pulsoConexao = animacoes.pulsar(pontoConexao);
        HBox header = new HBox(12, titulos, status); header.getStyleClass().add("chat-header"); header.setAlignment(Pos.CENTER_LEFT);
        busca = new TextField(); busca.setPromptText("Buscar no histórico público…"); busca.setId("busca"); HBox.setHgrow(busca, Priority.ALWAYS);
        Button pesquisar = botao("Buscar", "secondary", Feather.SEARCH); pesquisar.setOnAction(e -> buscar()); busca.setOnAction(e -> buscar());
        Button voltar = botao("Histórico", "secondary", Feather.CLOCK); voltar.setTooltip(new Tooltip("Mostrar as últimas mensagens da sala")); voltar.setOnAction(e -> { busca.clear(); comando("/history 50"); });
        HBox ferramentas = new HBox(9, busca, pesquisar, voltar); ferramentas.getStyleClass().add("toolbar");
        mensagens = new VBox(14); mensagens.getStyleClass().add("messages");
        digitacoesRemotas = new VBox(10); digitacoesRemotas.setManaged(false); digitacoesRemotas.setVisible(false);
        previa = new BalaoRascunho();
        fluxoConversa = new VBox(14, mensagens, digitacoesRemotas, previa); fluxoConversa.getStyleClass().add("conversation-stream");
        rolagem = new ScrollPane(fluxoConversa); rolagem.setFitToWidth(true); rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); rolagem.getStyleClass().add("message-scroll"); VBox.setVgrow(rolagem, Priority.ALWAYS);
        rolagem.setOnScroll(e -> { if (e.getDeltaY() > 0 && rolagemSuave != null) rolagemSuave.stop(); });
        novasMensagens = botao("Novas mensagens", "new-messages", Feather.ARROW_DOWN); novasMensagens.setVisible(false); novasMensagens.setOnAction(e -> rolar());
        StackPane conversa = new StackPane(rolagem, novasMensagens); VBox.setVgrow(conversa, Priority.ALWAYS);
        StackPane.setAlignment(novasMensagens, Pos.BOTTOM_RIGHT); StackPane.setMargin(novasMensagens, new Insets(0, 25, 18, 0));
        rolagem.vvalueProperty().addListener((obs, antes, depois) -> { if (depois.doubleValue() > .98) novasMensagens.setVisible(false); });
        aviso = label("Públicas ficam no histórico. Privadas duram apenas esta sessão.", "small-muted"); aviso.setWrapText(true);
        destino = new ComboBox<>(); destino.getItems().add("Todos"); destino.setValue("Todos"); destino.setPrefWidth(165); destino.setMinWidth(130); destino.setAccessibleText("Destinatário da mensagem");
        texto = new TextField(); texto.setPromptText("Escreva uma mensagem…"); texto.setId("mensagem"); HBox.setHgrow(texto, Priority.ALWAYS); texto.setOnAction(e -> enviar());
        contador = label("0 / 500", "counter");
        enviarMensagem = botao("Enviar", "primary", Feather.SEND); enviarMensagem.setContentDisplay(ContentDisplay.RIGHT); enviarMensagem.setOnAction(e -> enviar()); enviarMensagem.setId("enviar"); enviarMensagem.setDisable(true);
        texto.textProperty().addListener((obs, antes, depois) -> {
            contador.setText(depois.length() + " / 500"); contador.getStyleClass().remove("over-limit");
            if (depois.length() > 500) contador.getStyleClass().add("over-limit");
            enviarMensagem.setDisable(depois.isBlank() || depois.length() > 500 || !conectado);
            atualizarRascunho(); sinalizarDigitacao();
        });
        composicao = new HBox(10, destino, texto, enviarMensagem); composicao.setAlignment(Pos.CENTER_LEFT); composicao.getStyleClass().add("compose-box");
        destino.setOnAction(e -> { if (!atualizandoParticipantes) atualizarDestino(); });
        Label ajuda = label("Enter para enviar · / para ajuda", "small-muted"); Region respiro = new Region(); HBox.setHgrow(respiro, Priority.ALWAYS);
        Button ajudaComandos = botao("Comandos /", "command-help", Feather.COMMAND); ajudaComandos.setOnAction(e -> comando("/help"));
        HBox legenda = new HBox(10, ajuda, respiro, ajudaComandos, contador); legenda.setAlignment(Pos.CENTER_LEFT);
        VBox rodape = new VBox(10, aviso, composicao, legenda); rodape.getStyleClass().add("composer");
        principal.getChildren().addAll(header, ferramentas, conversa, rodape); raiz.setCenter(principal);
        trocar(raiz); janela.setTitle("Conexo · " + salaAtualNome + " · " + apelido + " · " + endereco); Platform.runLater(texto::requestFocus);
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
                    Label icone = label("", "empty-icon"); icone.setGraphic(Icones.criar(Feather.MESSAGE_CIRCLE, 36));
                    Label chamada = label("A conversa começa com você", "empty-title");
                    Label detalhe = label("Diga olá ou convide alguém para a sala " + salaAtualNome + ".", "muted"); detalhe.setWrapText(true); detalhe.setAlignment(Pos.CENTER);
                    VBox convite = new VBox(10, icone, chamada, detalhe); convite.setAlignment(Pos.CENTER); convite.getStyleClass().add("empty-state");
                    mensagens.getChildren().add(convite); vazio = convite;
                }
                aviso.setText("Histórico carregado: " + evento.campo(0) + " mensagem(ns) na sala " + salaAtualNome + ".");
                animacoes.aparecer(mensagens, 0, 6, 0); rolar();
            }
            case HISTORICO_LIMPO -> {
                if (!salaAtualId.equals(evento.campo(0))) break;
                mensagens.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("mensagem-publica")));
                ids.clear();
                if (vazio != null) { mensagens.getChildren().remove(vazio); vazio = null; }
                String retorno = evento.campo(2) + " limpou o histórico público da sala " + evento.campo(1)
                        + ": " + evento.campo(3) + " mensagem(ns) apagada(s).";
                subtitulo.setText("Histórico público limpo"); aviso.getStyleClass().removeIf("error"::equals); aviso.setText(retorno);
                sistema(retorno); rolar();
            }
            case MENSAGEM -> {
                if (!ids.add(evento.campo(0))) break;
                if (ids.size() > 1000) ids.remove(ids.iterator().next());
                mensagem(evento.campo(1), evento.campo(2), evento.campo(3), null);
                ocultarDigitacao(evento.campo(1));
            }
            case PRIVADA -> mensagem(evento.campo(0), evento.campo(2), evento.campo(3), evento.campo(1));
            case SISTEMA -> {
                if (evento.campos().size() >= 2) {
                    respostaComando(evento.campo(1), evento.campo(0), false);
                    aviso.setText("Resposta do comando exibida na conversa.");
                } else sistema(evento.campo(0));
            }
            case USUARIOS -> {
                for (String usuario : new ArrayList<>(digitacoes.keySet()))
                    if (evento.campos().stream().noneMatch(u -> u.equalsIgnoreCase(usuario))) ocultarDigitacao(usuario);
                participantes.getChildren().clear(); online.setText("PARTICIPANTES  ·  " + evento.campos().size());
                totalOnline.setText(evento.campos().size() == 1 ? "1 pessoa online" : evento.campos().size() + " pessoas online");
                atualizandoParticipantes = true;
                String selecionado = destino.getValue(); destino.getItems().setAll("Todos");
                for (String usuario : evento.campos()) {
                    boolean souEu = usuario.equalsIgnoreCase(apelido);
                    Button pessoa = botao("", "person"); pessoa.setMaxWidth(Double.MAX_VALUE); pessoa.setUserData(usuario); pessoa.setAccessibleText(usuario + (souEu ? ", você" : ", enviar mensagem privada"));
                    Label avatar = avatar(usuario); avatar.getStyleClass().add("person-avatar");
                    Label nomePessoa = label(usuario, "person-name"); nomePessoa.setMaxWidth(158); nomePessoa.setTextOverrun(OverrunStyle.ELLIPSIS);
                    VBox detalhes = new VBox(3, nomePessoa, label(souEu ? "Você" : "Disponível", "person-status"));
                    HBox pessoaVisual = new HBox(10, avatar, detalhes); pessoaVisual.setAlignment(Pos.CENTER_LEFT); pessoa.setGraphic(pessoaVisual);
                    pessoa.setOnAction(e -> destino.setValue(souEu ? "Todos" : usuario)); participantes.getChildren().add(pessoa);
                    if (!souEu) destino.getItems().add(usuario);
                }
                // Reassocia o item à lista nova para não deixar o seletor visualmente vazio.
                destino.setValue(null); destino.setValue(selecionado == null ? "Todos" : selecionado);
                if (selecionado != null && !destino.getItems().contains(selecionado)) {
                    aviso.setText(selecionado + " está offline. Escolha outra pessoa ou selecione Todos para conversar em público.");
                }
                atualizandoParticipantes = false; destacarDestino();
            }
            case DIGITANDO -> {
                if (evento.campos().size() > 1 && "stop".equals(evento.campo(1))) ocultarDigitacao(evento.campo(0));
                else mostrarDigitacao(evento.campo(0));
            }
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
        Label corpo = label(conteudo, "message-body"); corpo.setWrapText(true); corpo.setMinWidth(0); corpo.setMaxWidth(580);
        VBox bolha = new VBox(8, cabecalho, corpo); bolha.getStyleClass().add(minha ? "bubble-own" : "bubble"); bolha.setMaxWidth(620);
        bolha.maxWidthProperty().bind(rolagem.widthProperty().subtract(minha ? 66 : 122).map(w -> Math.max(180, Math.min(620, w.doubleValue()))));
        if (privadoPara != null) {
            bolha.getStyleClass().add("bubble-private");
            Label privada = label(minha ? "Privada para " + privadoPara : "Conversa privada · só para você", "private-label");
            privada.setGraphic(Icones.criar(Feather.LOCK, 12));
            bolha.getChildren().add(0, privada);
        } else if (minha) {
            // O servidor só transmite a mensagem pública depois de persistir no JDBC.
            Label confirmacao = label("Salva no servidor", "message-receipt"); confirmacao.setGraphic(Icones.criar(Feather.CHECK, 12));
            confirmacao.setTooltip(new Tooltip("Mensagem pública registrada no histórico. Não indica leitura pelos participantes."));
            bolha.getChildren().add(confirmacao);
            if (!carregandoHistorico) animacoes.aparecer(confirmacao, 0, 3, 160);
        }
        ContextMenu menu = new ContextMenu(); MenuItem copiar = new MenuItem("Copiar mensagem", Icones.criar(Feather.COPY, 14));
        copiar.setOnAction(e -> { ClipboardContent c = new ClipboardContent(); c.putString(conteudo); Clipboard.getSystemClipboard().setContent(c); }); menu.getItems().add(copiar);
        bolha.setOnContextMenuRequested(e -> menu.show(bolha, e.getScreenX(), e.getScreenY()));
        HBox linha = new HBox(10); linha.setAlignment(minha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if (!minha) linha.getChildren().add(avatar(remetente));
        linha.getChildren().add(bolha); linha.setUserData(minha ? 6.0 : -6.0);
        linha.getProperties().put("mensagem-publica", privadoPara == null); adicionar(linha);
    }
    private void sistema(String texto) { Label l = label(texto, "system-message"); l.setWrapText(true); l.setMaxWidth(Double.MAX_VALUE); l.setAlignment(Pos.CENTER); adicionar(l); }
    private void respostaComando(String titulo, String conteudo, boolean erro) {
        if (vazio != null) { mensagens.getChildren().remove(vazio); vazio = null; }
        Label cabecalho = label(titulo, "command-title"); Label corpo = label(conteudo, "command-body");
        cabecalho.setGraphic(Icones.criar(erro ? Feather.ALERT_CIRCLE : Feather.TERMINAL, 17));
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
            animacoes.aparecer(item, item.getUserData() instanceof Double x ? x : 0, 9, 0);
            if (noFim) rolar();
            else if (!novasMensagens.isVisible()) { novasMensagens.setVisible(true); animacoes.aparecer(novasMensagens, 0, 8, 0); }
        }
    }
    private void rolar() {
        if (rolagemAgendada) return;
        rolagemAgendada = true;
        VBox conversaAtual = mensagens;
        Platform.runLater(() -> {
            if (encerrando || !conectado || conversaAtual != mensagens) return;
            rolagemAgendada = false;
            if (rolagemSuave != null) rolagemSuave.stop();
            fluxoConversa.applyCss(); fluxoConversa.layout(); rolagem.layout();
            novasMensagens.setVisible(false);
            rolagemSuave = new Timeline(new KeyFrame(Duration.millis(160), new KeyValue(rolagem.vvalueProperty(), 1, Animacoes.SUAVE)));
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
        pararDigitacaoLocal(); previa.limpar();
        trocandoSala = true; escolherSala.setDisable(true); texto.setDisable(true); destino.setDisable(true); enviarMensagem.setDisable(true);
        aviso.getStyleClass().removeIf("error"::equals); aviso.setText("Entrando na sala " + proxima.nome() + "…");
        conexao.enviar("/join " + proxima.id());
    }
    private void atualizarDestino() {
        destacarDestino();
        atualizarRascunho(); sinalizarDigitacao();
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
    private void mostrarDigitacao(String usuario) {
        if (!conectado || usuario.equalsIgnoreCase(apelido)) return;
        String chave = usuario.toLowerCase(Locale.ROOT);
        DigitacaoAtiva atual = digitacoes.get(chave);
        if (atual == null) {
            boolean acompanhar = estaNoFim();
            BalaoDigitacao balao = new BalaoDigitacao(usuario, avatar(usuario));
            PauseTransition expirar = new PauseTransition(Duration.seconds(4));
            expirar.setOnFinished(e -> ocultarDigitacao(usuario));
            atual = new DigitacaoAtiva(balao, expirar); digitacoes.put(chave, atual);
            digitacoesRemotas.setVisible(true); digitacoesRemotas.setManaged(true); digitacoesRemotas.getChildren().add(balao);
            balao.iniciar(); animacoes.aparecer(balao, -5, 5, 0);
            if (acompanhar) rolar();
        }
        atual.expirar().playFromStart();
    }
    private void ocultarDigitacao(String usuario) {
        DigitacaoAtiva atual = digitacoes.remove(usuario.toLowerCase(Locale.ROOT));
        if (atual == null) return;
        atual.expirar().stop(); atual.balao().parar();
        VBox grupo = digitacoesRemotas;
        animacoes.sumir(atual.balao(), () -> {
            grupo.getChildren().remove(atual.balao());
            grupo.setManaged(!grupo.getChildren().isEmpty()); grupo.setVisible(grupo.isManaged());
        });
    }
    private void limparDigitacoes() {
        digitacoes.values().forEach(d -> { d.expirar().stop(); d.balao().parar(); }); digitacoes.clear();
        if (digitacoesRemotas != null) { digitacoesRemotas.getChildren().clear(); digitacoesRemotas.setManaged(false); digitacoesRemotas.setVisible(false); }
    }
    private boolean estaNoFim() {
        return rolagem.getVvalue() > .93 || rolagem.getVmax() == rolagem.getVmin()
                || rolagemSuave != null && rolagemSuave.getStatus() == Animation.Status.RUNNING;
    }
    private void atualizarRascunho() {
        boolean acompanhar = estaNoFim();
        previa.atualizar(texto.getText(), destino.getValue(), conectado && !trocandoSala);
        if (acompanhar) rolar();
    }
    private void sinalizarDigitacao() {
        String rascunho = texto.getText();
        boolean publica = conectado && !trocandoSala && "Todos".equals(destino.getValue()) && !rascunho.isBlank()
                && rascunho.length() <= 500 && !rascunho.stripLeading().startsWith("/");
        if (!publica || conexao == null) { pararDigitacaoLocal(); return; }
        long agora = System.nanoTime();
        if (!digitacaoEnviada || agora-ultimaDigitacao > 1_200_000_000L) {
            conexao.enviar("/typing"); digitacaoEnviada = true; ultimaDigitacao = agora;
        }
        pausaDigitacaoLocal.playFromStart();
    }
    private void pararDigitacaoLocal() {
        pausaDigitacaoLocal.stop();
        if (digitacaoEnviada && conexao != null) conexao.enviar("/typing stop");
        digitacaoEnviada = false;
    }
    private void restaurarBotaoConectar() { conectar.setDisable(false); conectar.setGraphic(Icones.criar(Feather.ARROW_RIGHT, 16)); conectar.setText("Conectar ao chat"); }
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
        pararDigitacaoLocal();
        if (pedido.isPresent()) conexao.enviar(pedido.get().paraLinha());
        else conexao.enviar("Todos".equals(para) ? mensagem : "/msg \"" + para + "\" " + mensagem);
        animacoes.envio(enviarMensagem.getGraphic()); texto.clear(); aviso.getStyleClass().removeIf("error"::equals);
        aviso.setText(pedido.isPresent() ? "Comando enviado: " + pedido.get().nome() : "Todos".equals(para) ? "Mensagens públicas ficam salvas no servidor." : "Mensagem privada · disponível apenas nesta sessão.");
    }
    private void desconectado(String motivo) {
        conexao = null;
        if (conectado) {
            conectado = false; texto.setDisable(true); destino.setDisable(true); busca.setDisable(true);
            enviarMensagem.setDisable(true); escolherSala.setDisable(true); trocandoSala = false; pararDigitacaoLocal(); limparDigitacoes(); previa.limpar(); pulsoConexao.stop(); pontoConexao.setFill(Color.web("#b97964")); totalOnline.setText("Offline");
            estadoConexao.setText("Desconectado\n" + endereco); estadoConexao.setGraphic(Icones.criar(Feather.WIFI_OFF, 15)); participantes.getChildren().clear(); online.setText("VOCÊ ESTÁ OFFLINE");
            aviso.setText(motivo); aviso.getStyleClass().add("error"); sistema("Você está offline. Use “Sair do chat” para conectar novamente.");
        } else { erroLogin.setText(motivo); animacoes.aparecer(erroLogin, 0, 4, 0); restaurarBotaoConectar(); }
    }
    private void comando(String texto) { if (conexao != null) conexao.enviar(texto); }
    private void sair() { ConexaoChat anterior = conexao; conexao = null; if (anterior != null) anterior.close(); mostrarLogin(""); janela.setTitle("Conexo · Chat multiusuário"); }
    private void fecharJanela() {
        if (encerrando) return;
        encerrando = true; pararEfeitos(); palco.setMouseTransparent(true);
        ConexaoChat anterior = conexao; conexao = null;
        if (anterior != null) anterior.close();
        animacaoJanela.fechar(Platform::exit);
    }
    @Override public void stop() { animacaoJanela.parar(); prepararTela(); if (conexao != null) conexao.close(); }
    public static void main(String[] args) { launch(args); }
}
