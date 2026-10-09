# FREZZAMUSIC — Contrato de compras e direitos de download (proposta v0.22)

**Estado:** implementação parcial em desenvolvimento: cotação, pedidos pendentes, verificação de JWT e armazenamento JSON de protótipo. Nenhum endpoint de pagamento, login, direitos de download ou arquivos privados está publicado em produção.

## Regras comerciais
- Streaming gratuito, sem necessidade de compra ou login. O arquivo para download terá a **mesma qualidade de áudio** da reprodução gratuita; a compra concede apenas o direito de baixar.
- Faixa avulsa: **BRL 1,99**.
- Álbum completo: **BRL 14,99**, independentemente da quantidade de faixas.
- Seleção parcial: **BRL 1,99 × número de faixas**. Uma seleção com todas as faixas é tratada como álbum completo, mesmo que o valor das faixas avulsas fosse inferior.
- O preço é calculado **no servidor**, nunca aceito do aplicativo.

## Identidades
- `catalog_track_id` e `catalog_album_id` são IDs estáveis, sem depender do Google Drive.
- `user_id` autenticado é obrigatório para compra e resgate.
- `order_id` e `payment_provider_event_id` são únicos.
- A confirmação de pagamento ocorre exclusivamente por evento autenticado e validado do provedor (webhook + consulta quando aplicável).

## API — implementada parcialmente / proposta
- `GET /v1/catalog`: catálogo, preços e disponibilidade.
- `POST /v1/checkout/quote`: **implementado em desenvolvimento**; recebe IDs de faixas/álbuns e devolve itens normalizados, valores em centavos, moeda BRL e total. Ainda não fornece `quote_id` ou expiração.
- `POST /v1/checkout/orders`: **implementado em desenvolvimento**; recebe seleção de IDs, token JWT verificado e `Idempotency-Key`; calcula novamente a cotação e cria pedido pendente. Não recebe `quote_id` ainda.
- `GET /v1/orders/{id}`: **implementado em desenvolvimento**; retorna apenas os dados públicos do pedido do usuário autenticado.
- `GET /v1/me/entitlements`: IDs de faixas e álbuns autorizados.
- `POST /v1/downloads/{trackId}/authorize`: exige direito confirmado; retorna URL curta assinada, limitada ao arquivo autorizado.
- `POST /v1/payments/webhook`: valida assinatura, origem, pedido, valor, moeda e status; processa idempotentemente.

## Persistência proposta
- `users(id, created_at)`
- `albums(id, artist_id, title, active)`
- `tracks(id, album_id, title, audio_object_key, active)`
- `orders(id, user_id, status, amount_cents, currency, created_at)`
- `order_items(order_id, item_type, item_id, amount_cents)`
- `payment_events(provider, provider_event_id, order_id, payload_digest, verified_at)`
- `entitlements(user_id, track_id, order_id, granted_at, revoked_at)`

Ao confirmar compra de álbum, conceder direitos individuais para **todas** as faixas integrantes da versão do álbum adquirida; preservar um snapshot dos itens do pedido. Impedir concessão dupla em webhooks repetidos. Estornos e chargebacks devem revogar direitos conforme política publicada.

## Segurança e testes obrigatórios
1. Sem autenticação: nenhum download comprado.
2. Pedido pendente, recusado ou com valor divergente: nenhum direito.
3. Pagamento confirmado, assinatura válida e valor correto: direitos concedidos uma única vez.
4. Usuário A não pode consultar pedidos ou obter downloads de B.
5. URL assinada expira rapidamente; nunca expor `hq_object_key` nem link permanente.
6. Reenvio do mesmo webhook e do mesmo pedido com chave de idempotência não duplica cobrança/direitos.
7. Seleção completa de álbum custa 1499 centavos; faixa custa 199 centavos.
8. Download local não equivale a prova de compra. Não confiar em flags manipuláveis no APK.

## Pré-requisitos para ativação
Escolher hospedagem/API, banco de dados, provedor de pagamento conforme canal de distribuição e armazenamento privado dos arquivos para download; configurar segredos apenas no ambiente do servidor; revisar política da Google Play para bens digitais, LGPD e termos de compra.

**Observação:** o bloqueio no app é apenas uma proteção de interface/cliente. Proteção efetiva requer arquivos de download privados e autorização verificada no servidor.
## Canais de distribuição e autenticação — decisão de arquitetura
- **Três aplicativos:** FREZZAMUSIC, Solasias e theFrezza; uma identidade de usuário compartilhada, com direitos por catálogo/faixa.
- **APK direto:** pagamentos via Mercado Pago; validação de webhook e consulta de pagamento no backend.
- **Google Play:** compras digitais via Google Play Billing, com validação do token de compra na Google Play Developer API, reconhecimento de compra e tratamento de cancelamentos/reembolsos. Avaliar as regras vigentes da Play antes da publicação.
- **Login:** Firebase Authentication (Google/e-mail); o backend deve verificar tokens Firebase via chaves públicas/SDK de administração. O JWT HS256 local atual é **somente uma etapa de desenvolvimento**, não é a integração Firebase.
- **Persistência de produção:** PostgreSQL, com transações, unicidade de eventos de pagamento e chaves de idempotência por usuário.
- **Distribuição:** variantes Play e APK direto com identificadores de canal confiáveis no build, sem escolher provedor apenas por detectar Play Store instalada.
- **Não implementado:** cadastro/login, Firebase, PostgreSQL, Mercado Pago, Play Billing, direitos de download, URL temporária, infraestrutura de produção.
- **Nenhuma compra deve liberar arquivos até confirmação de pagamento validada no servidor.**
