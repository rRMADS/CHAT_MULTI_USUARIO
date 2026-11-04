# Bibliotecas e créditos visuais

O Conexo usa dependências públicas obtidas pelo Maven. Não é necessário copiar repositórios completos nem enviar os arquivos JAR junto com os fontes. Os nomes e avisos de licença dos autores devem acompanhar qualquer futura distribuição que inclua essas bibliotecas.

| Componente | Versão utilizada | Autoria / licença | Origem |
| --- | --- | --- | --- |
| AtlantaFX | 2.0.1 | Michael Paz; MIT | [Projeto](https://github.com/mkpaz/atlantafx/tree/v2.0.1), [licença](https://github.com/mkpaz/atlantafx/blob/v2.0.1/LICENSE) |
| Ikonli JavaFX e Feather Pack | 12.4.0 | Andres Almiray e colaboradores; Apache 2.0 | [Projeto](https://github.com/kordamp/ikonli), [licença](https://github.com/kordamp/ikonli/blob/master/LICENSE) |
| Desenhos Feather | incluídos no Feather Pack | Cole Bemis; MIT | [Projeto](https://github.com/feathericons/feather), [licença](https://github.com/feathericons/feather/blob/main/LICENSE) |
| AnimateFX | 1.3.0 | Loïc Sculier e colaboradores; Apache 2.0 | [Projeto](https://github.com/Typhon0/AnimateFX), [licença](https://github.com/Typhon0/AnimateFX/blob/master/LICENSE) |

O tema Primer Light é carregado pela API do AtlantaFX. As cores e os componentes específicos do Conexo estão em `src/main/resources/client/chat.css`. Os ícones são instanciados por `client.Icones`; as transições são coordenadas por `client.Animacoes`.

O ícone animado de envio representa o pedido feito pelo cliente. A indicação **Salva no servidor** aparece na mensagem pública recebida de volta após a persistência pelo servidor. Nenhum desses efeitos representa confirmação de leitura ou presença de uma pessoa fora da sala.
