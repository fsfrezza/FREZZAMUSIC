# Recuperação de base após regressão da implementação Wi-Fi

A branch `feature/wifi-google-cast` foi criada a partir da `main` antiga (`versionCode 20`). Portanto, os APKs `versionCode 21` de Wi-Fi **não preservam** a base de trabalho atual, `development/v0.23-links` (`versionCode 40` / `versionName 0.23.5`). Não os distribuir como atualização.

Esta branch foi criada **a partir da base v0.23.5**, para que a implementação Wi-Fi seja refeita por adição, sem substituir `MainActivity.kt`, `FullPlayer.kt`, catálogos, ícones, sincronização ou atualizações já presentes.

Antes de publicar APKs de substituição:
1. Confirmar todas as funcionalidades atuais e ícones específicos por flavor;
2. Reaplicar apenas o módulo Cast/receiver às APIs existentes;
3. Aumentar versionCode para mais de 40, com assinatura Android compatível;
4. Compilar as três variantes, testar biblioteca/atualização/ícone e Cast;
5. Atualizar os APKs existentes no Drive **somente após** essa validação.
