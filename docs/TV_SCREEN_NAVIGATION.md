# Navegação Tocar / Letras na TV (protótipo)

Branch: `hotfix/restore-v023-base`, criada a partir de `development/v0.23-links`. Versão do Android: 0.23.7-wifi-tv, versionCode 42.

## Implementação
- Mantém a interface original de quatro abas (Tocar, Letras, Fila, Áudio) e os ícones e controles do app v0.23.x.
- Um Cast receiver com UI para `Tocar` e `Letras` está em `cast-receiver/index.html`. Exibe título/artista, tempo de música e letra LRC destacada em sincronia com o relógio do receiver.
- O remetente envia a escolha da aba e os dados de LRC através do namespace `urn:x-cast:com.frezzamusic.lyrics`. Quando `TV acompanha as abas` está desligado, a navegação no celular não altera a aba exibida na TV.
- Fila/Áudio não possuem telas dedicadas no receptor: a TV mantém a tela anterior.
- Ainda não há streaming de arquivos locais nem emparelhamento DLNA. Faixas online dependem de URL HTTP(S) acessível pela TV.

## Ativação necessária para letras
A compilação atual usa o Default Media Receiver (`CC1AD845`) até que se configure um **Custom Web Receiver**. O receptor padrão **ignora as mensagens de interface personalizada**.
1. Hospedar `cast-receiver/index.html` em servidor HTTPS acessível à TV, incluindo o script Google CAF.
2. Registrar no [Google Cast SDK Developer Console](https://cast.google.com/publish/) e configurar os dispositivos de teste.
3. Configurar a propriedade Gradle `frezzamusicCastReceiverId=SEU_APP_ID` (por exemplo, em `~/.gradle/gradle.properties`, fora do Git) e recompilar os três sabores. Sem essa propriedade, permanece o receptor padrão `CC1AD845`, que não suporta telas customizadas.
4. Testar em TV real se o receptor recebe `urn:x-cast:com.frezzamusic.lyrics`, se a música toca e se LRC acompanha seek/pause.

O protótipo não foi validado em TV e não deve ser descrito como recurso totalmente funcional. É proibido substituir APKs em produção sem aprovação de compilação, QA das funcionalidades existentes, checagem de ícones e assinatura Android compatível.

## Navegação manual
Na tela de reprodução, desligue `TV acompanha as abas` para revelar `TV: Tocar` e `TV: Letras`. Essas ações enviam instruções ao receptor personalizado enquanto a navegação no celular permanece independente. A API de custom messages não funciona no receptor padrão.
