# FREZZAMUSIC — Contrato de compras e direitos de download (proposta v0.22)

**Estado:** especificação técnica, ainda não implementada. Nenhum endpoint está publicado.

## Regras comerciais
- Streaming gratuito, sem necessidade de compra.
- Faixa avulsa: **BRL 1,99**.
- Álbum completo: **BRL 14,99**, independentemente da quantidade de faixas.
- Seleção parcial: **BRL 1,99 × número de faixas**. Uma seleção com todas as faixas é tratada como álbum completo, mesmo que o valor das faixas avulsas fosse inferior.
- O preço é calculado **no servidor**, nunca aceito do aplicativo.

## Identidades
- `catalog_track_id` e `catalog_album_id` são IDs estáveis, sem depender do Google Drive.
- `user_id` autenticado é obrigatório para compra e resgate.
- `order_id` e `payment_provider_event_id` são únicos.
- A confirmação de pagamento ocorre exclusivamente por evento autenticado e validado do provedor (webhook + consulta quando aplicável).

## API proposta
- `GET /v1/catalog`: catálogo, preços e disponibilidade.
- `POST /v1/checkout/quote`: recebe IDs de faixas/álbuns; devolve itens normalizados, valores em centavos, moeda BRL, total e expiração.
- `POST /v1/checkout/orders`: recebe `quote_id` e chave de idempotência; cria pedido pendente.
- `GET /v1/orders/{id}`: estado do pedido do usuário autenticado.
- `GET /v1/me/entitlements`: IDs de faixas e álbuns autorizados.
- `POST /v1/downloads/{trackId}/authorize`: exige direito confirmado; retorna URL curta assinada, limitada ao arquivo HQ autorizado.
- `POST /v1/payments/webhook`: valida assinatura, origem, pedido, valor, moeda e status; processa idempotentemente.

## Persistência proposta
- `users(id, created_at)`
- `albums(id, artist_id, title, active)`
- `tracks(id, album_id, title, hq_object_key, active)`
- `orders(id, user_id, status, amount_cents, currency, created_at)`
- `order_items(order_id, item_type, item_id, amount_cents)`
- `payment_events(provider, provider_event_id, order_id, payload_digest, verified_at)`
- `entitlements(user_id, track_id, order_id, granted_at, revoked_at)`

Ao confirmar compra de álbum, conceder direitos individuais para **todas** as faixas integrantes da versão do álbum adquirida; preservar um snapshot dos itens do pedido. Impedir concessão dupla em webhooks repetidos. Estornos e chargebacks devem revogar direitos conforme política publicada.

## Segurança e testes obrigatórios
1. Sem autenticação: nenhum download HQ.
2. Pedido pendente, recusado ou com valor divergente: nenhum direito.
3. Pagamento confirmado, assinatura válida e valor correto: direitos concedidos uma única vez.
4. Usuário A não pode consultar pedidos ou obter downloads de B.
5. URL assinada expira rapidamente; nunca expor `hq_object_key` nem link permanente.
6. Reenvio do mesmo webhook e do mesmo pedido com chave de idempotência não duplica cobrança/direitos.
7. Seleção completa de álbum custa 1499 centavos; faixa custa 199 centavos.
8. Download local não equivale a prova de compra. Não confiar em flags manipuláveis no APK.

## Pré-requisitos para ativação
Escolher hospedagem/API, banco de dados, provedor de pagamento conforme canal de distribuição e armazenamento privado de HQ; configurar segredos apenas no ambiente do servidor; revisar política da Google Play para bens digitais, LGPD e termos de compra.

**Observação:** o bloqueio no app é apenas uma proteção de interface/cliente. Proteção efetiva requer mídia HQ privada e autorização verificada no servidor.