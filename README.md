# Conexo

Chat multiusuário desktop em Java, com sockets TCP, histórico JDBC e interface JavaFX com AtlantaFX, ícones Ikonli e animações AnimateFX.

## Download e requisitos

No [repositório do GitHub](https://github.com/rRMADS/CHAT_MULTI_USUARIO), use **Code → Download ZIP** e extraia todo o conteúdo antes de executar. Também é possível clonar o repositório.

É necessário ter **JDK 17 ou superior** (recomendado JDK 21) e internet na primeira execução. **Não é necessário instalar Maven ou IntelliJ:** o Maven Wrapper incluído prepara o Maven automaticamente. O H2 também é baixado como biblioteca, sem instalação de banco separada.

As instruções abaixo explicam como executar no Windows, conectar participantes e rodar os testes pelo terminal.

## Como abrir no Windows

1. Abra `Servidor.cmd` no computador que vai hospedar a conversa. Mantenha essa janela aberta.
2. Abra `Cliente.cmd` para cada participante. Para testar no mesmo computador, abra o cliente três vezes.
3. Escolha apelidos diferentes. Use IP `127.0.0.1` e porta `5050` no mesmo computador.
4. Em outro computador da mesma rede, use o endereço IPv4 mostrado pelo servidor e a mesma porta.
5. Envie uma mensagem. Todos os participantes da mesma sala recebem a mensagem.
6. Na lateral do cliente, use **Salas do servidor** para ir a Geral, Estudos, Projetos, Café ou Livre, sem sair do programa.

A primeira preparação baixa as bibliotecas e exige internet. Depois disso, os lançadores usam os arquivos já compilados, recompilando quando as fontes mudam. Não abra vários lançadores durante o primeiro download. O projeto foi testado em Windows com JDK 26.0.2.1 e compilado para Java 17. Cada computador precisa ter o projeto extraído e um JDK; ainda não há instalador independente com Java embutido.

O IP identifica o computador servidor; a porta identifica o serviço de chat. O apelido identifica o participante. O servidor imprime os endereços das interfaces de rede; escolha aquele da rede compartilhada com a outra pessoa. Não use `0.0.0.0` como endereço no cliente.

## Várias conversas no mesmo servidor

Ao abrir `Servidor.cmd`, as salas configuradas são disponibilizadas automaticamente. O padrão é **Geral, Estudos, Projetos, Café e Livre**. Elas usam **o mesmo IP e a mesma porta**. Não é necessário criar uma faixa de IPs nem abrir um servidor para cada conversa.

Você entra na Geral e pode escolher outra sala na lista lateral. A lista mostra a quantidade de pessoas em cada sala. A troca mantém a conexão TCP e carrega o histórico da sala escolhida. Para conversar com outra pessoa, os dois devem escolher a mesma sala.

Mensagens públicas, privadas, avisos, participantes, busca e digitação têm o escopo da sala atual. O histórico de uma sala não aparece nas outras. Os apelidos precisam ser únicos dentro da sala: se seu apelido já estiver sendo usado no destino, a troca é recusada e você continua na sala anterior.

Ao atualizar uma instalação antiga, o banco é atualizado automaticamente e as mensagens existentes permanecem na **Geral**. Para usar as salas, atualize e reinicie o servidor e os clientes.

### Como alterar, adicionar ou retirar salas

1. Na pasta do projeto **no computador do servidor**, copie `chat.properties.example` e renomeie a cópia para `chat.properties`, caso esse arquivo ainda não exista.
2. Abra `chat.properties` no Bloco de Notas e altere a linha `server.rooms`. Salve o arquivo como UTF-8.
3. Encerre o servidor com Ctrl+C, abra `Servidor.cmd` novamente e reconecte os clientes. A lista será carregada pelo servidor; não é necessário editar arquivos nos computadores dos participantes.

Exemplo:

```properties
server.rooms=geral:Geral;faculdade:Faculdade;amigos:Amigos;trabalho:Trabalho
```

Cada item tem o formato **identificador:nome exibido**, e os itens são separados por `;`. No exemplo, você entra em Faculdade pela lista ou com `/join faculdade`.

- Para mudar somente o nome, mantenha o identificador: `faculdade:Minha Turma`. Assim, o histórico continua associado à mesma sala.
- Para adicionar uma sala, acrescente um item, como `;jogos:Jogos`.
- Para retirar uma sala da lista, remova seu item. O histórico permanece no banco e volta a aparecer se o mesmo identificador for adicionado novamente.
- Mantenha uma sala com o identificador `geral`, que é a entrada inicial. Seu nome pode ser alterado, por exemplo `geral:Recepção`.
- São aceitas de 1 a 20 salas. Os identificadores usam até 30 letras minúsculas sem acentos, números, `_` ou `-`, sem espaços. Os nomes exibidos aceitam espaços e acentos, até 40 caracteres.

As mudanças só entram em vigor após reiniciar o servidor. Renomear um identificador cria outra associação de histórico; para preservar as mensagens, altere apenas o nome depois de `:`.

## Funcionalidades disponíveis

- Múltiplos participantes simultâneos, broadcast e ordenação consistente.
- Salas configuráveis abertas automaticamente, com seleção na interface, contagem de participantes e troca sem reconectar.
- Histórico público salvo em `data/chat.mv.db`, recuperado ao conectar novamente.
- Interface em português, bolhas de mensagem, horário, lista de participantes e avisos de entrada e saída.
- Entrada por IP/host e porta; recusa de apelidos duplicados.
- Apelidos com espaços e acentos, como `João da Silva`, até 30 caracteres. Espaços nas pontas são removidos e espaços repetidos viram um só.
- Mensagens privadas: clique em uma pessoa na lista ou selecione seu nome no campo de destinatário.
- Busca no histórico público e recuperação das últimas mensagens.
- Balões de digitação na conversa: nome, avatar e pontos animados para cada pessoa digitando em público na sala atual.
- Prévia local em um balão que cresce e quebra as linhas conforme você digita; fica visível só para você até enviar. Privadas usam a cor roxa de conversa privada; comandos não geram prévia de mensagem.
- Tipografia equilibrada: mensagens em 16 px, campos em 15 px, participantes em 14 px e informações auxiliares de 12 a 13 px, com contraste reforçado.
- Validação de mensagens, limite de 500 caracteres e contador.
- Animações sempre ativas: transições curtas sem sobrepor telas, entrada discreta das mensagens, resposta ao pressionar botões, ilustração flutuante e pontos de digitação em ondas.
- Abertura gradual da janela, troca de salas com transição e fechamento animado com encerramento da conexão.
- Ícones vetoriais em botões, mensagens privadas, menus e avisos; tema verde consistente em seletores, dicas e campos.
- Movimento do ícone ao enviar e indicação de mensagem pública salva no servidor após a confirmação do histórico. Essa indicação não confirma leitura.
- Avatares com iniciais, mensagens privadas em destaque e rolagem suave com atalho para novas mensagens.
- Copiar endereço do servidor e copiar mensagem pelo botão direito.
- Heartbeat e recuperação do histórico por reconexão manual após queda.
- Filas limitadas por cliente para que um destinatário lento não bloqueie o broadcast.

Privadas não ficam no banco e desaparecem ao sair, trocar de sala ou recarregar o histórico. Mensagens públicas ficam disponíveis a qualquer participante que entre na respectiva sala deste servidor. As salas são públicas e não têm senha.

## Comandos

Digite o comando no campo de mensagens e pressione **Enter** ou clique em **Enviar**. Digitar apenas `/` e enviar também abre a ajuda. O botão **Comandos /** mostra a mesma ajuda, com exemplos das salas realmente configuradas no servidor.

| Comando | Resultado |
| --- | --- |
| `/help`, `/ajuda` ou `/` | Mostra a ajuda em um cartão com instruções e exemplos |
| `/salas` ou `/rooms` | Lista salas, contagens e comandos para entrar; também atualiza a lista lateral |
| `/join estudos` ou `/sala Estudos` | Troca pelo identificador ou pelo nome exibido da sala |
| `/users` ou `/usuarios` | Mostra os participantes da sala em uma resposta visível |
| `/history 50` ou `/historico 50` | Carrega as últimas 50 mensagens públicas da sala atual; aceita 1 a 200 |
| `/buscar texto` | Busca literal, sem diferenciar maiúsculas, nas mensagens públicas da sala atual; até 100 resultados |
| `/limpar` | Solicita a limpeza permanente do histórico público da sala atual, sem apagar imediatamente |
| `/limpar confirmar` | Confirma o pedido em até 30 segundos; apaga do banco e remove as mensagens públicas da tela de todos na sala |
| `/limpar cancelar` | Cancela seu pedido de limpeza |
| `/msg Ana Olá` | Envia uma mensagem privada para Ana |
| `/msg "Ana Silva" Olá` | Envia uma mensagem privada para um apelido com espaços |
| `/quit` ou `/sair` | Encerra a conexão |

Os comandos aceitam maiúsculas e espaços adicionais. Para a sala Café, funcionam `/join cafe` e `/join Café`. Para um nome com espaços, use, por exemplo, `/join "Sala da Turma"`. Se duas salas tiverem o mesmo nome, informe o identificador mostrado por `/salas`. Uma troca para a sala em que você já está também recebe confirmação.

As respostas de ajuda e listagem ficam visíveis somente para quem pediu e não são gravadas no histórico. Se a sala não existir, a mensagem de erro sugere comandos reais disponíveis no servidor.

### Limpar o histórico de uma sala

Na sala desejada, envie `/limpar` pelo campo de mensagens. Leia o aviso e, em até **30 segundos**, envie `/limpar confirmar`. Para desistir, envie `/limpar cancelar` ou deixe o prazo expirar. Os aliases `/clear` e `/limparhistorico` funcionam da mesma maneira.

A exclusão é permanente e afeta **todas as mensagens públicas da sala atual**, inclusive as que não estão carregadas na tela. Elas deixam de aparecer no histórico e nas buscas. Todos os participantes dessa sala recebem o aviso com o nome de quem limpou e a quantidade apagada. Outras salas, mensagens privadas da sessão e rascunhos locais são preservados. A conversa continua aberta e aceita novas mensagens normalmente.

Nesta versão não há contas de administrador: **qualquer participante da sala pode solicitar e confirmar a limpeza**. A confirmação é individual, expira e é cancelada ao sair ou trocar de sala. Depois que alguém limpa, os demais pedidos anteriores são invalidados. O comando não aceita o nome de outra sala; entre na sala desejada antes de usá-lo.

Reinicie o servidor e todos os clientes após atualizar, pois esta versão inclui o evento de sincronização da limpeza. A atualização não executa a limpeza automaticamente.

A busca mostra os resultados públicos e as novas mensagens recebidas depois da busca. Use **Histórico** para voltar às últimas mensagens. A interface mantém até 500 itens visuais; as mensagens públicas continuam no banco. Não há confirmação de leitura ou garantia de entrega ao destinatário desconectado.

O balão com o rascunho é uma prévia local: seu conteúdo não é enviado a outros participantes nem salvo no banco antes de pressionar **Enviar**. Para as outras pessoas, aparece somente o nome e o indicador de digitação. Esse indicador some ao enviar, apagar tudo, começar um comando, escolher uma conversa privada, trocar de sala ou ficar alguns segundos sem digitar. Cada participante tem um balão independente. Se o cliente cair sem avisar, o indicador expira automaticamente. As prévias são descartadas ao trocar de sala ou sair; mensagens privadas não anunciam digitação à sala pública.

Depois desta atualização, reinicie o servidor e todos os clientes para usar também o encerramento imediato do indicador de digitação. O fluxo continua na mesma rede, com os mesmos IP e porta.

Ao selecionar uma pessoa na interface, o nome completo é reconhecido automaticamente. Se digitar `/msg` manualmente, coloque apelidos com espaços entre aspas duplas.

## Configuração

Copie `chat.properties.example` para `chat.properties` e ajuste os valores se necessário. O padrão é porta 5050, bind em todas as interfaces, limite de 100 conexões, 50 mensagens iniciais e cinco salas. `server.rooms` define as salas. `server.port` altera a porta usada por todas elas. O IP é o endereço real do computador na rede. Somente o servidor acessa o banco. Para encerrar o servidor, use Ctrl+C na janela dele; fechar um cliente não encerra os demais.

O uso inicial é em rede local confiável. TCP não está criptografado e apelidos não têm autenticação. Redes diferentes exigem uma rota entre os computadores, como uma VPN existente, ou uma futura implantação apropriada. Um IP local não é acessível diretamente pela internet. Se outra máquina não conectar, confira IP, porta, rede e a permissão de entrada do Java no firewall; o projeto não altera essas configurações.

## Desenvolvimento e testes

Abra `pom.xml` na IDE de sua preferência como projeto Maven. Execute `Testes.cmd` ou `mvnw.cmd test`. O script PowerShell usa um repositório Maven local à pasta `.m2/`, ignorada pelo Git.

```powershell
.\scripts\Iniciar.ps1 -Modo Preparar -Recompilar
.\scripts\Iniciar.ps1 -Modo Testes
.\scripts\Iniciar.ps1 -Modo Servidor
.\scripts\Iniciar.ps1 -Modo Cliente
```

Pontos de entrada: `server.Servidor` e `client.Launcher`. Pelo terminal, use `mvnw.cmd compile exec:java` para o servidor e `mvnw.cmd compile javafx:run` para o cliente. Em Linux/macOS, substitua `mvnw.cmd` por `sh mvnw`.

### Bibliotecas visuais

| Biblioteca | Versão | Aplicação no Conexo |
| --- | --- | --- |
| [AtlantaFX](https://github.com/mkpaz/atlantafx) | 2.0.1 | Tema Primer Light, com a paleta verde personalizada |
| [Ikonli](https://github.com/kordamp/ikonli) + Feather | 12.4.0 | Ícones vetoriais nos controles e nas mensagens |
| [AnimateFX](https://github.com/Typhon0/AnimateFX) | 1.3.0 | Fade de entrada, troca de telas e fechamento |

As versões estão fixadas no `pom.xml` e são baixadas automaticamente. AtlantaFX 2.0.1 mantém a compatibilidade com Java 17; AnimateFX 1.3.0 é a versão publicada no Maven Central usada no projeto. As transições de movimento, rolagem, digitação e interação são combinadas com as animações nativas do JavaFX. Ao sair de uma tela, as animações anteriores são interrompidas.

Para ver a atualização, feche os clientes antigos e abra `Cliente.cmd` novamente. Na primeira abertura após atualizar, é necessário ter internet para baixar as novas bibliotecas. O servidor continua usando as mesmas salas, porta e banco. Créditos e licenças estão em [TERCEIROS.md](docs/TERCEIROS.md).

## Arquivos do repositório

O repositório contém fontes (`src/`), testes, recursos gráficos, script SQL, documentação, lançadores, `pom.xml` e Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`). Os arquivos `.gitignore` e `.gitattributes` mantêm os arquivos gerados fora da publicação e padronizam os fins de linha.

Não envie `target/`, `.m2/`, `data/`, `tmp/`, `entrega/`, configurações pessoais da IDE ou `chat.properties` local. Essas pastas e arquivos são criados ao compilar/executar. As bibliotecas são baixadas automaticamente; não precisam acompanhar o código.

Os scripts do Maven Wrapper são do projeto Apache Maven Wrapper e têm licença Apache 2.0, incluída em `.mvn/wrapper/LICENSE.txt`.

## Documentação

- [Análise dos documentos e requisitos](docs/REQUISITOS.md)
- [Arquitetura e concorrência](docs/ARQUITETURA.md)
- [Plano de evolução e apresentação](docs/PLANO.md)
