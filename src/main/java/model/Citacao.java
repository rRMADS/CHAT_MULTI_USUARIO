package model;

/** Resumo de uma mensagem pública; o conteúdo original permanece no histórico. */
public record Citacao(long id, String autor, String texto) {
    public Citacao {
        if (id <= 0 || autor == null || texto == null)
            throw new IllegalArgumentException("Citação inválida.");
        texto = texto.replaceAll("\\s+", " ").strip();
        if (texto.length() > 180) texto = texto.substring(0, 180) + "…";
    }

    public static Citacao de(Mensagem mensagem) {
        return new Citacao(mensagem.id(), mensagem.remetente().nome(), mensagem.conteudo());
    }
}
