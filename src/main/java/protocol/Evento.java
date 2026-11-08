package protocol;

import java.util.List;

public record Evento(Tipo tipo, List<String> campos) {
    public enum Tipo {
        BEM_VINDO,
        MENSAGEM,
        PRIVADA,
        SISTEMA,
        USUARIOS,
        HISTORICO_INICIO,
        HISTORICO_FIM,
        ERRO,
        DIGITANDO,
        PONG,
        SALAS,
        HISTORICO_LIMPO,
        APRESENTACAO,
        PERFIS,
        IA_DISPONIVEL,
        IA_INICIO,
        IA_RESPOSTA,
        IA_ERRO,
        REACOES,
        FIXADAS,
        MENCAO,
        PERFIL_ALTERADO,
        ATIVIDADE_SALA,
        BUSCA_FILTROS,
        RESUMIR,
        ARQUIVO_INICIO,
        ARQUIVO_PARTE,
        ARQUIVO_FIM,
        ARQUIVO_CANCELAR,
        ARQUIVO_PEDIR,
        ARQUIVO_PROGRESSO,
        ARQUIVO_ERRO,
        ARQUIVO_PRONTO,
        DOWNLOAD_INICIO,
        DOWNLOAD_PARTE,
        DOWNLOAD_FIM
    }

    public Evento {
        campos = List.copyOf(campos);
    }

    public static Evento de(Tipo tipo, String... campos) {
        return new Evento(tipo, List.of(campos));
    }

    public static Evento resposta(String titulo, String texto) {
        return de(Tipo.SISTEMA, texto, titulo);
    }

    public String campo(int indice) {
        return campos.get(indice);
    }
}
