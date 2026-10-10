# Android — cliente de compras e downloads

`PurchasedDownloadsApi` fornece duas operações autenticadas para os três aplicativos Android:

- `purchasedTrackIds()`: consulta `GET /v1/me/entitlements` e devolve as faixas compradas.
- `authorizeDownload(trackId)`: solicita `POST /v1/downloads/{trackId}/authorize` e valida HTTPS e a expiração da URL recebida.

A classe recebe a URL HTTPS do backend e uma função suspensa `tokenProvider` que deverá obter um **Firebase ID token válido**. Ela não cria contas, não inventa tokens e não libera downloads por conta própria.

**Pendente:** conectar Firebase Authentication ao Android, configurar a URL da API, implementar a interface Minhas Compras, baixar o arquivo com segurança para o armazenamento interno e tratar revogação. A classe foi adicionada como base de integração; ainda não está conectada à interface de compras nem ao repositório offline. Nenhuma cobrança ou download pago foi habilitado.

## Armazenamento privado de downloads comprados
O módulo `PurchasedAudioStorage` consulta os direitos no servidor, solicita URL assinada para a faixa e transfere o arquivo para o diretório interno do aplicativo. Usa arquivo temporário e substituição atômica após concluir a transferência. O nome do arquivo é derivado do SHA-256 do ID da faixa para impedir travessia de diretórios.

**Ainda pendente:** vincular a classe às telas e ao player, autenticar o usuário com Firebase no Android, testar em dispositivos e tratar revogação/remoção de arquivos já baixados. O armazenamento interno evita exposição direta, mas não equivale a DRM; também é necessário limitar tamanho dos arquivos e reforçar a verificação de conteúdo antes da liberação em produção.
