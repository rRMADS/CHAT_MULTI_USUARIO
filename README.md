# Conexo

Chat multiusuário desktop em Java, com sockets TCP, histórico JDBC e interface JavaFX com animações suaves.

## Download e requisitos

No [repositório do GitHub](https://github.com/rRMADS/CHAT_MULTI_USUARIO), use **Code → Download ZIP** e extraia todo o conteúdo antes de executar. Também é possível clonar o repositório.

É necessário ter **JDK 17 ou superior** (recomendado JDK 21) e internet na primeira execução. **Não é necessário instalar Maven ou IntelliJ:** o Maven Wrapper incluído prepara o Maven automaticamente. O H2 também é baixado como biblioteca, sem instalação de banco separada.

As instruções abaixo explicam como executar no Windows, conectar participantes e rodar os testes pelo terminal.

## Como abrir no Windows

1. Abra `Servidor.cmd` no computador que vai hospedar a conversa. Mantenha essa janela aberta.
2. Abra `Cliente.cmd` para cada participante. Para testar no mesmo computador, abra o cliente três vezes.
3. Escolha apelidos diferentes. Use IP `127.0.0.1` e porta `5050` no mesmo computador.
4. Em outro computador da mesma rede, use o endereço IPv4 mostrado pelo servidor e a mesma porta.
5. Envie uma mensagem. Todos os participantes conectados à conversa geral recebem a mensagem.

A primeira preparação baixa as bibliotecas e exige internet. Depois disso, os lançadores usam os arquivos já compilados, recompilando quando as fontes mudam. Não abra vários lançadores durante o primeiro download. O projeto foi testado em Windows com JDK 26.0.2.1 e compilado para Java 17. Cada computador precisa ter o projeto extraído e um JDK; ainda não há instalador independente com Java embutido.

O IP identifica o computador servidor; a porta identifica o serviço de chat. O apelido identifica o participante. O servidor imprime os endereços das interfaces de rede; escolha aquele da rede compartilhada com a outra pessoa. Não use `0.0.0.0` como endereço no cliente.

## Funcionalidades disponíveis

- Múltiplos participantes simultâneos, broadcast e ordenação consistente.
- Histórico público salvo em `data/chat.mv.db`, recuperado ao conectar novamente.
- Interface em português, bolhas de mensagem, horário, lista de participantes e avisos de entrada e saída.
- Entrada por IP/host e porta; recusa de apelidos duplicados.
- Apelidos com espaços e acentos, como `João da Silva`, até 30 caracteres. Espaços nas pontas são removidos e espaços repetidos viram um só.
- Mensagens privadas: clique em uma pessoa na lista ou selecione seu nome no campo de destinatário.
- Busca no histórico público e recuperação das últimas mensagens.
- Indicador de digitação na conversa geral.
- Validação de mensagens, limite de 500 caracteres e contador.
- Animações sempre ativas: transições entre telas, entrada de mensagens, botões responsivos, ilustração flutuante e indicador de digitação com pontos animados.
- Avatares com iniciais, mensagens privadas em destaque e rolagem suave com atalho para novas mensagens.
- Copiar endereço do servidor e copiar mensagem pelo botão direito.
- Heartbeat e recuperação do histórico por reconexão manual após queda.
- Filas limitadas por cliente para que um destinatário lento não bloqueie o broadcast.

Privadas não ficam no banco e desaparecem ao sair ou recarregar o histórico. Mensagens públicas ficam disponíveis a qualquer participante que entre neste servidor. Há um único chat geral por servidor nesta versão.

## Comandos

| Comando | Resultado |
| --- | --- |
| `/help` | Lista os comandos |
| `/users` | Atualiza os participantes online |
| `/history 50` | Carrega as últimas 50 mensagens públicas; aceita 1 a 200 |
| `/buscar texto` | Busca literal, sem diferenciar maiúsculas, nas mensagens públicas; até 100 resultados |
| `/msg Ana Olá` | Envia uma mensagem privada para Ana |
| `/msg "Ana Silva" Olá` | Envia uma mensagem privada para um apelido com espaços |
| `/quit` | Encerra a conexão |

A busca mostra os resultados públicos e as novas mensagens recebidas depois da busca. Use **Últimas mensagens** para voltar. A interface mantém até 500 itens visuais; as mensagens públicas continuam no banco. Não há confirmação de leitura ou garantia de entrega ao destinatário desconectado.

Ao selecionar uma pessoa na interface, o nome completo é reconhecido automaticamente. Se digitar `/msg` manualmente, coloque apelidos com espaços entre aspas duplas.

## Configuração

Copie `chat.properties.example` para `chat.properties` e ajuste os valores se necessário. O padrão é porta 5050, bind em todas as interfaces, limite de 100 conexões e 50 mensagens iniciais. Somente o servidor acessa o banco. Para encerrar o servidor, use Ctrl+C na janela dele; fechar um cliente não encerra os demais.

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

## Arquivos do repositório

O repositório contém fontes (`src/`), testes, recursos gráficos, script SQL, documentação, lançadores, `pom.xml` e Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`). Os arquivos `.gitignore` e `.gitattributes` mantêm os arquivos gerados fora da publicação e padronizam os fins de linha.

Não envie `target/`, `.m2/`, `data/`, `tmp/`, `entrega/`, configurações pessoais da IDE ou `chat.properties` local. Essas pastas e arquivos são criados ao compilar/executar. As bibliotecas são baixadas automaticamente; não precisam acompanhar o código.

Os scripts do Maven Wrapper são do projeto Apache Maven Wrapper e têm licença Apache 2.0, incluída em `.mvn/wrapper/LICENSE.txt`.

## Documentação

- [Análise dos documentos e requisitos](docs/REQUISITOS.md)
- [Arquitetura e concorrência](docs/ARQUITETURA.md)
- [Plano de evolução e apresentação](docs/PLANO.md)
