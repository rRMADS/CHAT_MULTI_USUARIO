package client;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.text.Normalizer;
import java.util.*;

final class SeletorEmojis extends Dialog<String> {
    record Opcao(String simbolo, String nome, String categoria) {}

    static final List<Opcao> CATALOGO =
            Arrays.stream(
                            new String[] {
                                "😀|Sorriso|Carinhas",
                                "😃|Feliz|Carinhas",
                                "😄|Alegria|Carinhas",
                                "😁|Sorridente|Carinhas",
                                "😆|Rindo|Carinhas",
                                "😂|Rindo muito|Carinhas",
                                "🤣|Gargalhada|Carinhas",
                                "😊|Contente|Carinhas",
                                "🙂|Sorriso leve|Carinhas",
                                "😉|Piscadinha|Carinhas",
                                "😍|Apaixonado|Carinhas",
                                "🥰|Carinho|Carinhas",
                                "😘|Beijo|Carinhas",
                                "😎|Óculos escuros|Carinhas",
                                "🤓|Estudioso|Carinhas",
                                "🤔|Pensativo|Carinhas",
                                "🤩|Estrelas nos olhos|Carinhas",
                                "🥳|Festa|Carinhas",
                                "😮|Surpresa|Carinhas",
                                "😴|Sono|Carinhas",
                                "😭|Chorando|Carinhas",
                                "😅|Alívio|Carinhas",
                                "🙃|De cabeça para baixo|Carinhas",
                                "😇|Anjo|Carinhas",
                                "🤗|Abraço|Carinhas",
                                "🤖|Robô|Carinhas",
                                "👻|Fantasma|Carinhas",
                                "👽|Alienígena|Carinhas",
                                "👍|Gostei|Gestos",
                                "👎|Não gostei|Gestos",
                                "👏|Palmas|Gestos",
                                "🙌|Comemorar|Gestos",
                                "👋|Olá|Gestos",
                                "🤝|Acordo|Gestos",
                                "🙏|Obrigado|Gestos",
                                "💪|Força|Gestos",
                                "✌️|Paz|Gestos",
                                "👌|Tudo certo|Gestos",
                                "🎉|Comemoração|Gestos",
                                "🎊|Confete|Gestos",
                                "💜|Coração roxo|Corações",
                                "❤️|Coração vermelho|Corações",
                                "💙|Coração azul|Corações",
                                "💚|Coração verde|Corações",
                                "💛|Coração amarelo|Corações",
                                "🧡|Coração laranja|Corações",
                                "🩷|Coração rosa|Corações",
                                "🤍|Coração branco|Corações",
                                "🖤|Coração preto|Corações",
                                "💖|Carinho brilhante|Corações",
                                "💕|Dois corações|Corações",
                                "🐶|Cachorro|Animais",
                                "🐱|Gato|Animais",
                                "🦊|Raposa|Animais",
                                "🐼|Panda|Animais",
                                "🦁|Leão|Animais",
                                "🐸|Sapo|Animais",
                                "🐧|Pinguim|Animais",
                                "🦉|Coruja|Animais",
                                "🦋|Borboleta|Animais",
                                "🐢|Tartaruga|Animais",
                                "🐬|Golfinho|Animais",
                                "🦄|Unicórnio|Animais",
                                "🐝|Abelha|Animais",
                                "🐙|Polvo|Animais",
                                "🚀|Foguete|Objetos",
                                "⭐|Estrela|Objetos",
                                "✨|Brilhos|Objetos",
                                "🔥|Fogo|Objetos",
                                "🌈|Arco íris|Objetos",
                                "☀️|Sol|Objetos",
                                "🌙|Lua|Objetos",
                                "🌻|Girassol|Objetos",
                                "🌎|Terra|Objetos",
                                "🎮|Videogame|Objetos",
                                "🎧|Fone de ouvido|Objetos",
                                "🎸|Guitarra|Objetos",
                                "🎨|Arte|Objetos",
                                "⚽|Futebol|Objetos",
                                "🏀|Basquete|Objetos",
                                "🏆|Troféu|Objetos",
                                "💻|Computador|Objetos",
                                "📚|Livros|Objetos",
                                "📎|Anexo|Objetos",
                                "✅|Concluído|Objetos",
                                "💡|Ideia|Objetos",
                                "📅|Calendário|Objetos",
                                "☕|Café|Objetos",
                                "🍕|Pizza|Objetos",
                                "🍰|Bolo|Objetos",
                                "🍫|Chocolate|Objetos",
                                "🥑|Abacate|Objetos"
                            })
                    .map(s -> {
                        String[] p = s.split("\\|");
                        return new Opcao(p[0], p[1], p[2]);
                    })
                    .toList();
    private final PreferenciasCliente preferencias = new PreferenciasCliente();
    private final LinkedHashSet<String> favoritos = lerLista("emojis.favoritos"),
            recentes = lerLista("emojis.recentes");
    private String escolhido;

