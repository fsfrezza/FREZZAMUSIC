# Entrega privada de downloads — contrato do assinador

O backend gera uma URL temporária **somente após** verificar no PostgreSQL o direito ativo de uma compra paga. O assinador é um adaptador para um gateway/CDN **controlado pelo projeto**; não é uma URL pré-assinada nativa de S3, Google Cloud Storage ou Google Drive.

## Configuração

- `PRIVATE_DOWNLOAD_BASE_URL`: origem HTTPS e prefixo público do gateway (por exemplo, `https://downloads.example.com/private`).
- `PRIVATE_DOWNLOAD_SIGNING_KEY`: segredo compartilhado de pelo menos 32 bytes, guardado no gerenciador de segredos.
- `PRIVATE_DOWNLOAD_OBJECTS_FILE`: caminho de um JSON privado no servidor, com IDs de faixas mapeados para chaves de objetos, por exemplo `{"track-a":"album/track-a.mp3"}`. Não incluir esse mapa no APK.

O gateway **deve** implementar validação de HMAC-SHA256 antes de servir qualquer arquivo, validar `expires`, `nonce` e o caminho do objeto e rejeitar requisições expiradas, malformadas ou com assinatura inválida. A assinatura cobre a string UTF-8 `GET\\n<objectKeyPath>\\n<expires>\\n<nonce>`, em que `objectKeyPath` é `/album/track-a.mp3` (sem o prefixo da URL-base). O HMAC é representado em hexadecimal minúsculo. A URL contém os parâmetros `expires`, `nonce` e `signature`. A validade padrão é 120 segundos (limite de 30 a 300 segundos). Recomenda-se não registrar URLs assinadas em logs.

**Limitação importante:** o gateway de entrega e os objetos privados **não foram provisionados**. O backend não serve os bytes diretamente. Sem configuração completa, o endpoint retorna 503 após confirmar o direito; não disponibiliza o áudio. Não aponte o assinador para um CDN público que ignore a assinatura.

O áudio disponível para download não terá qualidade superior à reprodução gratuita.
