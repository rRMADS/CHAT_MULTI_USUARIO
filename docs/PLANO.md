# Plano de evolução e apresentação

## Primeira versão

O primeiro marco entrega o núcleo acadêmico com interface gráfica animada e recursos de uso cotidiano. O próximo ciclo deve partir do código validado, preservando os testes e a separação de responsabilidades.

| Etapa | Situação | Critério de conclusão |
| --- | --- | --- |
| Análise dos documentos | Concluída | Requisitos separados das sugestões e instruções do DOCX |
| Domínio e histórico JDBC | Implementada | Validação, script SQL e reabertura do histórico nos testes |
| TCP e concorrência | Implementada | Três clientes, ordem consistente e queda isolada |
| Interface e animações | Implementada | Login por IP/porta, conversa, participantes e transições |
| Comandos e recursos úteis | Implementada | Privadas em sessão, busca, digitação e cópia |
| Várias conversas no mesmo servidor | Implementada | Salas configuráveis, troca pela interface e histórico isolado |
| Demonstração em dois computadores | Pendente | Conectar dois dispositivos reais na mesma rede |
| Vídeo da entrega | Pendente | Gravar três clientes simultâneos e recuperação de histórico |

## Próximas melhorias sugeridas

| Prioridade | Melhoria | Motivo e limite |
| --- | --- | --- |
| Alta | Instalador Windows com runtime incluído | Permitir abrir sem instalar JDK ou Maven |
| Alta | Reconexão automática com retomada por ID | Recuperar lacunas sem repetir mensagens; não reenviar automaticamente conteúdo sem confirmação |
| Alta | Controle de flood | Aplicar limite por sessão com relógio testável e feedback claro |
| Média | Gestão de salas em execução | Permitir criar e renomear salas sem reiniciar o servidor; a configuração por arquivo já está disponível |
| Média | Contas e TLS | Necessários antes de exposição pública; só então histórico privado persistido por identidade autenticada |
| Média | Testes automatizados da interface | Cobrir conexão, acessibilidade, teclado, busca e mudanças de destinatário |
| Baixa | Entrega e leitura confirmadas | Exige protocolo de ACK e apresentação honesta dos estados |
| Baixa | Arquivos e reações | Adicionar limites, isolamento e persistência próprios depois de estabilizar o núcleo |

Essas melhorias são propostas, não funcionalidades já entregues. Um identificador de sala complementaria o IP e a porta do servidor, sem substituí-los.

## Roteiro de apresentação

1. Iniciar o servidor e mostrar o IP/porta e o banco em arquivo.
2. Abrir três clientes com apelidos distintos e trocar mensagens com acentos.
3. Mostrar os avisos, a lista de participantes e uma mensagem privada invisível ao terceiro cliente.
4. Fechar um cliente abruptamente e mostrar os outros conversando.
5. Conectar outro cliente e mostrar as mensagens públicas anteriores.
6. Buscar texto no histórico e voltar às últimas mensagens.
7. Desligar e religar o servidor para demonstrar persistência em disco.
8. Mostrar `ClienteHandler`, o registry sincronizado, a camada JDBC e os testes.

O documento de arquitetura serve como relatório técnico inicial. Antes da entrega, incluir identificação dos integrantes, eventuais orientações do professor e o link do vídeo. O cronograma do DOCX chega a 30/11/2026; a data oficial de entrega precisa ser confirmada com a turma, pois o PDF não fixa uma data.
