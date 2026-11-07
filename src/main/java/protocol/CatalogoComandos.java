package protocol;

import java.util.List;

/** A ajuda do servidor e o painel do cliente usam a mesma lista. */
public final class CatalogoComandos {
    public record Item(String comando,String preencher,String descricao) { }
    public static final List<Item> TODOS=List.of(
        new Item("/help · /ajuda · /","/help","Mostra a ajuda completa"),
        new Item("/salas · /rooms","/salas","Lista as salas disponíveis"),
        new Item("/join · /sala","/join ","Muda de sala. Exemplo: /join estudos"),
        new Item("/users · /usuarios","/users","Lista os participantes da sala"),
        new Item("/history · /historico","/history 50","Carrega de 1 a 200 mensagens públicas"),
        new Item("/buscar","/buscar ","Procura um texto no histórico desta sala"),
        new Item("/msg","/msg \"","Mensagem privada. Exemplo: /msg \"Ana Silva\" Olá"),
        new Item("/ia privado","/ia privado ","Pergunta à IA com resposta só para você"),
        new Item("/ia sala","/ia sala ","Publica pergunta e resposta da IA na sala"),
        new Item("/responder","/responder ","Cita uma mensagem pública: /responder 12 Olá"),
        new Item("/reagir","/reagir ","Adiciona ou remove reação: /reagir 12 💜"),
        new Item("/fixar","/fixar ","Fixa uma mensagem: /fixar 12. Limite: 5 por sala"),
        new Item("/desfixar","/desfixar ","Retira uma mensagem dos avisos: /desfixar 12"),
        new Item("/perfil","/perfil smile ","Altera emoji e apelido: /perfil dragon Ana Silva"),
        new Item("/limpar · /clear · /limparhistorico","/limpar","Solicita a limpeza permanente do histórico público desta sala"),
        new Item("/limpar confirmar","/limpar confirmar","Confirma a limpeza em até 30 segundos"),
        new Item("/limpar cancelar","/limpar cancelar","Cancela a solicitação de limpeza"),
        new Item("/quit · /sair","/quit","Sai do chat"));
    private CatalogoComandos() { }
    public static String ajuda() { return TODOS.stream().map(i->i.comando()+" — "+i.descricao()).collect(java.util.stream.Collectors.joining("\n")); }
}
