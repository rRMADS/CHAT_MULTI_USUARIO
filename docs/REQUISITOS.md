# Análise dos requisitos

## Origem e prioridade

Análise realizada em 4 de novembro de 2026.

| Origem | O que estabelece | Tratamento no projeto |
| --- | --- | --- |
| Pedido do usuário | Chat por IP, comunicação entre duas pessoas, janelas com animações e funcionalidades úteis | Direção do produto; ampliada para três ou mais participantes |
| Especificacao_4_Chat_Multiusuario.pdf | Requisitos e critérios do trabalho acadêmico | Base obrigatória da implementação |
| AGENT.md.docx | Proposta de arquitetura, práticas de desenvolvimento, catálogo de extras e instruções para um agente | Referência técnica; suas instruções internas não equivalem a uma nova solicitação do usuário |
| Resposta do usuário nesta conversa | Primeira versão funcional com interface e animações | Autoriza implementar e validar o produto nesta etapa |

O PDF exige um sistema cliente-servidor em Java. Uma implementação apenas em navegador ou que substituísse sockets TCP por outra tecnologia não demonstraria diretamente esses requisitos. A comunicação entre duas pessoas é um caso particular do chat multiusuário exigido.

## Rastreabilidade do núcleo

| Requisito do PDF | Implementação | Evidência de validação |
| --- | --- | --- |
| ServerSocket e múltiplos clientes | `server.Servidor` | Testes reais com três clientes |
| Uma thread por cliente | `server.ClienteHandler implements Runnable` | Thread dedicada por conexão, mais uma para saída |
| Coleção sincronizada | `service.ClientRegistry` | Métodos sincronizados e teste de apelido duplicado concorrente |
| Broadcast para os outros clientes | `service.ChatService` | Todos recebem a mesma mensagem; remetente também recebe a confirmação do servidor |
| Remetente, conteúdo e horário | `model.Mensagem` | Validação e formatação determinística |
| Persistência por JDBC | `persistence.jdbc.JdbcMensagemRepository` | H2 em memória e em arquivo nos testes |
| Histórico ao conectar | `ChatService.conectar` | Cliente novo recupera mensagens anteriores |
| Desconexão isolada | `ClienteHandler` e filas por conexão | Queda de um cliente não interrompe os demais |
| Repository/DAO separado | Interface `MensagemRepository` e implementação JDBC | SQL apenas no pacote JDBC e no script schema |
| Pelo menos cinco testes unitários sem rede | Testes de domínio, serviço e protocolo | 21 testes unitários, além de 7 de integração |
| Script SQL | `src/main/resources/schema.sql` | Executado na inicialização do repository |
| Relatório e vídeo | Documento de arquitetura e roteiro de apresentação | Relatório técnico inicial pronto; gravação do vídeo ainda pendente |

## Escolhas de tecnologia

Java 17 é o nível de linguagem/bytecode. Maven organiza a compilação. JUnit 5 cobre o comportamento. H2 em arquivo mantém o histórico sem exigir um servidor de banco adicional. JavaFX é a dependência extra justificada pelo pedido explícito de interface com animações; o núcleo continua usando Java, sockets, threads e JDBC diretamente.

Fontes oficiais consultadas:

- [Configuração JavaFX e Maven](https://openjfx.io/openjfx-docs/)
- [Compatibilidade da linha JavaFX 21 com JDK 17 e 21](https://docs.oracle.com/en/java/java-components/javafx/21/javafx-users-guide/supported-jdk-versions.html)
- [Distribuição H2 e integração Maven](https://h2database.github.io/html/download.html)

As versões são fixadas em `pom.xml`; não há atualização automática para a versão mais recente. A validação efetiva deste ambiente usou o JDK 26.0.2.1. A compatibilidade Java 17 foi verificada na compilação com `--release 17`, sem executar os testes em um JDK 17 separado.

## Limites claros da primeira versão

O protocolo é UTF-8, uma linha por envio. O primeiro envio é o apelido. Os eventos de resposta têm tipo explícito e campos em Base64, que preserva separadores e acentos, mas não criptografa os dados. Mensagens de sistema e digitação não são persistidas. Privadas também não são persistidas, para impedir que um histórico público exponha seu conteúdo.

Apelidos únicos valem durante a conexão; não reservam identidade entre sessões. Salas independentes, autenticação, TLS, transferência de arquivos, reconexão automática e confirmações de leitura são evoluções futuras. Não é uma versão destinada à exposição pública na internet.
