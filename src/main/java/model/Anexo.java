package model;

import java.util.*;

public record Anexo(String id, String nome, long tamanho, String tipo) {
    public static final int LIMITE = 5 * 1024 * 1024;
    private static final Map<String, String> TIPOS =
            Map.ofEntries(
                    Map.entry("png", "image/png"),
                    Map.entry("jpg", "image/jpeg"),
                    Map.entry("jpeg", "image/jpeg"),
                    Map.entry("gif", "image/gif"),
                    Map.entry("pdf", "application/pdf"),
                    Map.entry("txt", "text/plain"),
                    Map.entry("csv", "text/csv"),
                    Map.entry("java", "text/plain"),
                    Map.entry(
                            "docx",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                    Map.entry(
                            "xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    Map.entry(
                            "pptx",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                    Map.entry("zip", "application/zip"));

    public Anexo {
        UUID.fromString(id);
        if (nome == null
                || nome.isBlank()
                || nome.length() > 120
                || nome.matches(".*[\\p{Cntrl}\\\\/:*?\"<>|].*")
                || nome.endsWith(".")
                || nome.endsWith(" "))
            throw new IllegalArgumentException("Nome de arquivo inválido.");
        if (tamanho < 1 || tamanho > LIMITE)
            throw new IllegalArgumentException("O arquivo deve ter de 1 byte a 5 MB.");
        String esperado = tipoDe(nome);
        if (!esperado.equals(tipo)) throw new IllegalArgumentException("Tipo de arquivo inválido.");
    }

    public static String tipoDe(String nome) {
        int ponto = nome.lastIndexOf('.');
        String ext = ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
        String tipo = TIPOS.get(ext);
        if (tipo == null)
            throw new IllegalArgumentException(
                    "Envie imagens PNG/JPG/GIF, PDF, TXT, CSV, JAVA, DOCX, XLSX, PPTX ou ZIP.");
        return tipo;
    }

    public boolean imagem() {
        return tipo.startsWith("image/");
    }

    public String tamanhoLegivel() {
        return tamanho < 1024 * 1024
                ? String.format(Locale.ROOT, "%.1f KB", tamanho / 1024.0)
                : String.format(Locale.ROOT, "%.1f MB", tamanho / (1024.0 * 1024));
    }
}