    private LinkedHashSet<String> lerLista(String chave) {
        var lista = new LinkedHashSet<String>();
        for (String s : preferencias.ler(chave, "").split("\\|"))
            if (CATALOGO.stream().anyMatch(e -> e.simbolo().equals(s))) lista.add(s);
        return lista;
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    SeletorEmojis(Window dono, Temas temas) {
        initOwner(dono);
        setTitle("Emojis para sua mensagem");
        setResizable(true);
        var p = getDialogPane();
        p.getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm());
        temas.registrar(p);
        TextField busca = new TextField();
        busca.setPromptText("Buscar emoji…");
        busca.setId("busca-emoji-mensagem");
        ComboBox<String> categoria = new ComboBox<>();
        categoria
                .getItems()
                .addAll(
                        "Todos",
                        "Favoritos",
                        "Recentes",
                        "Carinhas",
                        "Gestos",
                        "Corações",
                        "Animais",
                        "Objetos");
        categoria.setId("categoria-emojis-mensagem");
        categoria.setValue("Todos");
        categoria.setMaxWidth(Double.MAX_VALUE);
        TilePane grade = new TilePane(7, 7);
        grade.setPrefColumns(7);
        grade.setPrefTileWidth(48);
        grade.setPrefTileHeight(48);
        grade.setMinWidth(0);
        Label selecao = new Label("Escolha um emoji");
        Button favorito = new Button("☆ Favoritar");
        favorito.setId("favoritar-emoji");
        favorito.setDisable(true);
        ButtonType inserir = new ButtonType("Inserir emoji", ButtonBar.ButtonData.OK_DONE);
        p.getButtonTypes().addAll(ButtonType.CANCEL, inserir);
        p.lookupButton(inserir).setDisable(true);
        Runnable filtrar =
                () -> {
                    grade.getChildren().clear();
                    ToggleGroup grupo = new ToggleGroup();
                    List<Opcao> opcoes =
                            categoria.getValue().equals("Recentes")
                                    ? recentes.stream()
                                            .flatMap(s ->
                                                    CATALOGO.stream()
                                                            .filter(o -> o.simbolo().equals(s)))
                                            .toList()
                                    : CATALOGO;
                    for (var opcao : opcoes) {
                        String c = categoria.getValue();
                        boolean aceita =
                                c.equals("Todos")
                                        || c.equals("Favoritos")
                                                && favoritos.contains(opcao.simbolo())
                                        || c.equals("Recentes")
                                        || c.equals(opcao.categoria());
                        if (!aceita
                                || !normalizar(opcao.nome() + opcao.simbolo())
                                        .contains(normalizar(busca.getText()))) continue;
                        ToggleButton b = new ToggleButton(opcao.simbolo());
                        b.setTextOverrun(OverrunStyle.CLIP);
                        b.setMinSize(48, 48);
                        b.setPrefSize(48, 48);
                        b.setMaxSize(48, 48);
                        b.getStyleClass().add("emoji-message-choice");
                        b.setToggleGroup(grupo);
                        b.setSelected(opcao.simbolo().equals(escolhido));
                        b.setTooltip(new Tooltip(opcao.nome()));
                        b.setAccessibleText(opcao.nome());
                        b.setOnAction(e -> {
                            escolhido = opcao.simbolo();
                            b.setSelected(true);
                            selecao.setText(escolhido + "  " + opcao.nome());
                            favorito.setDisable(false);
                            favorito.setText(
                                    favoritos.contains(escolhido)
                                            ? "★ Remover favorito"
                                            : "☆ Favoritar");
                            p.lookupButton(inserir).setDisable(false);
                        });
                        grade.getChildren().add(b);
                    }
                    if (grade.getChildren().isEmpty())
                        grade.getChildren().add(new Label("Nenhum emoji nesta seleção."));
                };
        favorito.setOnAction(e -> {
            if (!favoritos.remove(escolhido)) favoritos.add(escolhido);
            preferencias.salvar("emojis.favoritos", String.join("|", favoritos));
            favorito.setText(favoritos.contains(escolhido) ? "★ Remover favorito" : "☆ Favoritar");
            filtrar.run();
        });
        busca.textProperty().addListener((o, a, b) -> filtrar.run());
        categoria.setOnAction(e -> filtrar.run());
        filtrar.run();
        ScrollPane scroll = new ScrollPane(grade);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(
                Math.min(
                        280, javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() - 330));
        VBox conteudo = new VBox(12, busca, categoria, scroll, selecao, favorito);
        conteudo.setPrefWidth(460);
        p.setContent(conteudo);
        setResultConverter(b -> {
            if (b != inserir) return null;
            var ordem = new LinkedHashSet<String>();
            ordem.add(escolhido);
            ordem.addAll(recentes);
            preferencias.salvar(
                    "emojis.recentes", String.join("|", ordem.stream().limit(16).toList()));
            return escolhido;
        });
    }
}
