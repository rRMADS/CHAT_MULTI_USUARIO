package model;

import java.util.Arrays;

/** Identificadores estáveis: a rede e o banco não transportam imagens nem texto arbitrário. */
public enum EmojiIdentificacao {
    SMILE("smile", "Sorriso", "#FBBF24"), UNICORN("unicorn", "Unicórnio", "#E879F9"),
    FOX("fox", "Raposa", "#FB923C"), CAT("cat", "Gato", "#FBBF24"),
    DOG("dog", "Cachorro", "#F59E0B"), BEAR("bear", "Urso", "#D6A178"),
    PANDA("panda", "Panda", "#C4B5FD"), LION("lion", "Leão", "#FBBF24"),
    ROBOT("robot", "Robô", "#60A5FA"), ROCKET("rocket", "Foguete", "#FB7185"),
    GHOST("ghost", "Fantasma", "#C4B5FD"), ALIEN("alien", "Alienígena", "#86EFAC"),
    BUTTERFLY("butterfly", "Borboleta", "#38BDF8"), SUNFLOWER("sunflower", "Girassol", "#FACC15"),
    COFFEE("coffee", "Café", "#F0ABFC"), SPARKLES("sparkles", "Brilhos", "#FDE68A"),
    COOL("cool", "Óculos escuros", "#FBBF24"), LOVE("love", "Apaixonado", "#FB7185");

    private final String id, nome, brilho;
    EmojiIdentificacao(String id, String nome, String brilho) { this.id=id; this.nome=nome; this.brilho=brilho; }
    public String id() { return id; }
    public String nome() { return nome; }
    public String brilho() { return brilho; }
    public String recurso() { return "/client/emojis/"+id+".png"; }
    public static EmojiIdentificacao porId(String id) {
        return Arrays.stream(values()).filter(e -> e.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Escolha um emoji de identificação para entrar no chat."));
    }
}
