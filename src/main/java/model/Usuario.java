package model;

import java.util.Locale;
import java.util.Objects;

public final class Usuario {
    private final String nome;
    public Usuario(String nome) {
        if (nome == null || !nome.matches("[\\p{L}\\p{N}_-]{1,30}")) {
            throw new IllegalArgumentException("Use um apelido de 1 a 30 letras, números, _ ou -, sem espaços.");
        }
        this.nome = nome;
    }
    public String nome() { return nome; }
    @Override public boolean equals(Object outro) {
        return outro instanceof Usuario u && nome.toLowerCase(Locale.ROOT).equals(u.nome.toLowerCase(Locale.ROOT));
    }
    @Override public int hashCode() { return Objects.hash(nome.toLowerCase(Locale.ROOT)); }
    @Override public String toString() { return nome; }
}
