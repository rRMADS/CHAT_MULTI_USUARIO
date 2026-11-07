package model;

import java.util.List;

public record Reacao(String emoji, int quantidade, boolean minha) {
    public static final List<String> PERMITIDAS=List.of("👍","💜","😂","🎉","👏","😮");
    public Reacao {
        validar(emoji);
        if(quantidade<1) throw new IllegalArgumentException("Quantidade inválida.");
    }
    public static void validar(String emoji) {
        if(!PERMITIDAS.contains(emoji)) throw new IllegalArgumentException("Escolha uma das reações: "+String.join(" ",PERMITIDAS));
    }
}
