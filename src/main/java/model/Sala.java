package model;
import java.util.List;

public record Sala(String id, String nome) {
    public static final Sala GERAL = new Sala("geral", "Geral");
    public Sala {
        if (id == null || !id.matches("[a-z0-9_-]{1,30}") || nome == null || nome.isBlank() || nome.length() > 40
                || nome.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Sala inválida.");
    }
    public static List<Sala> padrao() {
        return List.of(GERAL, new Sala("estudos", "Estudos"), new Sala("projetos", "Projetos"),
                new Sala("cafe", "Café"), new Sala("livre", "Livre"));
    }
}
