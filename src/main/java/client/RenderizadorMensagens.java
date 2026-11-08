package client;

import static client.Componentes.*;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import model.*;

import org.kordamp.ikonli.feather.Feather;

import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/** Monta os cartões sem conhecer sockets, salas ou o ciclo de vida da aplicação. */
final class RenderizadorMensagens {
    private static final DateTimeFormatter HORARIO = DateTimeFormatter.ofPattern("dd/MM · HH:mm");
    private final ReadOnlyDoubleProperty larguraConversa;
    private final Supplier<String> apelidoAtual;
    private final RecursosConversa recursos;
    private final Animacoes animacoes;

    RenderizadorMensagens(
            ReadOnlyDoubleProperty larguraConversa,
            Supplier<String> apelidoAtual,
            RecursosConversa recursos,
            Animacoes animacoes) {
        this.larguraConversa = larguraConversa;
        this.apelidoAtual = apelidoAtual;
        this.recursos = recursos;
        this.animacoes = animacoes;
    }

    Node mensagem(Mensagem mensagem, String privadoPara, boolean carregandoHistorico) {
        String remetente = mensagem.remetente().nome(),
                conteudo = mensagem.conteudo(),
                apelido = apelidoAtual.get();
        Long id = mensagem.id();
        EmojiIdentificacao emoji = mensagem.remetente().emoji();
        boolean formatada = mensagem.formato() == FormatoMensagem.MARKDOWN_IA;
        Citacao citacao = mensagem.citacao();
        boolean minha = remetente.equalsIgnoreCase(apelido);
        String hora = mensagem.horario().format(HORARIO);
        Label autor = label(minha ? "Você" : remetente, "message-author");
        Label meta = label(hora + (id == null ? "" : " · #" + id), "message-meta");
        HBox cabecalho = new HBox(10, autor, meta);
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        Node corpo;
        if (formatada) corpo = new ConteudoMarkdown(conteudo);
        else {
            Label textoCorpo = label(conteudo, "message-body");
            textoCorpo.setWrapText(true);
            textoCorpo.setMinWidth(0);
            textoCorpo.setMaxWidth(580);
            corpo = textoCorpo;
        }
        VBox bolha = new VBox(8, cabecalho, corpo);
        if (citacao != null) bolha.getChildren().add(1, recursos.citacao(citacao));
        if (!minha && privadoPara == null && Mencoes.contem(conteudo, apelido)) {
            bolha.getStyleClass().add("mention-me");
            bolha.getChildren().add(label("Mencionou você", "mention-label"));
        }
        bolha.getStyleClass().add(minha ? "bubble-own" : "bubble");
        bolha.setMaxWidth(620);
        bolha.maxWidthProperty()
                .bind(
                        larguraConversa
                                .subtract(minha ? 66 : 122)
                                .map(w ->
                                        Math.max(
                                                180,
                                                Math.min(formatada ? 760 : 620, w.doubleValue()))));
        if (formatada) {
            bolha.getStyleClass().add("bubble-ai");
            bolha.setPrefWidth(760);
            FlowPane ferramentasIA =
                    new FlowPane(
                            12,
                            8,
                            label("Resposta gerada por IA", "small-muted"),
                            ConteudoMarkdown.copiar(conteudo));
            ferramentasIA.setAlignment(Pos.CENTER_LEFT);
            bolha.getChildren().add(ferramentasIA);
        }
        if (privadoPara != null) {
            bolha.getStyleClass().add("bubble-private");
            Label privada =
                    label(
                            minha
                                    ? "Privada para " + privadoPara
                                    : "Conversa privada · só para você",
                            "private-label");
            privada.setGraphic(Icones.criar(Feather.LOCK, 12));
            bolha.getChildren().add(0, privada);
        } else if (minha) {
            // O servidor só transmite a mensagem pública depois de persistir no JDBC.
            Label confirmacao = label("Salva no servidor", "message-receipt");
            confirmacao.setGraphic(Icones.criar(Feather.CHECK, 12));
            confirmacao.setTooltip(
                    new Tooltip(
                            "Mensagem pública registrada no histórico. Não indica leitura pelos participantes."));
            bolha.getChildren().add(confirmacao);
            if (!carregandoHistorico) animacoes.aparecer(confirmacao, 0, 3, 160);
        }
        if (id != null) recursos.registrar(id, remetente, conteudo, bolha);
        ContextMenu menu = new ContextMenu();
        MenuItem copiar = new MenuItem("Copiar mensagem", Icones.criar(Feather.COPY, 14));
        copiar.setOnAction(e -> {
            copiarTexto(conteudo);
        });
        menu.getItems().add(copiar);
        bolha.setOnContextMenuRequested(e -> menu.show(bolha, e.getScreenX(), e.getScreenY()));
        HBox linha = new HBox(10);
        linha.setAlignment(formatada ? Pos.TOP_LEFT : minha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if (!minha) linha.getChildren().add(new EmojiAvatar(emoji, 42));
        linha.getChildren().add(bolha);
        if (minha) linha.getChildren().add(new EmojiAvatar(emoji, 42));
        linha.setUserData(minha ? 6.0 : -6.0);
        linha.getProperties().put("mensagem-publica", privadoPara == null);
        if (id != null) linha.getProperties().put("mensagem-id", id);
        return linha;
    }

    Node respostaIA(String pergunta, String resposta) {
        Label cabecalho = label("Conexo IA · privado", "command-title");
        cabecalho.setGraphic(new EmojiAvatar(EmojiIdentificacao.ROBOT, 36));
        Label pedido = label("Você: " + pergunta, "muted");
        pedido.setWrapText(true);
        pedido.setMinWidth(0);
        Label privacidade =
                label("Visível apenas para você · Resposta gerada por IA", "small-muted");
        privacidade.setWrapText(true);
        VBox cartao =
                new VBox(
                        12,
                        cabecalho,
                        pedido,
                        new ConteudoMarkdown(resposta),
                        privacidade,
                        ConteudoMarkdown.copiar(resposta));
        cartao.getStyleClass().add("command-reply");
        cartao.setMinWidth(0);
        cartao.setPrefWidth(760);
        cartao.maxWidthProperty()
                .bind(
                        larguraConversa
                                .subtract(60)
                                .map(w -> Math.max(180, Math.min(760, w.doubleValue()))));
        return new HBox(cartao);
    }

    Node respostaComando(String titulo, String conteudo, boolean erro) {
        Label cabecalho = label(titulo, "command-title");
        Label corpo = label(conteudo, "command-body");
        cabecalho.setGraphic(Icones.criar(erro ? Feather.ALERT_CIRCLE : Feather.TERMINAL, 17));
        corpo.setWrapText(true);
        corpo.setMaxWidth(560);
        VBox cartao =
                new VBox(10, cabecalho, corpo, label("Visível apenas para você", "small-muted"));
        cartao.getStyleClass().add("command-reply");
        cartao.setMaxWidth(600);
        if (erro) cartao.getStyleClass().add("command-error");
        ContextMenu menu = new ContextMenu();
        MenuItem copiar = new MenuItem("Copiar resposta");
        copiar.setOnAction(e -> {
            copiarTexto(conteudo);
        });
        menu.getItems().add(copiar);
        cartao.setOnContextMenuRequested(e -> menu.show(cartao, e.getScreenX(), e.getScreenY()));
        HBox linha = new HBox(cartao);
        linha.setAlignment(Pos.CENTER_LEFT);
        return linha;
    }
}
