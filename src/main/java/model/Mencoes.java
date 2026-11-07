package model;

import java.util.regex.Pattern;

public final class Mencoes {
    private static final Pattern PADRAO=Pattern.compile("(?<![\\p{L}\\p{N}_@])@(?:\"([^\"\\r\\n]+)\"|([\\p{L}\\p{N}_-]+))");
    private Mencoes() { }
    public static boolean contem(String texto,String nome) {
        var m=PADRAO.matcher(texto);
        while(m.find()) if(nome.equalsIgnoreCase(m.group(1)!=null ? m.group(1) : m.group(2))) return true;
        return false;
    }
    public static String para(String nome) { return nome.contains(" ") ? "@\""+nome+"\"" : "@"+nome; }
}
