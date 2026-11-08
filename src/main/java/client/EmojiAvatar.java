package client;

import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import model.EmojiIdentificacao;

import java.util.EnumMap;

/** Os PNGs locais garantem o mesmo emoji em qualquer computador. */
final class EmojiAvatar extends StackPane {
    private static final EnumMap<EmojiIdentificacao, Image> IMAGENS =
            new EnumMap<>(EmojiIdentificacao.class);
    private final ImageView imagem = new ImageView();
    private final Circle brilho;

    EmojiAvatar(EmojiIdentificacao emoji, double tamanho) {
        setMinSize(tamanho, tamanho);
        setPrefSize(tamanho, tamanho);
        setMaxSize(tamanho, tamanho);
        getStyleClass().add("emoji-avatar");
        brilho = new Circle(tamanho * .34);
        brilho.setEffect(new GaussianBlur(tamanho * .15));
        brilho.setOpacity(.75);
        imagem.setFitWidth(tamanho * .85);
        imagem.setFitHeight(tamanho * .85);
        imagem.setPreserveRatio(true);
        imagem.setSmooth(true);
        getChildren().addAll(brilho, imagem);
        atualizar(emoji);
    }

    void atualizar(EmojiIdentificacao emoji) {
        imagem.setImage(
                IMAGENS.computeIfAbsent(
                        emoji, e -> new Image(EmojiAvatar.class.getResourceAsStream(e.recurso()))));
        brilho.setFill(Color.web(emoji.brilho()));
        setAccessibleText("Emoji: " + emoji.nome());
    }
}
