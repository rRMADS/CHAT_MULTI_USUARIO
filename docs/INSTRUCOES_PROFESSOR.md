# Como baixar e executar o Conexo

## Requisitos

- JDK 17 ou superior instalado; recomendado JDK 21. Um JRE sozinho não compila o projeto.
- Internet na primeira execução para baixar Maven, JavaFX, H2 e JUnit.
- Interface gráfica disponível para abrir o cliente.

Não é necessário instalar Maven, IntelliJ ou um banco de dados separado. Os scripts oficiais `mvnw` e `mvnw.cmd` baixam a versão de Maven definida em `.mvn/wrapper/maven-wrapper.properties`.

## Download do GitHub

1. Abra [o repositório do projeto](https://github.com/rRMADS/CHAT_MULTI_USUARIO).
2. Clique em **Code** e depois em **Download ZIP**.
3. Extraia todo o ZIP para uma pasta local. Não execute os arquivos de dentro do ZIP.
4. Entre na pasta que contém `pom.xml`, `Servidor.cmd`, `Cliente.cmd`, `mvnw.cmd`, `.mvn` e `src`.

## Execução no Windows

1. Dê dois cliques em **Servidor.cmd** e aguarde a preparação terminar. A primeira vez pode levar alguns minutos. Mantenha a janela do servidor aberta.
2. Quando aparecer **Servidor ativo na porta 5050**, dê dois cliques em **Cliente.cmd**.
3. Informe um apelido, IP **127.0.0.1** e porta **5050**. Clique em **Conectar ao chat**.
4. Abra **Cliente.cmd** mais duas vezes e entre com apelidos diferentes para avaliar três clientes simultâneos.
5. Troque mensagens entre as janelas. Feche uma delas e confira que as demais continuam funcionando.
6. Abra um novo cliente para conferir o histórico. Encerre e inicie o servidor novamente para verificar que as mensagens públicas continuam salvas.

Os apelidos aceitam espaços e acentos, por exemplo **Ana Silva** e **João Souza**, até 30 caracteres. Para enviar uma privada, selecione o nome completo na interface ou digite `/msg "Ana Silva" Olá`.

Se o JDK não for detectado automaticamente, configure `JAVA_HOME` para a pasta de instalação do JDK. Por exemplo, no PowerShell, ajustando o caminho à instalação existente:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21'
.\scripts\Iniciar.ps1 -Modo Servidor
```

Para encerrar o servidor, use **Ctrl+C** na janela dele. Fechar um cliente encerra somente aquela conexão.

## Execução pelo terminal

No Windows, a partir da pasta com o `pom.xml`:

```powershell
# Testes
.\mvnw.cmd test

# Servidor
.\mvnw.cmd compile exec:java

# Cliente, em outro terminal; repita para cada participante
.\mvnw.cmd compile javafx:run
```

No Linux/macOS, os comandos equivalentes são:

```sh
sh mvnw test
sh mvnw compile exec:java
sh mvnw compile javafx:run
```

Os comandos de Linux/macOS são caminhos alternativos de execução por Maven; a validação realizada neste projeto foi em Windows. O cliente precisa de uma sessão gráfica. Os testes não abrem janelas.

## Dois computadores na mesma rede

Execute um único servidor. Em cada computador, abra um cliente com o projeto extraído e um JDK instalado. No computador remoto, informe o IPv4 mostrado pelo servidor no lugar de `127.0.0.1`. Use a mesma porta e apelidos distintos.

O IP local só funciona em redes que tenham acesso entre si. Se houver bloqueio, confira a rede e a permissão de entrada da porta no firewall. Os scripts não alteram o firewall.

## Avaliação

- Abra **Testes.cmd** ou execute `mvnw.cmd test` para rodar a suíte JUnit.
- Consulte `docs/ARQUITETURA.md` para o relatório de organização e concorrência.
- O script SQL está em `src/main/resources/schema.sql` e é aplicado automaticamente.
- As mensagens públicas são salvas em `data/chat.mv.db` no computador do servidor. Esse arquivo nasce na execução e não acompanha o código do GitHub.
- Mensagens privadas não entram no histórico e duram apenas a sessão.

## Problemas comuns

| Mensagem ou situação | Como proceder |
| --- | --- |
| JDK não encontrado ou JAVA_HOME inválido | Instalar JDK 17+ e apontar JAVA_HOME para a pasta do JDK |
| Falha no primeiro download | Conferir internet e acesso ao Maven Central; tentar novamente |
| Porta 5050 em uso | Encerrar a outra instância do servidor ou alterar `server.port` em `chat.properties` |
| Cliente não conecta | Iniciar o servidor antes; conferir IP e porta |
| Apelido já está em uso | Escolher um apelido diferente |
| Arquivos/pasta .mvn não encontrados | Extrair novamente todo o ZIP, mantendo a estrutura de pastas |
