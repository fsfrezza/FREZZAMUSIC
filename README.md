# FREZZAMUSIC Android v0.10
Projeto Android nativo em Kotlin + Jetpack Compose + Android Media3.

## Implementado nesta árvore
- seleção persistente de múltiplas pastas com Storage Access Framework;
- varredura recursiva e metadados: título, artista, álbum, faixa, duração e gênero;
- modelo unificado LOCAL / ONLINE_FREE / ONLINE_HQ;
- PlaybackService + MediaSession + ExoPlayer;
- MediaController para reprodução, fila, play/pause, anterior/próxima, seek, shuffle e repeat;
- mini-player e componente de player completo;
- biblioteca por músicas/artistas/álbuns/gêneros/pastas e busca;
- favoritos, histórico/contador de reproduções, playlists e filas persistentes;
- catálogo online hierárquico artista → álbum → faixa;
- resolver separado para streaming: HQ é recusado sem autorização de backend;
- estrutura de configurações para tema, velocidade e crossfade.

## Catálogo remoto
A árvore contém os IDs reais já catalogados do Drive para desenvolvimento. O catálogo continua sendo ampliável em `RemoteCatalog.kt`. Google Drive compartilhado não é a solução de proteção para HQ comercial.

## Importante
Este ambiente não contém Android SDK/Gradle operacional e, portanto, esta versão não foi compilada aqui. Abra no Android Studio, sincronize o Gradle e compile. Erros de API/versão que apareçam na primeira compilação devem ser corrigidos com base no log real do Android Studio.

## Recursos preparados, mas que exigem validação/dispositivo/backend
- HQ pago/autenticação/entitlement;
- equalizador específico do dispositivo;
- Android Auto e widgets;
- edição física de tags via SAF;
- letras LRC completas e busca de letras;
- crossfade real (Media3 não fornece um toggle universal; requer estratégia de áudio/player);
- backup/importação exportável;
- Play Billing/Play Integrity.

Nenhum arquivo HQ, segredo ou credencial deve ser embutido no APK.

## Compilação sem Android Studio — GitHub Actions

Esta versão inclui `.github/workflows/build-android.yml`.

1. Crie um repositório no GitHub.
2. Envie **o conteúdo desta pasta FREZZAMUSIC para a raiz do repositório** (não envie apenas o ZIP fechado).
3. Abra a aba **Actions**.
4. Selecione **Build FREZZAMUSIC Android**.
5. Clique em **Run workflow**.
6. Quando o job terminar com sucesso, abra a execução e baixe o artefato **FREZZAMUSIC-debug-apk**.
7. Extraia o ZIP do artefato; dentro estará `app-debug.apk`.

O workflow usa Java 17 e Gradle 8.9, adequados ao Android Gradle Plugin 8.7.3 usado neste projeto. O Android SDK necessário é disponibilizado pelo runner hospedado do GitHub.

Se a compilação falhar, abra o passo **Build debug APK**, copie o trecho do erro (principalmente a primeira ocorrência de `FAILURE`, `e:` ou `Caused by`) e envie-o para correção do projeto.
