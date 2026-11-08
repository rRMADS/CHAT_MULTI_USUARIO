package client;

import javafx.geometry.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

import org.commonmark.ext.gfm.tables.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;

import java.util.*;

/**
 * Markdown em componentes JavaFX. HTML, scripts e imagens remotas nunca são executados/carregados.
 */
final class ConteudoMarkdown extends VBox {
    private static final Parser PARSER =
            Parser.builder()
                    .extensions(List.of(TablesExtension.builder().maxCells(500).build()))
                    .build();

    ConteudoMarkdown(String original) {
        super(11);
        getStyleClass().add("markdown");
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        try {
            blocos(PARSER.parse(original), this, 0);
        } catch (RuntimeException e) {
            getChildren().setAll(textoSimples(original));
        }
        setAccessibleText(original);
    }

    static Button copiar(String texto) {
        Button b = new Button("Copiar resposta");
        b.getStyleClass().add("command-help");
        b.setOnAction(e -> Componentes.copiarTexto(texto));
        return b;
    }

    private static TextFlow textoSimples(String literal) {
        TextFlow f = new TextFlow();
        f.setMinWidth(0);
        f.setMaxWidth(Double.MAX_VALUE);
        f.getStyleClass().add("md-paragraph");
        adicionar(f, literal, false, false, false);
        return f;
    }

    private static TextFlow texto(Node bloco) {
        TextFlow f = new TextFlow();
        f.setMinWidth(0);
        f.setMaxWidth(Double.MAX_VALUE);
        f.getStyleClass().add("md-paragraph");
        inline(bloco, f, false, false, 0);
        return f;
    }

    private static void adicionar(
            TextFlow f, String literal, boolean negrito, boolean italico, boolean codigo) {
        var t = new javafx.scene.text.Text(literal);
        t.getStyleClass().add("md-text");
        if (negrito) t.getStyleClass().add("md-strong");
        if (italico) t.getStyleClass().add("md-emphasis");
        if (codigo) t.getStyleClass().add("md-inline-code");
        f.getChildren().add(t);
    }

    private static void inline(
            Node pai, TextFlow fluxo, boolean negrito, boolean italico, int profundidade) {
        if (profundidade > 48) return;
        for (Node n = pai.getFirstChild(); n != null; n = n.getNext()) {
            if (n instanceof Text t) adicionar(fluxo, t.getLiteral(), negrito, italico, false);
            else if (n instanceof Code c) adicionar(fluxo, c.getLiteral(), negrito, italico, true);
            else if (n instanceof SoftLineBreak || n instanceof HardLineBreak)
                adicionar(fluxo, "\n", negrito, italico, false);
            else if (n instanceof HtmlInline h)
                adicionar(fluxo, h.getLiteral(), negrito, italico, true);
            else {
                if (n instanceof Image) adicionar(fluxo, "Imagem: ", negrito, italico, false);
                inline(
                        n,
                        fluxo,
                        negrito || n instanceof StrongEmphasis,
                        italico || n instanceof Emphasis,
                        profundidade + 1);
                if (n instanceof Link l)
                    adicionar(fluxo, " (" + l.getDestination() + ")", false, false, false);
            }
        }
    }

    private static void blocos(Node pai, VBox destino, int profundidade) {
        if (profundidade > 32) return;
        for (Node n = pai.getFirstChild(); n != null; n = n.getNext()) {
            if (n instanceof Paragraph) destino.getChildren().add(texto(n));
            else if (n instanceof Heading h) {
                TextFlow f = texto(n);
                f.getStyleClass().add("md-h" + h.getLevel());
                destino.getChildren().add(f);
            } else if (n instanceof FencedCodeBlock c)
                destino.getChildren().add(codigo(c.getLiteral(), c.getInfo()));
            else if (n instanceof IndentedCodeBlock c)
                destino.getChildren().add(codigo(c.getLiteral(), "Código"));
            else if (n instanceof ListBlock lista) {
                VBox itens = new VBox(7);
                itens.getStyleClass().add("md-list");
                int numero =
                        lista instanceof OrderedList ordenada
                                        && ordenada.getMarkerStartNumber() != null
                                ? ordenada.getMarkerStartNumber()
                                : 1;
                for (Node item = n.getFirstChild(); item != null; item = item.getNext()) {
                    Label marcador =
                            new Label(lista instanceof OrderedList ? (numero++) + "." : "•");
                    marcador.getStyleClass().add("md-marker");
                    marcador.setMinWidth(27);
                    marcador.setAlignment(Pos.TOP_RIGHT);
                    VBox corpo = new VBox(6);
                    corpo.setMinWidth(0);
                    HBox.setHgrow(corpo, Priority.ALWAYS);
                    blocos(item, corpo, profundidade + 1);
                    HBox linha = new HBox(9, marcador, corpo);
                    linha.setMinWidth(0);
                    itens.getChildren().add(linha);
                }
                destino.getChildren().add(itens);
            } else if (n instanceof BlockQuote) {
                VBox citacao = new VBox(8);
                citacao.getStyleClass().add("md-quote");
                blocos(n, citacao, profundidade + 1);
                destino.getChildren().add(citacao);
            } else if (n instanceof ThematicBreak) destino.getChildren().add(new Separator());
            else if (n instanceof TableBlock) destino.getChildren().add(new Tabela(n));
            else if (n instanceof HtmlBlock h)
                destino.getChildren().add(textoSimples(h.getLiteral()));
            else if (n.getFirstChild() != null) blocos(n, destino, profundidade + 1);
        }
    }

