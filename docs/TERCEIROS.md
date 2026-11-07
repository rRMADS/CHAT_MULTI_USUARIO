# Bibliotecas e créditos visuais

O Conexo usa dependências públicas obtidas pelo Maven. Não é necessário copiar repositórios completos nem enviar os arquivos JAR junto com os fontes. Os nomes e avisos de licença dos autores devem acompanhar qualquer futura distribuição que inclua essas bibliotecas.

| Componente | Versão utilizada | Autoria / licença | Origem |
| --- | --- | --- | --- |
| AtlantaFX | 2.0.1 | Michael Paz; MIT | [Projeto](https://github.com/mkpaz/atlantafx/tree/v2.0.1), [licença](https://github.com/mkpaz/atlantafx/blob/v2.0.1/LICENSE) |
| Ikonli JavaFX e Feather Pack | 12.4.0 | Andres Almiray e colaboradores; Apache 2.0 | [Projeto](https://github.com/kordamp/ikonli), [licença](https://github.com/kordamp/ikonli/blob/master/LICENSE) |
| Desenhos Feather | incluídos no Feather Pack | Cole Bemis; MIT | [Projeto](https://github.com/feathericons/feather), [licença](https://github.com/feathericons/feather/blob/main/LICENSE) |
| AnimateFX | 1.3.0 | Loïc Sculier e colaboradores; Apache 2.0 | [Projeto](https://github.com/Typhon0/AnimateFX), [licença](https://github.com/Typhon0/AnimateFX/blob/master/LICENSE) |
| Gson | 2.13.2 | Google e colaboradores; Apache 2.0 | [Projeto](https://github.com/google/gson), [licença](https://github.com/google/gson/blob/main/LICENSE) |
| Fluent Emoji 3D | 18 PNGs no commit `1ffb34c752ecf5d402f04cfb4b392c77f57c54bc` | Microsoft Corporation; MIT | [Projeto](https://github.com/microsoft/fluentui-emoji), [licença incluída](../src/main/resources/client/emojis/LICENSE.txt) |

Os emojis 3D são distribuídos como recursos locais em `src/main/resources/client/emojis`, acompanhados da licença MIT original da Microsoft. A marca Fluent e as demais marcas continuam pertencendo aos respectivos titulares.

Os temas Primer Light e Primer Dark são carregados pela API do AtlantaFX. As cores e os componentes específicos do Conexo estão em `src/main/resources/client/chat.css`. Os ícones são instanciados por `client.Icones`; as transições são coordenadas por `client.Animacoes`.

O ícone animado de envio representa o pedido feito pelo cliente. A indicação **Salva no servidor** aparece na mensagem pública recebida de volta após a persistência pelo servidor. Nenhum desses efeitos representa confirmação de leitura ou presença de uma pessoa fora da sala.
