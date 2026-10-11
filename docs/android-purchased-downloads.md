# Android — cliente de compras e downloads

`PurchasedDownloadsApi` fornece duas operações autenticadas para os três aplicativos Android:

- `purchasedTrackIds()`: consulta `GET /v1/me/entitlements` e devolve as faixas compradas.
- `authorizeDownload(trackId)`: solicita `POST /v1/downloads/{trackId}/authorize` e valida HTTPS e a expiração da URL recebida.

A classe recebe a URL HTTPS do backend e uma função suspensa `tokenProvider` que deverá obter um **Firebase ID token válido**. Ela não cria contas, não inventa tokens e não libera downloads por conta própria.

**Pendente:** conectar Firebase Authentication ao Android, configurar a URL da API, implementar a interface Minhas Compras, baixar o arquivo com segurança para o armazenamento interno e tratar revogação. A classe foi adicionada como base de integração; ainda não está conectada à interface de compras nem ao repositório offline. Nenhuma cobrança ou download pago foi habilitado.

## Armazenamento privado de downloads comprados
O módulo `PurchasedAudioStorage` consulta os direitos no servidor, solicita URL assinada para a faixa e transfere o arquivo para o diretório interno do aplicativo. Usa arquivo temporário e substituição atômica após concluir a transferência. O nome do arquivo é derivado do SHA-256 do ID da faixa para impedir travessia de diretórios.

**Ainda pendente:** vincular a classe às telas e ao player, autenticar o usuário com Firebase no Android, testar em dispositivos e tratar revogação/remoção de arquivos já baixados. O armazenamento interno evita exposição direta, mas não equivale a DRM; também é necessário limitar tamanho dos arquivos e reforçar a verificação de conteúdo antes da liberação em produção.

## Limites de transferência
O repositório de áudio comprado agora desabilita redirecionamentos HTTP(S) durante a transferência e impõe limite de 200 MB por arquivo, inclusive quando o servidor não informa Content-Length. Respostas incompletas ou acima do limite não são marcadas como baixadas. Antes da publicação, revisar limites conforme tamanho real do catálogo e incluir testes instrumentados de rede.

## Interface Minhas Compras
A tela `MyPurchasesScreen` está implementada e acessível por **Mais → Minhas Compras**. Com API autenticada, consulta direitos, lista faixas do catálogo e oferece baixar/remover; sem autenticação e URL de API configuradas, mostra um aviso explícito e não oferece download. Atualmente a navegação fornece `api=null` e `storage=null` de propósito, até que o login Firebase e o endereço HTTPS real do backend sejam configurados. A interface ainda não foi conectada ao player e não realiza pagamentos.

O texto da seção Mais foi corrigido para deixar claro que baixar não significa qualidade de áudio superior ao streaming.

## Firebase Authentication Android — configuração por aplicativo

Foi incluída a classe `PurchaseIdentity`, que inicializa o Firebase Authentication com opções explícitas e fornece tokens de identidade para a API. A tela **Minhas Compras** oferece entrar e criar conta por e-mail e senha quando o serviço está configurado. O streaming gratuito continua sem exigir login.

Forneça as propriedades Gradle `frezzamusicFirebaseApiKey`, `frezzamusicFirebaseAppId`, `frezzamusicFirebaseProjectId` e `frezzamusicPurchaseApiUrl` (HTTPS). Use o **mesmo projeto Firebase** nos três aplicativos e registre os três package names no console Firebase. Cada pacote tem um Firebase App ID próprio; portanto, a configuração atual em `defaultConfig` precisa ser especializada por flavor antes de distribuir três APKs configurados. Não inclua credenciais administrativas no APK. Configure o projeto Firebase, ative autenticação por e-mail e senha, e configure o backend com o mesmo Firebase Project ID.

A autenticação Google ainda não foi integrada. Sem as propriedades, a tela permanece indisponível e não inventa uma identidade. A configuração Gradle e o fluxo Android ainda exigem validação de build e testes em dispositivos.
