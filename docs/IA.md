# Conexo IA: ativação e uso

A integração escolhida é a **Groq**, usando sua API compatível com OpenAI. O modelo padrão é `openai/gpt-oss-20b`, configurável pelo responsável pelo servidor. A escolha prioriza uma integração simples para este chat. Qwen é uma família de modelos; sua API oficial é fornecida pelo Alibaba Cloud Model Studio, com conta, chave e configuração próprias. O site qwen.ai não é uma chave de API para o programa.

## O que funciona sem chave

O chat, os emojis, as salas e os temas funcionam normalmente. O botão **IA** informa que o assistente ainda não foi ativado. Não há respostas simuladas apresentadas como reais.

## Ativar uma vez e abrir normalmente pelo Explorador

1. Crie sua chave em **API Keys** no [console da Groq](https://console.groq.com/keys), se ainda não tiver uma.
2. Na mesma pasta de `Servidor.cmd`, abra **ConfigurarIA.cmd** por duplo clique.
3. Cole a chave completa na solicitação **Chave da Groq** e pressione Enter. O texto fica oculto.
4. Aguarde a mensagem de conclusão. Esse configurador salva a chave com proteção do Windows e coloca `ai.enabled=true` no arquivo `chat.properties`, preservando salas, banco e o modelo que já estiver configurado.
5. Encerre o servidor anterior, caso esteja aberto, e abra **Servidor.cmd** normalmente pelo Explorador de Arquivos.
6. Reconecte os clientes e envie uma pergunta pelo botão **IA**.

Nas próximas vezes, basta abrir `Servidor.cmd`: a chave salva será carregada automaticamente. Isso funciona após fechar o PowerShell e reiniciar o computador, usando a conta do Windows que fez a configuração. A janela do servidor deve permanecer aberta durante o uso do chat, e esse computador precisa de internet para consultar a Groq.

A chave é guardada no arquivo local `.local/ia/groq-key.dpapi`, protegida pela conta do Windows. Esse arquivo e a pasta `.local` são excluídos do GitHub e do ZIP de fontes. Os computadores dos participantes não precisam de chave. Ao instalar em outro computador ou usar outra conta do Windows, execute `ConfigurarIA.cmd` novamente nesse ambiente.

Para trocar a chave, execute `ConfigurarIA.cmd` novamente e reinicie o servidor. Para desativar a IA, coloque `ai.enabled=false` em `chat.properties` e reinicie. A configuração de modelo continua sendo `ai.model`, com padrão `openai/gpt-oss-20b`.

O servidor exibirá **Conexo IA: Groq ativada** quando a configuração estiver habilitada e houver chave. Essa indicação confirma a configuração local; a primeira pergunta verifica se a Groq aceita a chave e o modelo.

## Uso temporário pelo PowerShell

Também é possível fornecer `GROQ_API_KEY` somente para uma sessão. Essa variável tem prioridade sobre a chave salva. Para manter apenas a configuração temporária, defina `ai.enabled=true` em `chat.properties` e execute cada etapa abaixo no mesmo PowerShell:

```powershell
$chaveGroq = Read-Host 'Cole sua chave da Groq' -AsSecureString
```

Quando aparecer a solicitação, cole a chave e pressione Enter. Depois execute:

```powershell
$credencialGroq = New-Object System.Management.Automation.PSCredential('groq', $chaveGroq)
$env:GROQ_API_KEY = $credencialGroq.GetNetworkCredential().Password
.\Servidor.cmd
```

A variável temporária termina com essa sessão. Use **ConfigurarIA.cmd** para que a chave seja lembrada nas próximas execuções pelo Explorador.

## Escolher entre privado e sala

Clique no botão **IA**, ao lado de **Histórico**, e escolha antes de perguntar:

| Opção | Quem vê | Histórico do servidor |
| --- | --- | --- |
| Privado · só você | Apenas quem perguntou recebe a resposta | Pergunta e resposta não são gravadas no banco do chat |
| Na sala · nome da sala | Todos na sala veem a pergunta e a resposta | Ambas ficam salvas nessa sala |

Também é possível digitar no campo de mensagens:

```text
/ia privado Explique o que é um socket em Java
/ia sala Sugira um roteiro para nossa apresentação
```

Cada pergunta é independente. O servidor envia à Groq a pergunta e uma orientação fixa do assistente, **sem encaminhar automaticamente o histórico, os apelidos, os emojis ou a lista de participantes**. O serviço externo recebe e processa o texto enviado; o modo privado define a visibilidade dentro do chat, não significa execução local da IA. Consulte as [informações de dados da Groq](https://console.groq.com/docs/your-data).

As respostas públicas aparecem como **Conexo IA**, com emoji de robô, em um único cartão de até 8.000 caracteres. Parágrafos, títulos, negrito, itálico, listas, citações, tabelas e blocos de código são formatados pelo cliente. Tabelas largas têm rolagem horizontal em janelas estreitas; blocos de código têm fonte própria e opção de cópia. A mesma apresentação é usada no modo privado e no popup. A IA não controla salas nem executa comandos.

O botão **Copiar resposta** copia o texto original completo, incluindo a marcação Markdown, para uso em outros editores. O código pode ser copiado separadamente. HTML é tratado como texto e imagens remotas não são carregadas automaticamente.

Ao instalar esta atualização, reinicie servidor e clientes. O servidor amplia automaticamente a coluna de conteúdo e adiciona o formato da mensagem no banco, preservando o histórico. Mensagens comuns continuam limitadas a uma linha e 500 caracteres. As respostas antigas que já haviam sido divididas e achatadas permanecem como foram salvas: as quebras de linha originais não podem ser reconstruídas com precisão. Faça uma nova pergunta para ver a apresentação corrigida.

## Limites e falhas

- Uma pergunta pendente por conexão e intervalo mínimo de 10 segundos entre pedidos aceitos.
- Duas consultas simultâneas no servidor e até seis consultas aceitas ao todo, incluindo as que aguardam.
- Perguntas com até 450 caracteres; respostas limitadas a 8.000 caracteres e 2.048 tokens de conclusão solicitados à API.
- Prazo de 25 segundos por requisição HTTP, com conexão de até 8 segundos e resposta de no máximo 128 KiB.
- Sair ou trocar de sala cancela a consulta pendente. Se o histórico for limpo durante uma pergunta pública, a resposta atrasada não recria mensagens na sala.
- Erros de autenticação, limite de uso, rede ou formato recebem avisos; a chave e o corpo bruto de erros da API não são exibidos aos clientes.
- O apelido **Conexo IA** fica reservado ao assistente.

A interface permanece utilizável enquanto a IA responde. Se o popup for fechado durante a consulta, a resposta privada ainda aparece em um cartão no chat; a resposta pública aparece na sala.

## Validação desta versão

Os testes usam uma API HTTP local simulada e um assistente controlado, sem chave real e sem consumir a Groq. Verificam autenticação no cabeçalho, conteúdo enviado, resposta Unicode, limites, erros, sigilo entre participantes, persistência pública, concorrência, cancelamento e limpeza do histórico. A chamada ao serviço real ainda precisa ser validada com a chave do responsável pelo servidor.

Referências oficiais: [compatibilidade da API Groq](https://console.groq.com/docs/openai), [modelos](https://console.groq.com/docs/models), [limites](https://console.groq.com/docs/rate-limits) e [API Qwen no Alibaba Cloud](https://www.alibabacloud.com/help/en/model-studio/first-api-call-to-qwen).
