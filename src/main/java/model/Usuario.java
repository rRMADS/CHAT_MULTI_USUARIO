package model;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Usuario {
    private static final Pattern NOME = Pattern.compile("[\\p{L}\\p{N}_-]+(?: [\\p{L}\\p{N}_-]+)*");
    private static final Pattern ESPACOS = Pattern.compile(" +");
    private final String nome, nomeComparavel;
    private final EmojiIdentificacao emoji;

    public Usuario(String nome) {
        this(nome, EmojiIdentificacao.SMILE.id());
    }

    public Usuario(String nome, String emoji) {
        if (nome == null || nome.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "Use um apelido de 1 a 30 caracteres: letras, números, espaços, _ ou -.");
        }
        String normalizado = ESPACOS.matcher(nome.strip()).replaceAll(" ");
        if (normalizado.length() > 30 || !NOME.matcher(normalizado).matches()) {
            throw new IllegalArgumentException(
                    "Use um apelido de 1 a 30 caracteres: letras, números, espaços, _ ou -.");
        }
        this.nome = normalizado;
        this.nomeComparavel = normalizado.toLowerCase(Locale.ROOT);
        this.emoji = EmojiIdentificacao.porId(emoji);
    }

    public String nome() {
        return nome;
    }

    public EmojiIdentificacao emoji() {
        return emoji;
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof Usuario u && nomeComparavel.equals(u.nomeComparavel);
    }

    @Override
    public int hashCode() {
        return nomeComparavel.hashCode();
    }

    @Override
    public String toString() {
        return nome;
    }
}
