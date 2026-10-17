# Atualizações dos APKs

Os três aplicativos verificam `https://fsfrezza.github.io/FREZZAMUSIC/updates.json`, comparando `versionCode` com a versão instalada. Isso ocorre junto à verificação automática do catálogo (30 segundos em primeiro plano, se habilitada), ao tocar em Atualizar biblioteca, e pelo botão Verificar atualização do app nas configurações. O aviso permite Atualizar ou Depois; o mesmo build não é oferecido repetidamente na mesma sessão. A confirmação abre o arquivo correspondente no Drive. O usuário baixa/abre o APK e confirma no instalador do Android. Não há instalação silenciosa ou credenciais do Drive dentro do aplicativo.

Falhas da verificação não impedem carregar a biblioteca. Somente versões estritamente superiores e o ID fixo de download do próprio pacote são aceitos. Datas de modificação do Drive não determinam versão: substituir um arquivo sem aumentar seu versionCode e sem publicar o manifesto não anuncia atualização.

## Publicar uma versão

1. Aumentar versionCode/versionName em app/build.gradle.kts; manter os três applicationId existentes.
2. Executar testes, gerar os três APKs release e conferir os manifests/R8.
3. Assinar PRIVADAMENTE com a chave de produção existente. Nunca enviar chave, senha ou signing.json ao repositório ou à pasta pública do Drive.
4. Verificar os APKs assinados e comparar o SHA-256 do certificado com a versão anterior: `98320fc332d4f1e8214733a21cdb8c25c86adf98a3a804e35faefe3472f0a64b`. Manter o mesmo pacote, aumentar o versionCode e não reduzir requisitos de assinatura. Isso permite atualizar diretamente a produção v0.23.3 em diante, preservando os dados.
5. Substituir os bytes dos três arquivos do Drive, preservando IDs e links. Verificar o resultado dos uploads.
6. SOMENTE DEPOIS publicar os versionCode/versionName correspondentes em web/updates.json e aguardar o Pages. Não anunciar builds que ainda não estejam disponíveis para baixar.
7. Atualizar o ZIP de fontes, sem incluir assinatura privada.

Versões de teste assinadas com outra chave não podem ser convertidas em atualizações compatíveis. Podem exigir uma migração única com reinstalação. Não apagar dados nem mudar identificadores para contornar a verificação do Android.
