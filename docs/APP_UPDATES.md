# Atualizações de APK — implementação inicial

Os três flavors Android consultam `https://raw.githubusercontent.com/fsfrezza/FREZZAMUSIC/main/updates/releases.json` após 30 s. A consulta é opcional; o usuário pode desabilitá-la no alerta.

O JSON possui canais `stable` e `experimental`, com chaves `FREZZAMUSIC`, `SOLASIAS` e `THEFREZZA`. O canal padrão é `stable`. Somente versões com `versionCode` superior ao aplicativo atual e URL HTTPS do GitHub são mostradas.

Exemplo (apenas ilustrativo, **não publicar sem APK assinado e disponível**):
```json
{
  "stable": {
    "FREZZAMUSIC": {"versionCode": 22, "versionName": "0.22.0", "url": "https://github.com/fsfrezza/FREZZAMUSIC/releases/download/v0.22.0/frezzamusic.apk"}
  },
  "experimental": {}
}
```

O manifesto de produção foi criado inicialmente **vazio**. O código não oferece atualização até existir release real, com assinatura compatível e versionCode superior. O protótipo abre URL HTTPS no navegador; não instala APK silenciosamente, nem faz sideload automático, nem confunde sincronização de biblioteca com atualização do aplicativo.

Para usar canal experimental futuramente, construir uma tela de Preferências para configurar `app_updates.channel=experimental`; ainda não há alternador de canal visível. A distribuição ideal será por GitHub Releases (um APK identificado e assinado para cada flavor) e não pelo artefato temporário de Actions.

**Atenção às assinaturas**: os APKs debug gerados no GitHub podem usar certificados distintos dos aplicativos previamente instalados. O Android só atualiza sobre uma instalação anterior quando package name, assinatura e versionCode são compatíveis; não orientar desinstalação sem backup dos dados.

O código precisa passar pelo Actions antes de qualquer afirmação de funcionalidade validada.
