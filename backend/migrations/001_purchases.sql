-- FREZZAMUSIC purchases v1: PostgreSQL schema (migration draft; not yet wired to runtime).
-- Run through a migration runner with a dedicated database role.
BEGIN;
CREATE TABLE IF NOT EXISTS app_users (
  id TEXT PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS purchase_orders (
  id UUID PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES app_users(id),
  idempotency_key TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','paid','cancelled','refunded')),
  currency CHAR(3) NOT NULL DEFAULT 'BRL' CHECK (currency='BRL'),
  amount_cents BIGINT NOT NULL CHECK (amount_cents>0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(user_id,idempotency_key)
);
CREATE INDEX IF NOT EXISTS purchase_orders_user_created_idx ON purchase_orders(user_id,created_at DESC);
CREATE TABLE IF NOT EXISTS purchase_order_items (
  order_id UUID NOT NULL REFERENCES purchase_orders(id),
  item_type TEXT NOT NULL CHECK(item_type IN ('album','track')),
  item_id TEXT NOT NULL,
  amount_cents BIGINT NOT NULL CHECK(amount_cents>0),
  track_ids JSONB NOT NULL CHECK(jsonb_typeof(track_ids)='array'),
  PRIMARY KEY(order_id,item_type,item_id)
);
CREATE TABLE IF NOT EXISTS payment_events (
  provider TEXT NOT NULL CHECK(provider IN ('google_play','mercado_pago')),
  provider_event_id TEXT NOT NULL,
  order_id UUID NOT NULL REFERENCES purchase_orders(id),
  payload_digest TEXT NOT NULL,
  verified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(provider,provider_event_id)
);
CREATE TABLE IF NOT EXISTS purchase_entitlements (
  user_id TEXT NOT NULL REFERENCES app_users(id),
  track_id TEXT NOT NULL,
  order_id UUID NOT NULL REFERENCES purchase_orders(id),
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at TIMESTAMPTZ,
  PRIMARY KEY(user_id,track_id,order_id)
);
CREATE INDEX IF NOT EXISTS purchase_entitlements_active_idx ON purchase_entitlements(user_id,track_id) WHERE revoked_at IS NULL;
COMMIT;