    private static VBox codigo(String literal, String linguagem) {
        Label titulo = new Label(linguagem == null || linguagem.isBlank() ? "Código" : linguagem);
        titulo.getStyleClass().add("small-muted");
        titulo.setMinWidth(0);
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        Button copiar = copiar(literal);
        copiar.setText("Copiar código");
        HBox cabecalho = new HBox(8, titulo, espaco, copiar);
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        TextArea conteudo = new TextArea(literal.stripTrailing());
        conteudo.setEditable(false);
        conteudo.setWrapText(false);
        conteudo.getStyleClass().add("md-code");
        conteudo.setMinWidth(0);
        conteudo.setPrefHeight(Math.min(300, Math.max(65, literal.lines().count() * 21 + 20)));
        VBox bloco = new VBox(8, cabecalho, conteudo);
        bloco.getStyleClass().add("md-code-block");
        bloco.setMinWidth(0);
        return bloco;
    }

    /** A tabela quebra texto nas células e ganha rolagem horizontal em janelas pequenas. */
    private static final class Tabela extends Region {
        private final GridPane grade = new GridPane();
        private final ScrollPane rolagem;
        private final double larguraMinima;

        Tabela(Node tabela) {
            getStyleClass().add("md-table");
            List<TableRow> linhas = new ArrayList<>();
            for (Node secao = tabela.getFirstChild(); secao != null; secao = secao.getNext())
                for (Node linha = secao.getFirstChild(); linha != null; linha = linha.getNext())
                    if (linha instanceof TableRow r) linhas.add(r);
            int colunas = 0;
            for (var linha : linhas) {
                int c = 0;
                for (Node celula = linha.getFirstChild(); celula != null; celula = celula.getNext())
                    c++;
                colunas = Math.max(colunas, c);
            }
            larguraMinima = Math.max(140, colunas * 145);
            grade.setMinWidth(larguraMinima);
            grade.setMinHeight(USE_PREF_SIZE);
            for (int i = 0; i < colunas; i++) {
                ColumnConstraints c = new ColumnConstraints();
                c.setPercentWidth(100.0 / colunas);
                c.setHgrow(Priority.ALWAYS);
                grade.getColumnConstraints().add(c);
            }
            for (int r = 0; r < linhas.size(); r++) {
                int c = 0;
                for (Node celula = linhas.get(r).getFirstChild();
                        celula != null;
                        celula = celula.getNext()) {
                    TextFlow texto = texto(celula);
                    if (celula instanceof TableCell t && t.getAlignment() != null)
                        texto.setTextAlignment(
                                switch (t.getAlignment()) {
                                    case LEFT -> TextAlignment.LEFT;
                                    case CENTER -> TextAlignment.CENTER;
                                    case RIGHT -> TextAlignment.RIGHT;
                                });
                    VBox caixa = new VBox(texto);
                    caixa.setMinWidth(0);
                    caixa.getStyleClass()
                            .add(
                                    celula instanceof TableCell t && t.isHeader()
                                            ? "md-table-head"
                                            : "md-table-cell");
                    GridPane.setFillWidth(caixa, true);
                    GridPane.setVgrow(caixa, Priority.ALWAYS);
                    grade.add(caixa, c++, r);
                }
            }
            rolagem = new ScrollPane(grade);
            rolagem.setFitToWidth(true);
            rolagem.setFitToHeight(true);
            rolagem.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            rolagem.getStyleClass().add("md-table-scroll");
            getChildren().add(rolagem);
            setMinWidth(0);
            setMaxWidth(Double.MAX_VALUE);
            setMinHeight(USE_PREF_SIZE);
        }

        @Override
        protected double computePrefWidth(double h) {
            return Math.min(700, larguraMinima);
        }

        @Override
        protected double computePrefHeight(double w) {
            return grade.prefHeight(Math.max(larguraMinima, w)) + (w < larguraMinima ? 20 : 2);
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected void layoutChildren() {
            rolagem.resizeRelocate(0, 0, getWidth(), getHeight());
        }
    }
}
