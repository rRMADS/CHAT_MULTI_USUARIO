package model;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Identificadores estáveis: a rede e o banco não transportam imagens nem texto arbitrário. */
public enum EmojiIdentificacao {
    SMILE("smile", "Sorriso", "#FBBF24"),
    UNICORN("unicorn", "Unicórnio", "#E879F9"),
    FOX("fox", "Raposa", "#FB923C"),
    CAT("cat", "Gato", "#FBBF24"),
    DOG("dog", "Cachorro", "#F59E0B"),
    BEAR("bear", "Urso", "#D6A178"),
    PANDA("panda", "Panda", "#C4B5FD"),
    LION("lion", "Leão", "#FBBF24"),
    ROBOT("robot", "Robô", "#60A5FA"),
    ROCKET("rocket", "Foguete", "#FB7185"),
    GHOST("ghost", "Fantasma", "#C4B5FD"),
    ALIEN("alien", "Alienígena", "#86EFAC"),
    BUTTERFLY("butterfly", "Borboleta", "#38BDF8"),
    SUNFLOWER("sunflower", "Girassol", "#FACC15"),
    COFFEE("coffee", "Café", "#F0ABFC"),
    SPARKLES("sparkles", "Brilhos", "#FDE68A"),
    COOL("cool", "Óculos escuros", "#FBBF24"),
    LOVE("love", "Apaixonado", "#FB7185"),
    WINK("wink", "Piscadinha", "#FBBF24"),
    LAUGH("laugh", "Rindo muito", "#FBBF24"),
    PARTY("party", "Festeiro", "#F472B6"),
    THINK("think", "Pensativo", "#FBBF24"),
    STARFACE("starface", "Olhos de estrela", "#FACC15"),
    TIGER("tiger", "Tigre", "#FB923C"),
    KOALA("koala", "Coala", "#C4B5FD"),
    FROG("frog", "Sapo", "#86EFAC"),
    PENGUIN("penguin", "Pinguim", "#7DD3FC"),
    OWL("owl", "Coruja", "#D6A178"),
    OCTOPUS("octopus", "Polvo", "#F0ABFC"),
    TURTLE("turtle", "Tartaruga", "#86EFAC"),
    DOLPHIN("dolphin", "Golfinho", "#7DD3FC"),
    DRAGON("dragon", "Dragão", "#86EFAC"),
    WOLF("wolf", "Lobo", "#CBD5E1"),
    RABBIT("rabbit", "Coelho", "#F9A8D4"),
    BEE("bee", "Abelha", "#FACC15"),
    GAME("game", "Videogame", "#A78BFA"),
    BASKETBALL("basketball", "Basquete", "#FB923C"),
    FOOTBALL("football", "Futebol", "#C4B5FD"),
    GUITAR("guitar", "Guitarra", "#FB7185"),
    HEADPHONES("headphones", "Fone de ouvido", "#A78BFA"),
    ART("art", "Pintura", "#F472B6"),
    BOOKS("books", "Livros", "#60A5FA"),
    BULB("bulb", "Ideia", "#FDE68A"),
    RAINBOW("rainbow", "Arco-íris", "#C4B5FD"),
    PIZZA("pizza", "Pizza", "#FB923C"),
    AVOCADO("avocado", "Abacate", "#86EFAC"),
    PLANET("planet", "Planeta", "#C4B5FD"),
    HEART("heart", "Coração roxo", "#A78BFA");

    public enum Categoria {
        CARINHAS("Carinhas"),
        ANIMAIS("Animais"),
        FANTASIA("Fantasia"),
        OBJETOS("Objetos");
        private final String nome;

        Categoria(String nome) {
            this.nome = nome;
        }

        @Override
        public String toString() {
            return nome;
        }
    }

    private final String id, nome, brilho;

    EmojiIdentificacao(String id, String nome, String brilho) {
        this.id = id;
        this.nome = nome;
        this.brilho = brilho;
    }

    public String id() {
        return id;
    }

    public String nome() {
        return nome;
    }

    public String brilho() {
        return brilho;
    }

    public String recurso() {
        return "/client/emojis/" + id + ".png";
    }

    public Categoria categoria() {
        return switch (this) {
            case SMILE, COOL, LOVE, WINK, LAUGH, PARTY, THINK, STARFACE -> Categoria.CARINHAS;
            case FOX,
                    CAT,
                    DOG,
                    BEAR,
                    PANDA,
                    LION,
                    BUTTERFLY,
                    TIGER,
                    KOALA,
                    FROG,
                    PENGUIN,
                    OWL,
                    OCTOPUS,
                    TURTLE,
                    DOLPHIN,
                    WOLF,
                    RABBIT,
                    BEE ->
                    Categoria.ANIMAIS;
            case UNICORN, ROBOT, GHOST, ALIEN, SPARKLES, DRAGON, RAINBOW -> Categoria.FANTASIA;
            default -> Categoria.OBJETOS;
        };
    }

    private static final Map<String, EmojiIdentificacao> POR_ID =
            Stream.of(values())
                    .collect(Collectors.toUnmodifiableMap(EmojiIdentificacao::id, e -> e));

    public static EmojiIdentificacao porId(String id) {
        EmojiIdentificacao emoji = id == null ? null : POR_ID.get(id);
        if (emoji == null)
            throw new IllegalArgumentException(
                    "Escolha um emoji de identificação para entrar no chat.");
        return emoji;
    }
}
