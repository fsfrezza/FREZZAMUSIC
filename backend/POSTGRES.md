# PostgreSQL — integração incremental

A migração `migrations/001_purchases.sql` cria as tabelas de pedidos, eventos e direitos de download.

O módulo `src/postgres-orders.js` implementa `PostgresOrderStore` com transações SQL e unicidade de `(user_id,idempotency_key)`. O servidor HTTP aceita repositórios assíncronos via `createAppServer(catalog,{store})`.

**Estado atual:** o processo `npm start` seleciona PostgreSQL automaticamente quando `DATABASE_URL` está configurada (driver `pg`, instalado com `npm install` no diretório `backend`). A migração SQL **deve ser executada antes** de iniciar o serviço; ainda não existe executor automático de migrações. Os testes atuais do repositório PostgreSQL utilizam um pool simulado: testes de integração com banco real ainda estão pendentes. Sem `DATABASE_URL`, `ORDER_STORE_FILE` habilita o JSON somente para desenvolvimento; sem ambos, os endpoints de pedidos ficam indisponíveis.

Não configure credenciais reais no repositório. Para ativar compras reais, ainda são necessários Firebase Authentication, provedores de pagamento, processamento autenticado de notificações, direitos de download e armazenamento privado dos arquivos. Streaming e downloads terão a **mesma qualidade de áudio**.

## Configuração inicial (ambiente de desenvolvimento)
1. Criar um banco PostgreSQL isolado e um usuário com permissões apropriadas.
2. Executar `psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f migrations/001_purchases.sql` dentro de `backend/` (com conexão segura).
3. Executar `npm install` no diretório `backend/`.
4. Configurar `DATABASE_URL` por variável de ambiente ou gerenciador de segredos, **nunca no Git**.
5. Configurar `ACCESS_TOKEN_SECRET` (JWT de desenvolvimento) e `CATALOG_FILE`; iniciar com `npm start`.

A autenticação Firebase ainda não está implementada. **Não habilitar pagamentos reais** com a autenticação de desenvolvimento.
