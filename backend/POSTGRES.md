# PostgreSQL — integração incremental

A migração `migrations/001_purchases.sql` cria as tabelas de pedidos, eventos e direitos de download.

O módulo `src/postgres-orders.js` implementa `PostgresOrderStore` com transações SQL e unicidade de `(user_id,idempotency_key)`. O servidor HTTP aceita repositórios assíncronos via `createAppServer(catalog,{store})`.

**Estado atual:** o PostgreSQL **não está ligado automaticamente** ao processo `npm start`. O driver `pg`, a configuração de `DATABASE_URL`, o executor de migrações e os testes com banco real ainda serão implementados. Sem `ORDER_STORE_FILE`, os endpoints de pedidos retornam indisponibilidade; o arquivo JSON permanece uma opção somente para desenvolvimento.

Não configure credenciais reais no repositório. Para ativar compras reais, ainda são necessários Firebase Authentication, provedores de pagamento, processamento autenticado de notificações, direitos de download e armazenamento privado dos arquivos. Streaming e downloads terão a **mesma qualidade de áudio**.
