package model;

public final class Marca {
    public static final String NOME = "Orkut 2.0";
    public static final String ASSISTENTE = "Orkut IA";

    private Marca() {}

    public static boolean assistente(String nome) {
        return ASSISTENTE.equalsIgnoreCase(nome) || "Conexo IA".equalsIgnoreCase(nome);
    }
}
