package model;

import java.util.Locale;
import java.util.Objects;

public final class Usuario {
    private final String nome;
    public Usuario(String nome) {
        if (nome == null || nome.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Use um apelido de 1 a 30 caracteres: letras, números, espaços, _ ou -.");
        }
        String normalizado = nome.strip().replaceAll(" +", " ");
        if (normalizado.length() > 30 || !normalizado.matches("[\\p{L}\\p{N}_-]+(?: [\\p{L}\\p{N}_-]+)*")) {
            throw new IllegalArgumentException("Use um apelido de 1 a 30 caracteres: letras, números, espaços, _ ou -.");
        }
        this.nome = normalizado;
    }
    public String nome() { return nome; }
    @Override public boolean equals(Object outro) {
        return outro instanceof Usuario u && nome.toLowerCase(Locale.ROOT).equals(u.nome.toLowerCase(Locale.ROOT));
    }
    @Override public int hashCode() { return Objects.hash(nome.toLowerCase(Locale.ROOT)); }
    @Override public String toString() { return nome; }
}
