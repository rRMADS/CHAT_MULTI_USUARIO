package client;

import static client.Componentes.*;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import org.kordamp.ikonli.feather.Feather;

import java.util.function.DoubleConsumer;

/** Formulário e ilustração de entrada; não acessa a rede nem credenciais da IA. */
final class TelaLogin {
    final TextField nome, host, porta;
    final Label erro;
    final Button conectar;
    final ViewportAdaptavel vista;
    private final Animacoes animacoes;

    TelaLogin(
            String apelido,
            String mensagem,
            Temas temas,
            Animacoes animacoes,
            Runnable aoConectar,
            DoubleConsumer painelDireito,
            boolean visivel) {
        this.animacoes = animacoes;
        VBox apresentacao = new VBox(18);
        apresentacao.getStyleClass().add("intro");
        apresentacao.setPrefWidth(470);
        apresentacao.setMinWidth(410);
        apresentacao.setMaxWidth(470);
        Label marca = marca();
        Label selo = label("UM ESPAÇO PARA SE CONECTAR", "eyebrow");
        Label chamada = label("Boas conversas\ncomeçam com\numa conexão.", "hero");
        Label descricao =
                label(
                        "Na mesma rede, na mesma conversa.\nSeu chat em tempo real, simples e acolhedor.",
                        "intro-copy");
        Pane desenho = ilustracao();
        VBox.setVgrow(desenho, Priority.ALWAYS);
        Label rodape = label("Seu espaço para trocar ideias.", "intro-footer");
        apresentacao.getChildren().addAll(marca, selo, chamada, descricao, desenho, rodape);

        VBox formulario = new VBox(12);
        formulario.getStyleClass().add("login-form");
        formulario.setMaxWidth(540);
        formulario.setMinWidth(0);
        formulario.setMinHeight(Region.USE_PREF_SIZE);
        formulario.setMaxHeight(Region.USE_PREF_SIZE);
        Label passo = label("OLÁ, QUE BOM TER VOCÊ AQUI", "eyebrow-green");
        Label heading = label("Entre na conversa", "login-title");
        Label explicacao =
                label(
                        "Escolha seu apelido e o endereço do servidor.\nCada pessoa entra pelo próprio computador.",
                        "muted");
        explicacao.setWrapText(true);
        explicacao.setMinHeight(Region.USE_PREF_SIZE);
        nome = new TextField(apelido == null ? "" : apelido);
        nome.setPromptText("Como você quer ser chamado?");
        nome.setId("apelido");
        host = new TextField("127.0.0.1");
        host.setPromptText("Ex.: 192.168.1.10");
        host.setId("ip");
        porta = new TextField("5050");
        porta.setId("porta");
        porta.setPrefWidth(100);
        porta.setMaxWidth(110);
        VBox campoHost = new VBox(7, label("IP ou nome do servidor", "field-label"), host);
        campoHost.setMinWidth(0);
        host.setMinWidth(0);
        HBox.setHgrow(campoHost, Priority.ALWAYS);
        HBox rede = new HBox(12, campoHost, new VBox(7, label("Porta", "field-label"), porta));
        erro = label(mensagem, "error");
        erro.setWrapText(true);
        erro.managedProperty().bind(erro.textProperty().isNotEmpty());
        erro.visibleProperty().bind(erro.managedProperty());
        this.conectar = botao(animacoes, "Conectar ao chat", "primary", Feather.ARROW_RIGHT);
        this.conectar.setContentDisplay(ContentDisplay.RIGHT);
        this.conectar.setMaxWidth(Double.MAX_VALUE);
        this.conectar.setDefaultButton(true);
        this.conectar.setId("conectar");
        this.conectar.setOnAction(e -> aoConectar.run());
        nome.setOnAction(e -> aoConectar.run());
        Label dica =
                label(
                        "No mesmo computador, use 127.0.0.1.\nEm outra máquina, use o IP exibido pelo servidor.",
                        "small-muted");
        dica.setWrapText(true);
        Label privacidade = label("Várias salas · Uma rede de conversas", "small-muted");
        privacidade.setGraphic(Icones.criar(Feather.HASH, 14));
        formulario
                .getChildren()
                .addAll(
                        temas.seletor(false),
                        passo,
                        heading,
                        explicacao,
                        espaco(8),
                        label("Seu apelido", "field-label"),
                        nome,
                        rede,
                        erro,
                        this.conectar,
                        dica,
                        espaco(6),
                        privacidade);
        StackPane areaFormulario = new StackPane(formulario);
        areaFormulario.setMinWidth(0);
        areaFormulario.setMinHeight(Region.USE_PREF_SIZE);
        areaFormulario.setPadding(new Insets(24));
        HBox.setHgrow(areaFormulario, Priority.ALWAYS);
        HBox raiz = new HBox(areaFormulario, apresentacao);
        raiz.getStyleClass().add("login-root");
        vista = new ViewportAdaptavel(raiz, 280, Region.USE_PREF_SIZE);
        Runnable adaptar =
                () -> {
                    boolean amplo = vista.getWidth() >= 940 && vista.getHeight() >= 760;
                    apresentacao.setVisible(amplo);
                    apresentacao.setManaged(amplo);
                    painelDireito.accept(amplo ? apresentacao.getWidth() : 0);
                    raiz.pseudoClassStateChanged(
                            javafx.css.PseudoClass.getPseudoClass("compact"),
                            vista.getWidth() < 620);
                    areaFormulario.setPadding(new Insets(vista.getWidth() < 620 ? 12 : 24));
                };
        vista.widthProperty().addListener((o, a, b) -> adaptar.run());
        vista.heightProperty().addListener((o, a, b) -> adaptar.run());
        apresentacao.widthProperty().addListener((o, a, b) -> adaptar.run());
        adaptar.run();
        if (visivel) animacoes.aparecer(formulario, 0, 8, 45);
        Platform.runLater(nome::requestFocus);
    }

    private Pane ilustracao() {
        Pane pane = new Pane();
        pane.setMinHeight(210);
        pane.setPrefHeight(220);
        Circle halo = new Circle(180, 104, 78);
        halo.getStyleClass().add("intro-halo");
        halo.setOpacity(.4);
        Circle orbita = new Circle(180, 104, 103, Color.TRANSPARENT);
        orbita.getStyleClass().add("intro-orbit");
        orbita.getStrokeDashArray().addAll(3.0, 9.0);
        Label a = label("Olá, que bom te ver!", "illustration-bubble");
        a.relocate(18, 129);
        Label b = label("Vamos conversar?", "illustration-bubble-alt");
        b.relocate(150, 42);
        Label c = label("●  Ideias que aproximam", "illustration-caption");
        c.relocate(80, 182);
        Circle ponto = new Circle(294, 142, 6);
        ponto.getStyleClass().add("intro-dot");
        pane.getChildren().addAll(halo, orbita, a, b, ponto, c);
        animacoes.flutuar(a, -3, 4.8, 0);
        animacoes.flutuar(b, 3, 5.4, 350);
        animacoes.pulsar(ponto);
        return pane;
    }
}
