package client;

import static client.Componentes.*;

import atlantafx.base.theme.PrimerLight;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import model.Citacao;
import model.EmojiIdentificacao;
import model.Mencoes;
import model.Usuario;

import org.kordamp.ikonli.feather.Feather;

import protocol.ComandoChat;
import protocol.Evento;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ClienteChat extends Application {
    private final Temas temas = new Temas();
    private final Map<String, EmojiIdentificacao> perfis = new LinkedHashMap<>();
    private EmojiIdentificacao meuEmoji = EmojiIdentificacao.SMILE;
    private PopupIA popupIA;
    private RecursosConversa recursos;
    private RenderizadorMensagens renderizador;
    private AjudaComandos ajudaDigitacao;
    private MenuButton mencionar;
    private Label meuPerfil;
    private Button notificacao;
    private boolean retomando, sessaoIniciada;
    private String rascunhoRetomar = "", destinoRetomar;
    private Citacao citacaoRetomar;
    private int mencoesNaoLidas;
    private boolean iaDisponivel;
    private Stage janela;
    private final FilaInterface filaInterface = new FilaInterface(Platform::runLater);
    private ConexaoChat conexao;
    private String apelido, endereco;
    private String salaAtualId = "geral", salaAtualNome = "Geral";

    private record ResumoSala(String id, String nome, int usuarios) {
        @Override
        public String toString() {
            return nome + " · " + usuarios + (usuarios == 1 ? " pessoa" : " pessoas");
        }
    }

    private final List<ResumoSala> salasDisponiveis = new ArrayList<>();
    private ComboBox<ResumoSala> escolherSala;
    private boolean atualizandoSalas, trocandoSala;
    private boolean conectado, carregandoHistorico, atualizandoParticipantes, encerrando;
    private final Animacoes animacoes = new Animacoes();
    private final AnimacaoJanela animacaoJanela = new AnimacaoJanela();
    private StackPane palco;
    private MolduraJanela moldura;
    private final Set<String> ids = new LinkedHashSet<>();
    private TelaLogin login;
    private TextField busca, texto;
    private Label aviso, titulo, subtitulo, contador, online, estadoConexao, totalOnline;
    private Node vazio;
    private Button enviarMensagem, novasMensagens;
    private VBox mensagens, participantes, fluxoConversa, digitacoesRemotas;
    private BalaoRascunho previa;

    private record DigitacaoAtiva(BalaoDigitacao balao, PauseTransition expirar) {}

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

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        temas.selecionar(temas.atual());
        janela = stage;
        janela.setTitle("Conexo · Chat multiusuário");
        var area = Screen.getPrimary().getVisualBounds();
        palco = new StackPane();
        moldura = new MolduraJanela(janela, palco, this::fecharJanela, animacaoJanela::parar);
        temas.registrar(moldura);
        Scene cena =
                new Scene(
                        moldura,
                        Math.min(1280, area.getWidth() - 48),
                        Math.min(850, area.getHeight() - 70));
        cena.getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm());
        janela.setScene(cena);
        janela.setMinWidth(0);
        janela.setMinHeight(0);
        pausaDigitacaoLocal.setOnFinished(e -> pararDigitacaoLocal());
        cena.addEventFilter(
                InputEvent.ANY,
                e -> {
                    if (encerrando) e.consume();
                });
        janela.focusedProperty()
                .addListener((o, a, b) -> {
                    if (b && sessaoIniciada) {
                        mencoesNaoLidas = 0;
                        atualizarTitulo();
                    }
                });
        janela.setOnCloseRequest(e -> {
            e.consume();
            fecharJanela();
        });
        mostrarLogin("");
        palco.applyCss();
        palco.layout();
        animacaoJanela.preparar(janela, palco);
        janela.show();
        animacaoJanela.abrir();
    }

    private Button botao(String texto, String estilo) {
        return Componentes.botao(animacoes, texto, estilo);
    }

    private Button botao(String texto, String estilo, Feather icone) {
        return Componentes.botao(animacoes, texto, estilo, icone);
    }

    private void trocar(Parent raiz) {
        if (janela.isShowing()) animacoes.trocar(palco, raiz);
        else palco.getChildren().setAll(raiz);
    }

    private void prepararTela() {
        if (recursos != null) recursos.fechar();
        pararEfeitos();
        limparDigitacoes();
        if (previa != null) previa.limpar();
    }

    private void pararEfeitos() {
        animacoes.prepararTela();
        pausaDigitacaoLocal.stop();
        digitacaoEnviada = false;
        digitacoes
                .values()
                .forEach(d -> {
                    d.expirar().stop();
                    d.balao().parar();
                });
        if (previa != null) previa.parar();
        rolagemAgendada = false;
        if (rolagemSuave != null) rolagemSuave.stop();
    }

    private void mostrarLogin(String mensagem) {
        prepararTela();
        conectado = false;
        retomando = false;
        sessaoIniciada = false;
        mencoesNaoLidas = 0;
        destinoRetomar = null;
        citacaoRetomar = null;
        rascunhoRetomar = "";
        salasDisponiveis.clear();
        trocandoSala = false;
        login =
                new TelaLogin(
                        apelido,
                        mensagem,
                        temas,
                        animacoes,
                        this::conectar,
                        largura -> {
                            if (moldura != null) moldura.painelDireito(largura);
                        },
                        janela.isShowing());
        trocar(login.vista);
    }

    private void conectar() {
        if (conexao != null || encerrando) return;
        Usuario usuario;
        String ip = login.host.getText().strip();
        int numero;
        try {
            usuario = new Usuario(login.nome.getText());
        } catch (IllegalArgumentException e) {
            login.erro.setText(e.getMessage());
            return;
        }
        try {
            numero = Integer.parseInt(login.porta.getText().strip());
            if (numero < 1 || numero > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            login.erro.setText("A porta deve ser um número entre 1 e 65535.");
            return;
        }
        if (ip.isBlank()) {
            login.erro.setText("Informe o IP do servidor.");
            return;
        }
        var identidade = new PopupIdentidade(janela, temas, usuario.nome()).showAndWait();
        if (identidade.isEmpty()) return;
        usuario = identidade.get();
        meuEmoji = usuario.emoji();
        perfis.clear();
        perfis.put(usuario.nome().toLowerCase(Locale.ROOT), meuEmoji);
        apelido = usuario.nome();
        endereco = ip + ":" + numero;
        login.conectar.setDisable(true);
        login.conectar.setText("Conectando…");
        login.erro.setText("");
        ProgressIndicator progresso = new ProgressIndicator();
        progresso.setMaxSize(17, 17);
        progresso.setPrefSize(17, 17);
        login.conectar.setGraphic(progresso);
        ConexaoChat[] sessao = new ConexaoChat[1];
        sessao[0] =
                new ConexaoChat(
                        evento ->
                                filaInterface.enfileirar(() -> {
                                    if (!encerrando && conexao == sessao[0]) receber(evento);
                                }),
                        motivo ->
                                filaInterface.enfileirar(() -> {
                                    if (!encerrando && conexao == sessao[0]) desconectado(motivo);
                                }),
                        estado ->
                                filaInterface.enfileirar(() -> {
                                    if (!encerrando && conexao == sessao[0]) reconectando(estado);
                                }));
        conexao = sessao[0];
        conexao.conectar(ip, numero, usuario);
    }

    private void mostrarChat() {
        prepararTela();
        ids.clear();
        sessaoIniciada = true;
        login = null;
        recursos =
                new RecursosConversa(
                        janela,
                        temas,
                        this::comando,
                        () -> {
                            destino.setValue("Todos");
                            texto.requestFocus();
                        },
                        this::localizarMensagem);
        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("chat-root");
        VBox lateral = new VBox(10);
        lateral.getStyleClass().add("sidebar");
        lateral.setPrefWidth(278);
        lateral.setMinWidth(250);
        Label marca = marca();
        marca.setPadding(new Insets(0, 0, 16, 0));
        escolherSala = new ComboBox<>();
        escolherSala.setMaxWidth(Double.MAX_VALUE);
        escolherSala.setPromptText("Carregando salas…");
        escolherSala.setId("salas");
        escolherSala.setAccessibleText("Escolher sala do servidor");
        escolherSala.getStyleClass().add("room-picker");
        escolherSala.setOnAction(e -> {
            if (!atualizandoSalas) trocarSala();
        });
        preencherSalas();
        Button geral = botao(salaAtualNome, "channel", Feather.HASH);
        geral.setMaxWidth(Double.MAX_VALUE);
        geral.setOnAction(e -> {
            destino.setValue("Todos");
            comando("/history 50");
        });
        geral.setTooltip(new Tooltip("Mostrar as últimas mensagens da sala"));
        online = label("PARTICIPANTES", "sidebar-label");
        participantes = new VBox(6);
        ScrollPane lista = new ScrollPane(participantes);
        lista.getStyleClass().add("people-scroll");
        lista.setFitToWidth(true);
        lista.setMinHeight(80);
        lista.setPrefHeight(160);
        VBox.setVgrow(lista, Priority.ALWAYS);
        meuPerfil = label(apelido + "  ·  você", "sidebar-name");
        meuPerfil.setWrapText(true);
        meuPerfil.setGraphic(new EmojiAvatar(meuEmoji, 42));
        Button editarPerfil = botao("Editar perfil", "sidebar-button", Feather.EDIT_2);
        editarPerfil.setId("editar-perfil");
        editarPerfil.setOnAction(e -> editarPerfil());
        estadoConexao = label("Conectado\n" + endereco + "\nSala: " + salaAtualNome, "connection");
        estadoConexao.setGraphic(Icones.criar(Feather.WIFI, 15));
        estadoConexao.setWrapText(true);
        Button copiar = botao("Copiar endereço", "sidebar-button", Feather.COPY);
        copiar.setOnAction(e -> {
            copiarTexto(endereco);
            aviso.setText(
                    "Endereço copiado. Entre nesse servidor e escolha a sala "
                            + salaAtualNome
                            + ".");
            animacoes.aparecer(aviso, 0, 4, 0);
        });
        Button sair = botao("Sair do chat", "sidebar-button", Feather.LOG_OUT);
        sair.setOnAction(e -> sair());
        lateral.getChildren()
                .addAll(
                        marca,
                        temas.seletor(true),
                        label("SALAS DO SERVIDOR", "sidebar-label"),
                        escolherSala,
                        geral,
                        espaco(6),
                        online,
                        lista,
                        meuPerfil,
                        editarPerfil,
                        estadoConexao,
                        copiar,
                        sair);
        raiz.setRight(lateral);

        VBox principal = new VBox();
        principal.setMinSize(0, 0);
        titulo = label("Sala " + salaAtualNome, "chat-title");
        subtitulo = label("Um lugar para compartilhar ideias e se encontrar.", "muted");
        subtitulo.setWrapText(true);
        VBox titulos = new VBox(5, titulo, subtitulo);
        titulos.setMinWidth(0);
        titulo.setMinWidth(0);
        titulo.setWrapText(true);
        HBox.setHgrow(titulos, Priority.ALWAYS);
        totalOnline = label("Conectado", "status-text");
        pontoConexao = new Circle(4, Color.web("#397c54"));
        HBox status = new HBox(7, pontoConexao, totalOnline);
        status.setAlignment(Pos.CENTER);
        status.getStyleClass().add("status-pill");
        pulsoConexao = animacoes.pulsar(pontoConexao);
        Button abrirPainel = botao("Salas", "secondary", Feather.USERS);
        abrirPainel.setId("abrir-painel");
        HBox header = new HBox(12, titulos, status, abrirPainel);
        header.getStyleClass().add("chat-header");
        header.setAlignment(Pos.CENTER_LEFT);
        busca = new TextField();
        busca.setPromptText("Buscar no histórico público…");
        busca.setId("busca");
        busca.setMinWidth(0);
        HBox.setHgrow(busca, Priority.ALWAYS);
        Button pesquisar = botao("Buscar", "secondary", Feather.SEARCH);
        pesquisar.setAccessibleText("Buscar no histórico");
        pesquisar.setTooltip(new Tooltip("Buscar no histórico"));
        pesquisar.setOnAction(e -> buscar());
        busca.setOnAction(e -> buscar());
        Button voltar = botao("Histórico", "secondary", Feather.CLOCK);
        voltar.setTooltip(new Tooltip("Mostrar as últimas mensagens da sala"));
        voltar.setOnAction(e -> {
            busca.clear();
            comando("/history 50");
        });
        Button ia = botao("IA", "secondary", Feather.ZAP);
        ia.setTooltip(new Tooltip("Perguntar à Conexo IA em privado ou na sala"));
        voltar.setAccessibleText("Mostrar histórico");
        ia.setAccessibleText("Perguntar à IA");
        ia.setOnAction(e -> abrirIA());
        HBox ferramentasLargas = new HBox(9, busca, pesquisar, voltar, ia, recursos.avisos);
        ferramentasLargas.setMinWidth(0);
        FlowPane acoesBusca = new FlowPane(8, 8);
        VBox ferramentas = new VBox(8, ferramentasLargas);
        ferramentas.getStyleClass().add("toolbar");
        ferramentas.setMinWidth(0);
        notificacao = new Button();
        notificacao.setWrapText(true);
        notificacao.setMaxWidth(Double.MAX_VALUE);
        notificacao.getStyleClass().add("mention-notice");
        notificacao.setVisible(false);
        notificacao.setManaged(false);
        mensagens = new VBox(14);
        mensagens.getStyleClass().add("messages");
        digitacoesRemotas = new VBox(10);
        digitacoesRemotas.setManaged(false);
        digitacoesRemotas.setVisible(false);
        previa = new BalaoRascunho();
        fluxoConversa = new VBox(14, mensagens, digitacoesRemotas, previa);
        fluxoConversa.getStyleClass().add("conversation-stream");
        rolagem = new ScrollPane(fluxoConversa);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagem.getStyleClass().add("message-scroll");
        VBox.setVgrow(rolagem, Priority.ALWAYS);
        rolagem.setOnScroll(e -> {
            if (e.getDeltaY() > 0 && rolagemSuave != null) rolagemSuave.stop();
        });
        rolagem.setMinSize(0, 0);
        fluxoConversa.setMinWidth(0);
        renderizador =
                new RenderizadorMensagens(
                        rolagem.widthProperty(), () -> apelido, recursos, animacoes);
        novasMensagens = botao("Novas mensagens", "new-messages", Feather.ARROW_DOWN);
        novasMensagens.setVisible(false);
        novasMensagens.setOnAction(e -> rolar());
        StackPane conversa = new StackPane(rolagem, novasMensagens);
        conversa.setMinSize(0, 0);
        VBox.setVgrow(conversa, Priority.ALWAYS);
        StackPane.setAlignment(novasMensagens, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(novasMensagens, new Insets(0, 25, 18, 0));
        rolagem.vvalueProperty()
                .addListener((obs, antes, depois) -> {
                    if (depois.doubleValue() > .98) novasMensagens.setVisible(false);
                });
        aviso =
                label(
                        "Públicas ficam no histórico. Privadas duram apenas esta sessão.",
                        "small-muted");
        aviso.setWrapText(true);
        destino = new ComboBox<>();
        destino.getItems().add("Todos");
        destino.setValue("Todos");
        destino.setPrefWidth(165);
        destino.setMinWidth(130);
        destino.setAccessibleText("Destinatário da mensagem");
        texto = new TextField();
        texto.setPromptText("Escreva uma mensagem…");
        texto.setId("mensagem");
        texto.setMinWidth(0);
        HBox.setHgrow(texto, Priority.ALWAYS);
        texto.setOnAction(e -> enviar());
        texto.setTooltip(
                new Tooltip("Até 500 caracteres · Enter para enviar · /help para ver os comandos"));
        ajudaDigitacao = new AjudaComandos(texto);
        mencionar = new MenuButton("@");
        mencionar.getStyleClass().add("secondary");
        mencionar.setTooltip(new Tooltip("Mencionar uma pessoa da sala"));
        mencionar.setAccessibleText("Inserir menção");
        mencionar.setId("mencionar");
        contador = label("0 / 500", "counter");
        enviarMensagem = botao("Enviar", "primary", Feather.SEND);
        enviarMensagem.setContentDisplay(ContentDisplay.RIGHT);
        enviarMensagem.setOnAction(e -> enviar());
        enviarMensagem.setId("enviar");
        enviarMensagem.setDisable(true);
        enviarMensagem.setAccessibleText("Enviar mensagem");
        texto.textProperty()
                .addListener((obs, antes, depois) -> {
                    contador.setText(depois.length() + " / 500");
                    contador.getStyleClass().remove("over-limit");
                    if (depois.length() > 500) contador.getStyleClass().add("over-limit");
                    enviarMensagem.setDisable(
                            depois.isBlank() || depois.length() > 500 || !conectado);
                    atualizarRascunho();
                    sinalizarDigitacao();
                });
        HBox linhaEnvio = new HBox(8, mencionar, texto, enviarMensagem);
        linhaEnvio.setMinWidth(0);
        HBox.setHgrow(linhaEnvio, Priority.ALWAYS);
        composicao = new HBox(10, destino, linhaEnvio);
        composicao.setMinWidth(0);
        composicao.setAlignment(Pos.CENTER_LEFT);
        composicao.getStyleClass().add("compose-box");
        destino.setOnAction(e -> {
            if (!atualizandoParticipantes) atualizarDestino();
        });
        Label ajuda = label("Enter para enviar · / para ajuda", "small-muted");
        Region respiro = new Region();
        HBox.setHgrow(respiro, Priority.ALWAYS);
        Button ajudaComandos = botao("Comandos /", "command-help", Feather.COMMAND);
        ajudaComandos.setOnAction(e -> ajudaDigitacao.abrir());
        HBox legenda = new HBox(10, ajuda, respiro, ajudaComandos, contador);
        legenda.setAlignment(Pos.CENTER_LEFT);
        VBox caixaEnvio = new VBox(8, composicao);
        caixaEnvio.setMinWidth(0);
        VBox rodape =
                new VBox(8, aviso, ajudaDigitacao, recursos.citacaoEnvio, caixaEnvio, legenda);
        rodape.setMinWidth(0);
        rodape.getStyleClass().add("composer");
        principal.getChildren().addAll(header, ferramentas, notificacao, conversa, rodape);
        raiz.setCenter(principal);
        TelaChatAdaptavel adaptavel =
                new TelaChatAdaptavel(
                        raiz,
                        lateral,
                        abrirPainel,
                        largura -> {
                            if (moldura != null) moldura.painelDireito(largura);
                        });
        boolean[] estreito = {false};
        Runnable adaptarChat =
                () -> {
                    boolean menor = adaptavel.getWidth() < 620;
                    if (menor != estreito[0]) {
                        boolean escrevendo = texto.isFocused();
                        estreito[0] = menor;
                        // Desanexa antes de reorganizar: não recria campos, rascunhos ou a conexão.
                        ferramentasLargas.getChildren().clear();
                        acoesBusca.getChildren().clear();
                        ferramentas.getChildren().clear();
                        composicao.getChildren().clear();
                        caixaEnvio.getChildren().clear();
                        if (menor) {
                            acoesBusca.getChildren().setAll(pesquisar, voltar, ia, recursos.avisos);
                            ferramentas.getChildren().setAll(busca, acoesBusca);
                            composicao.getChildren().setAll(linhaEnvio);
                            caixaEnvio.getChildren().setAll(destino, composicao);
                            destino.setMaxWidth(Double.MAX_VALUE);
                        } else {
                            ferramentasLargas
                                    .getChildren()
                                    .setAll(busca, pesquisar, voltar, ia, recursos.avisos);
                            ferramentas.getChildren().setAll(ferramentasLargas);
                            composicao.getChildren().setAll(destino, linhaEnvio);
                            caixaEnvio.getChildren().setAll(composicao);
                            destino.setMaxWidth(165);
                        }
                        if (escrevendo) Platform.runLater(texto::requestFocus);
                    }
                    boolean detalhes = adaptavel.getWidth() >= 680 && adaptavel.getHeight() >= 560;
                    subtitulo.setVisible(detalhes);
                    subtitulo.setManaged(detalhes);
                    status.setVisible(detalhes);
                    status.setManaged(detalhes);
                    boolean dicas = adaptavel.getHeight() >= 500;
                    aviso.setVisible(dicas);
                    aviso.setManaged(dicas);
                    ajuda.setVisible(!menor);
                    ajuda.setManaged(!menor);
                    boolean legendaVisivel = adaptavel.getHeight() >= 460;
                    legenda.setVisible(legendaVisivel);
                    legenda.setManaged(legendaVisivel);
                    abrirPainel.setText(adaptavel.getWidth() < 420 ? "" : "Salas");
                    enviarMensagem.setText(adaptavel.getWidth() < 420 ? "" : "Enviar");
                    pesquisar.setText(adaptavel.getWidth() < 420 ? "" : "Buscar");
                    voltar.setText(adaptavel.getWidth() < 420 ? "" : "Histórico");
                    ia.setText(adaptavel.getWidth() < 420 ? "" : "IA");
                };
        adaptavel.widthProperty().addListener((o, a, b) -> adaptarChat.run());
        adaptavel.heightProperty().addListener((o, a, b) -> adaptarChat.run());
        trocar(new ViewportAdaptavel(adaptavel, 300, 400));
        atualizarTitulo();
        Platform.runLater(texto::requestFocus);
    }

    private void receber(Evento evento) {
        switch (evento.tipo()) {
            case BEM_VINDO -> {
                String salaAnterior = salaAtualId;
                if (evento.campos().size() >= 3) {
                    salaAtualId = evento.campo(1);
                    salaAtualNome = evento.campo(2);
                } else {
                    salaAtualId = "geral";
                    salaAtualNome = "Geral";
                }
                if (popupIA != null) popupIA.close();
                apelido = evento.campo(0);
                if (evento.campos().size() > 3)
                    meuEmoji = EmojiIdentificacao.porId(evento.campo(3));
                perfis.clear();
                perfis.put(apelido.toLowerCase(Locale.ROOT), meuEmoji);
                boolean restaurar = retomando;
                trocandoSala = false;
                conectado = true;
                retomando = false;
                mostrarChat();
                if (restaurar) {
                    texto.setText(rascunhoRetomar);
                    if (citacaoRetomar != null && salaAnterior.equals(salaAtualId))
                        recursos.responder(citacaoRetomar);
                    aviso.setText(
                            "Conexão restabelecida. Histórico atualizado; mensagens pendentes não foram reenviadas.");
                }
            }
            case ERRO -> {
                if (sessaoIniciada) {
                    aviso.setText(evento.campo(0));
                    aviso.getStyleClass().add("error");
                    respostaComando("Não foi possível executar", evento.campo(0), true);
                    if (trocandoSala) {
                        trocandoSala = false;
                        preencherSalas();
                        texto.setDisable(false);
                        destino.setDisable(false);
                        enviarMensagem.setDisable(
                                texto.getText().isBlank() || texto.getText().length() > 500);
                    }
                } else {
                    login.erro.setText(evento.campo(0));
                    animacoes.aparecer(login.erro, 0, 4, 0);
                    ConexaoChat anterior = conexao;
                    conexao = null;
                    if (anterior != null) anterior.close();
                    restaurarBotaoConectar();
                }
            }
            case HISTORICO_INICIO -> {
                carregandoHistorico = true;
                mensagens.getChildren().clear();
                recursos.limparCartoes();
                ids.clear();
                vazio = null;
                subtitulo.setText(evento.campo(0));
            }
            case HISTORICO_FIM -> {
                carregandoHistorico = false;
                if (mensagens.getChildren().isEmpty()) {
                    Label icone = label("", "empty-icon");
                    icone.setGraphic(Icones.criar(Feather.MESSAGE_CIRCLE, 36));
                    Label chamada = label("A conversa começa com você", "empty-title");
                    Label detalhe =
                            label(
                                    "Diga olá ou convide alguém para a sala " + salaAtualNome + ".",
                                    "muted");
                    detalhe.setWrapText(true);
                    detalhe.setAlignment(Pos.CENTER);
                    VBox convite = new VBox(10, icone, chamada, detalhe);
                    convite.setAlignment(Pos.CENTER);
                    convite.getStyleClass().add("empty-state");
                    mensagens.getChildren().add(convite);
                    vazio = convite;
                }
                aviso.setText(
                        "Histórico carregado: "
                                + evento.campo(0)
                                + " mensagem(ns) na sala "
                                + salaAtualNome
                                + ".");
                animacoes.aparecer(mensagens, 0, 6, 0);
                rolar();
            }
            case HISTORICO_LIMPO -> {
                if (!salaAtualId.equals(evento.campo(0))) break;
                mensagens
                        .getChildren()
                        .removeIf(n ->
                                Boolean.TRUE.equals(n.getProperties().get("mensagem-publica")));
                ids.clear();
                recursos.limparCartoes();
                recursos.cancelarResposta();
                notificacao.setVisible(false);
                notificacao.setManaged(false);
                if (vazio != null) {
                    mensagens.getChildren().remove(vazio);
                    vazio = null;
                }
                String retorno =
                        evento.campo(2)
                                + " limpou o histórico público da sala "
                                + evento.campo(1)
                                + ": "
                                + evento.campo(3)
                                + " mensagem(ns) apagada(s).";
                subtitulo.setText("Histórico público limpo");
                aviso.getStyleClass().removeIf("error"::equals);
                aviso.setText(retorno);
                sistema(retorno);
                rolar();
            }
            case MENSAGEM -> {
                if (!ids.add(evento.campo(0))) break;
                if (ids.size() > 1000) ids.remove(ids.iterator().next());
                mensagem(protocol.MensagensProtocolo.ler(evento), null);
                ocultarDigitacao(evento.campo(1));
            }
            case PRIVADA ->
                    mensagem(
                            new model.Mensagem(
                                    null,
                                    new Usuario(evento.campo(0), emojiDoEvento(evento, 4).id()),
                                    evento.campo(2),
                                    LocalDateTime.parse(evento.campo(3))),
                            evento.campo(1));
            case REACOES -> recursos.reacoes(evento);
            case FIXADAS -> recursos.fixadas(evento);
            case MENCAO -> notificarMencao(evento);
            case PERFIL_ALTERADO -> {
                String anterior = evento.campo(0), novo = evento.campo(1);
                perfis.remove(anterior.toLowerCase(Locale.ROOT));
                perfis.put(
                        novo.toLowerCase(Locale.ROOT), EmojiIdentificacao.porId(evento.campo(2)));
                ocultarDigitacao(anterior);
                if (anterior.equalsIgnoreCase(apelido)) {
                    apelido = novo;
                    meuEmoji = EmojiIdentificacao.porId(evento.campo(2));
                    conexao.atualizarIdentidade(new Usuario(apelido, meuEmoji.id()));
                    meuPerfil.setText(apelido + "  ·  você");
                    meuPerfil.setGraphic(new EmojiAvatar(meuEmoji, 42));
                    atualizarTitulo();
                    aviso.setText(
                            "Seu perfil foi atualizado. As mensagens antigas preservam a identidade usada no envio.");
                } else if (anterior.equals(destino.getValue())) destino.setValue(novo);
            }
            case PERFIS -> {
                perfis.clear();
                for (int i = 0; i + 1 < evento.campos().size(); i += 2)
                    perfis.put(
                            evento.campo(i).toLowerCase(Locale.ROOT),
                            EmojiIdentificacao.porId(evento.campo(i + 1)));
                for (Node pessoa : participantes.getChildren()) {
                    if (pessoa instanceof Button b
                            && b.getGraphic() instanceof HBox h
                            && h.getChildren().get(0) instanceof EmojiAvatar a)
                        a.atualizar(
                                perfis.getOrDefault(
                                        pessoa.getUserData().toString().toLowerCase(Locale.ROOT),
                                        EmojiIdentificacao.SMILE));
                }
            }
            case IA_DISPONIVEL -> iaDisponivel = Boolean.parseBoolean(evento.campo(0));
            case IA_INICIO -> {
                if (popupIA != null) popupIA.iniciou();
            }
            case IA_RESPOSTA -> {
                if (popupIA != null) popupIA.respondeu(evento.campo(3), false);
                if ("privado".equals(evento.campo(0))) respostaIA(evento.campo(2), evento.campo(3));
            }
            case IA_ERRO -> {
                if (popupIA != null) popupIA.respondeu(evento.campo(0), true);
                respostaComando("Conexo IA", evento.campo(0), true);
            }
            case SISTEMA -> {
                if (evento.campos().size() >= 2) {
                    respostaComando(evento.campo(1), evento.campo(0), false);
                    aviso.setText("Resposta do comando exibida na conversa.");
                } else sistema(evento.campo(0));
            }
            case USUARIOS -> {
                for (String usuario : new ArrayList<>(digitacoes.keySet()))
                    if (evento.campos().stream().noneMatch(u -> u.equalsIgnoreCase(usuario)))
                        ocultarDigitacao(usuario);
                participantes.getChildren().clear();
                online.setText("PARTICIPANTES  ·  " + evento.campos().size());
                totalOnline.setText(
                        evento.campos().size() == 1
                                ? "1 pessoa online"
                                : evento.campos().size() + " pessoas online");
                atualizandoParticipantes = true;
                String selecionado = destinoRetomar != null ? destinoRetomar : destino.getValue();
                destinoRetomar = null;
                destino.getItems().setAll("Todos");
                mencionar.getItems().clear();
                for (String usuario : evento.campos()) {
                    boolean souEu = usuario.equalsIgnoreCase(apelido);
                    Button pessoa = botao("", "person");
                    pessoa.setMaxWidth(Double.MAX_VALUE);
                    pessoa.setUserData(usuario);
                    pessoa.setAccessibleText(
                            usuario + (souEu ? ", você" : ", enviar mensagem privada"));
                    EmojiAvatar avatar = avatar(usuario);
                    avatar.getStyleClass().add("person-avatar");
                    Label nomePessoa = label(usuario, "person-name");
                    nomePessoa.setMaxWidth(158);
                    nomePessoa.setTextOverrun(OverrunStyle.ELLIPSIS);
                    VBox detalhes =
                            new VBox(
                                    3,
                                    nomePessoa,
                                    label(souEu ? "Você" : "Disponível", "person-status"));
                    HBox pessoaVisual = new HBox(10, avatar, detalhes);
                    pessoaVisual.setAlignment(Pos.CENTER_LEFT);
                    pessoa.setGraphic(pessoaVisual);
                    pessoa.setOnAction(e -> destino.setValue(souEu ? "Todos" : usuario));
                    participantes.getChildren().add(pessoa);
                    if (!souEu) {
                        destino.getItems().add(usuario);
                        MenuItem item = new MenuItem(usuario);
                        item.setOnAction(e -> {
                            texto.insertText(texto.getCaretPosition(), Mencoes.para(usuario) + " ");
                            texto.requestFocus();
                        });
                        mencionar.getItems().add(item);
                    }
                }
                // Reassocia o item à lista nova para não deixar o seletor visualmente vazio.
                destino.setValue(null);
                destino.setValue(selecionado == null ? "Todos" : selecionado);
                if (selecionado != null && !destino.getItems().contains(selecionado)) {
                    aviso.setText(
                            selecionado
                                    + " está offline. Escolha outra pessoa ou selecione Todos para conversar em público.");
                }
                atualizandoParticipantes = false;
                destacarDestino();
            }
            case DIGITANDO -> {
                if (evento.campos().size() > 1 && "stop".equals(evento.campo(1)))
                    ocultarDigitacao(evento.campo(0));
                else mostrarDigitacao(evento.campo(0));
            }
            case SALAS -> {
                salasDisponiveis.clear();
                for (int i = 1; i + 2 < evento.campos().size(); i += 3) {
                    salasDisponiveis.add(
                            new ResumoSala(
                                    evento.campo(i),
                                    evento.campo(i + 1),
                                    Integer.parseInt(evento.campo(i + 2))));
                }
                preencherSalas();
            }
            case PONG -> {}
        }
    }

    private void mensagem(model.Mensagem mensagem, String privadoPara) {
        removerVazio();
        adicionar(renderizador.mensagem(mensagem, privadoPara, carregandoHistorico));
    }

    private void sistema(String texto) {
        Label l = label(texto, "system-message");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(Pos.CENTER);
        adicionar(l);
    }

    private void respostaIA(String pergunta, String resposta) {
        removerVazio();
        adicionar(renderizador.respostaIA(pergunta, resposta));
        rolar();
    }

    private void respostaComando(String titulo, String conteudo, boolean erro) {
        removerVazio();
        adicionar(renderizador.respostaComando(titulo, conteudo, erro));
        rolar();
    }

    private void removerVazio() {
        if (vazio != null) {
            mensagens.getChildren().remove(vazio);
            vazio = null;
        }
    }

    private void adicionar(Node item) {
        boolean noFim =
                rolagem.getVvalue() > .93
                        || mensagens.getChildren().size() < 5
                        || rolagemSuave != null
                                && rolagemSuave.getStatus() == Animation.Status.RUNNING;
        mensagens.getChildren().add(item);
        if (mensagens.getChildren().size() > 500) {
            Node removido = mensagens.getChildren().remove(0);
            if (removido.getProperties().get("mensagem-id") instanceof Long id)
                recursos.esquecer(id);
        }
        if (!carregandoHistorico) {
            animacoes.aparecer(item, item.getUserData() instanceof Double x ? x : 0, 9, 0);
            if (noFim) rolar();
            else if (!novasMensagens.isVisible()) {
                novasMensagens.setVisible(true);
                animacoes.aparecer(novasMensagens, 0, 8, 0);
            }
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
            fluxoConversa.applyCss();
            fluxoConversa.layout();
            rolagem.layout();
            novasMensagens.setVisible(false);
            rolagemSuave =
                    new Timeline(
                            new KeyFrame(
                                    Duration.millis(160),
                                    new KeyValue(rolagem.vvalueProperty(), 1, Animacoes.SUAVE)));
            rolagemSuave.play();
        });
    }

    private EmojiAvatar avatar(String usuario) {
        return new EmojiAvatar(
                perfis.getOrDefault(usuario.toLowerCase(Locale.ROOT), EmojiIdentificacao.SMILE),
                42);
    }

    private EmojiIdentificacao emojiDoEvento(Evento evento, int indice) {
        return evento.campos().size() > indice
                ? EmojiIdentificacao.porId(evento.campo(indice))
                : EmojiIdentificacao.SMILE;
    }

    private void abrirIA() {
        if (popupIA != null && popupIA.isShowing()) {
            popupIA.getDialogPane().getScene().getWindow().requestFocus();
            return;
        }
        popupIA =
                new PopupIA(
                        janela,
                        temas,
                        iaDisponivel && conectado,
                        salaAtualNome,
                        pedido -> comando(pedido));
        popupIA.show();
    }

    private void preencherSalas() {
        atualizandoSalas = true;
        try {
            escolherSala.getItems().setAll(salasDisponiveis);
            escolherSala
                    .getSelectionModel()
                    .select(
                            salasDisponiveis.stream()
                                    .filter(s -> s.id().equals(salaAtualId))
                                    .findFirst()
                                    .orElse(null));
            escolherSala.setDisable(trocandoSala || salasDisponiveis.size() <= 1);
        } finally {
            atualizandoSalas = false;
        }
    }

    private void trocarSala() {
        ResumoSala proxima = escolherSala.getValue();
        if (conexao == null
                || !conectado
                || trocandoSala
                || proxima == null
                || proxima.id().equals(salaAtualId)) return;
        pararDigitacaoLocal();
        previa.limpar();
        trocandoSala = true;
        escolherSala.setDisable(true);
        texto.setDisable(true);
        destino.setDisable(true);
        enviarMensagem.setDisable(true);
        aviso.getStyleClass().removeIf("error"::equals);
        aviso.setText("Entrando na sala " + proxima.nome() + "…");
        conexao.enviar("/join " + proxima.id());
    }

    private void atualizarDestino() {
        destacarDestino();
        atualizarRascunho();
        sinalizarDigitacao();
        boolean publica = "Todos".equals(destino.getValue());
        if (!publica) recursos.cancelarResposta();
        aviso.setText(
                publica
                        ? "Públicas ficam no histórico. Privadas duram apenas esta sessão."
                        : "Privada para " + destino.getValue() + " · só vocês dois recebem.");
        animacoes.aparecer(aviso, 0, 3, 0);
        texto.requestFocus();
    }

    private void destacarDestino() {
        boolean publica = destino.getValue() == null || "Todos".equals(destino.getValue());
        texto.setPromptText(
                publica
                        ? "Compartilhe uma ideia…"
                        : "Mensagem privada para " + destino.getValue() + "…");
        composicao.getStyleClass().remove("private-mode");
        if (!publica) composicao.getStyleClass().add("private-mode");
        for (Node node : participantes.getChildren()) {
            node.getStyleClass().remove("person-selected");
            if (!publica && destino.getValue().equals(node.getUserData()))
                node.getStyleClass().add("person-selected");
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
            atual = new DigitacaoAtiva(balao, expirar);
            digitacoes.put(chave, atual);
            digitacoesRemotas.setVisible(true);
            digitacoesRemotas.setManaged(true);
            digitacoesRemotas.getChildren().add(balao);
            balao.iniciar();
            animacoes.aparecer(balao, -5, 5, 0);
            if (acompanhar) rolar();
        }
        atual.expirar().playFromStart();
    }

    private void ocultarDigitacao(String usuario) {
        DigitacaoAtiva atual = digitacoes.remove(usuario.toLowerCase(Locale.ROOT));
        if (atual == null) return;
        atual.expirar().stop();
        atual.balao().parar();
        VBox grupo = digitacoesRemotas;
        animacoes.sumir(
                atual.balao(),
                () -> {
                    grupo.getChildren().remove(atual.balao());
                    grupo.setManaged(!grupo.getChildren().isEmpty());
                    grupo.setVisible(grupo.isManaged());
                });
    }

    private void limparDigitacoes() {
        digitacoes
                .values()
                .forEach(d -> {
                    d.expirar().stop();
                    d.balao().parar();
                });
        digitacoes.clear();
        if (digitacoesRemotas != null) {
            digitacoesRemotas.getChildren().clear();
            digitacoesRemotas.setManaged(false);
            digitacoesRemotas.setVisible(false);
        }
    }

    private boolean estaNoFim() {
        return rolagem.getVvalue() > .93
                || rolagem.getVmax() == rolagem.getVmin()
                || rolagemSuave != null && rolagemSuave.getStatus() == Animation.Status.RUNNING;
    }

    private void atualizarRascunho() {
        boolean acompanhar = estaNoFim();
        previa.atualizar(texto.getText(), destino.getValue(), conectado && !trocandoSala);
        if (acompanhar) rolar();
    }

    private void sinalizarDigitacao() {
        String rascunho = texto.getText();
        boolean publica =
                conectado
                        && !trocandoSala
                        && "Todos".equals(destino.getValue())
                        && !rascunho.isBlank()
                        && rascunho.length() <= 500
                        && !rascunho.stripLeading().startsWith("/");
        if (!publica || conexao == null) {
            pararDigitacaoLocal();
            return;
        }
        long agora = System.nanoTime();
        if (!digitacaoEnviada || agora - ultimaDigitacao > 1_200_000_000L) {
            conexao.enviar("/typing");
            digitacaoEnviada = true;
            ultimaDigitacao = agora;
        }
        pausaDigitacaoLocal.playFromStart();
    }

    private void pararDigitacaoLocal() {
        pausaDigitacaoLocal.stop();
        if (digitacaoEnviada && conexao != null) conexao.enviar("/typing stop");
        digitacaoEnviada = false;
    }

    private void restaurarBotaoConectar() {
        login.conectar.setDisable(false);
        login.conectar.setGraphic(Icones.criar(Feather.ARROW_RIGHT, 16));
        login.conectar.setText("Conectar ao chat");
    }

    private void buscar() {
        if (conexao == null) return;
        String termo = busca.getText().strip();
        if (termo.isBlank() || termo.length() > 100) {
            aviso.setText("Digite de 1 a 100 caracteres para buscar no histórico.");
            return;
        }
        conexao.enviar("/buscar " + termo);
    }

    private void enviar() {
        if (conexao == null || !conectado) return;
        String mensagem = ComandoChat.aparar(texto.getText());
        if (mensagem.isBlank()
                || mensagem.length() > 500
                || mensagem.chars().anyMatch(Character::isISOControl)) {
            aviso.setText("Escreva uma mensagem de 1 a 500 caracteres, em uma linha.");
            return;
        }
        String para = destino.getValue();
        var pedido = ComandoChat.ler(mensagem);
        pararDigitacaoLocal();
        if (pedido.isPresent()) conexao.enviar(pedido.get().paraLinha());
        else if ("Todos".equals(para) && recursos.resposta() != null)
            conexao.enviar("/responder " + recursos.resposta().id() + " " + mensagem);
        else conexao.enviar("Todos".equals(para) ? mensagem : "/msg \"" + para + "\" " + mensagem);
        if (pedido.isEmpty()) recursos.cancelarResposta();
        animacoes.envio(enviarMensagem.getGraphic());
        texto.clear();
        aviso.getStyleClass().removeIf("error"::equals);
        aviso.setText(
                pedido.isPresent()
                        ? "Comando enviado: " + pedido.get().nome()
                        : "Todos".equals(para)
                                ? "Mensagens públicas ficam salvas no servidor."
                                : "Mensagem privada · disponível apenas nesta sessão.");
    }

    private void reconectando(String estado) {
        if (!sessaoIniciada) return;
        if (!retomando) {
            rascunhoRetomar = texto.getText();
            destinoRetomar = destino.getValue();
            citacaoRetomar = recursos.resposta();
        }
        retomando = true;
        desativarConversa();
        aviso.setText(estado);
        estadoConexao.setText("Reconectando…\n" + endereco);
        totalOnline.setText("Reconectando…");
    }

    private void desativarConversa() {
        conectado = false;
        trocandoSala = false;
        iaDisponivel = false;
        if (popupIA != null) popupIA.close();
        texto.setDisable(true);
        destino.setDisable(true);
        busca.setDisable(true);
        enviarMensagem.setDisable(true);
        escolherSala.setDisable(true);
        pararDigitacaoLocal();
        limparDigitacoes();
        previa.limpar();
        pulsoConexao.stop();
        pontoConexao.setFill(Color.web("#b97964"));
        estadoConexao.setGraphic(Icones.criar(Feather.WIFI_OFF, 15));
        participantes.getChildren().clear();
        online.setText("VOCÊ ESTÁ OFFLINE");
    }

    private void desconectado(String motivo) {
        conexao = null;
        retomando = false;
        if (sessaoIniciada) {
            desativarConversa();
            totalOnline.setText("Offline");
            estadoConexao.setText("Desconectado\n" + endereco);
            aviso.setText(motivo);
            aviso.getStyleClass().add("error");
            sistema(motivo + " Use Sair do chat para entrar novamente.");
        } else {
            login.erro.setText(motivo);
            animacoes.aparecer(login.erro, 0, 4, 0);
            restaurarBotaoConectar();
        }
    }

    private void comando(String texto) {
        if (conexao != null && conectado) conexao.enviar(texto);
        else if (aviso != null && sessaoIniciada)
            aviso.setText("Aguarde a conexão para executar esta ação.");
    }

    private void editarPerfil() {
        if (!conectado) {
            aviso.setText("Aguarde a conexão para editar seu perfil.");
            return;
        }
        new PopupIdentidade(janela, temas, new Usuario(apelido, meuEmoji.id()))
                .showAndWait()
                .ifPresent(u -> comando("/perfil " + u.emoji().id() + " " + u.nome()));
    }

    private void notificarMencao(Evento e) {
        if (carregandoHistorico) return;
        notificacao.setText(e.campo(1) + " mencionou você · Ver mensagem");
        notificacao.setVisible(true);
        notificacao.setManaged(true);
        notificacao.setOnAction(a -> {
            localizarMensagem(Long.parseLong(e.campo(0)));
            notificacao.setVisible(false);
            notificacao.setManaged(false);
        });
        if (!janela.isFocused() || janela.isIconified()) {
            mencoesNaoLidas++;
            atualizarTitulo();
        }
        animacoes.aparecer(notificacao, 0, 4, 0);
    }

    private void atualizarTitulo() {
        janela.setTitle(
                (mencoesNaoLidas == 0 ? "" : "(" + mencoesNaoLidas + " menções) ")
                        + "Conexo · "
                        + salaAtualNome
                        + " · "
                        + apelido
                        + " · "
                        + endereco);
    }

    private void localizarMensagem(long id) {
        for (Node n : mensagens.getChildren())
            if (Long.valueOf(id).equals(n.getProperties().get("mensagem-id"))) {
                fluxoConversa.applyCss();
                fluxoConversa.layout();
                if (rolagemSuave != null) rolagemSuave.stop();
                double total =
                        fluxoConversa.getBoundsInLocal().getHeight()
                                - rolagem.getViewportBounds().getHeight();
                rolagem.setVvalue(
                        total <= 0
                                ? 0
                                : Math.max(
                                        0, Math.min(1, n.getBoundsInParent().getMinY() / total)));
                animacoes.aparecer(n, 0, 0, 0);
                return;
            }
        aviso.setText(
                "A mensagem #"
                        + id
                        + " está fora do histórico exibido. Use /history 200 ou a busca para encontrá-la.");
    }

    private void sair() {
        if (popupIA != null) popupIA.close();
        iaDisponivel = false;
        ConexaoChat anterior = conexao;
        conexao = null;
        if (anterior != null) anterior.close();
        mostrarLogin("");
        janela.setTitle("Conexo · Chat multiusuário");
    }

    private void fecharJanela() {
        if (popupIA != null) popupIA.close();
        if (encerrando) return;
        if (recursos != null) recursos.fechar();
        if (moldura != null) moldura.encerrarTransicoes();
        encerrando = true;
        filaInterface.close();
        pararEfeitos();
        palco.setMouseTransparent(true);
        ConexaoChat anterior = conexao;
        conexao = null;
        if (anterior != null) anterior.close();
        animacaoJanela.fechar(Platform::exit);
    }

    @Override
    public void stop() {
        filaInterface.close();
        if (moldura != null) moldura.encerrarTransicoes();
        animacaoJanela.parar();
        prepararTela();
        if (conexao != null) conexao.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
