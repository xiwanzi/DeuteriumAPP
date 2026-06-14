CREATE TABLE app_users (
  id VARCHAR(40) PRIMARY KEY,
  server_uuid VARCHAR(80) NOT NULL UNIQUE,
  current_game_id VARCHAR(32) NOT NULL,
  qq VARCHAR(20) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_app_users_game_id ON app_users (current_game_id);

CREATE TABLE sessions (
  id VARCHAR(40) PRIMARY KEY,
  user_id VARCHAR(40) NOT NULL,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE INDEX idx_sessions_user ON sessions (user_id);

CREATE TABLE verification_requests (
  id VARCHAR(40) PRIMARY KEY,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  purpose VARCHAR(32) NOT NULL,
  server_uuid VARCHAR(80) NOT NULL,
  game_id VARCHAR(32) NOT NULL,
  qq VARCHAR(20) NULL,
  code_hash VARCHAR(128) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  resend_available_at TIMESTAMP NOT NULL,
  attempts INT NOT NULL,
  max_attempts INT NOT NULL,
  consumed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_verification_context ON verification_requests (purpose, game_id, consumed_at, expires_at);

CREATE TABLE login_failures (
  failure_key VARCHAR(160) PRIMARY KEY,
  attempts INT NOT NULL,
  locked_until TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE player_refs (
  player_ref VARCHAR(64) PRIMARY KEY,
  server_uuid VARCHAR(80) NOT NULL,
  current_game_id VARCHAR(32) NOT NULL,
  qq VARCHAR(20) NULL,
  registered BOOLEAN NOT NULL,
  online BOOLEAN NOT NULL,
  source VARCHAR(32) NOT NULL,
  confirmed_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_player_refs_server_uuid ON player_refs (server_uuid);

CREATE TABLE wallet_balances (
  user_id VARCHAR(40) PRIMARY KEY,
  amount DECIMAL(18,2) NOT NULL,
  currency VARCHAR(16) NOT NULL,
  fresh BOOLEAN NOT NULL,
  refreshed_at TIMESTAMP NULL,
  CONSTRAINT fk_wallet_balances_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE TABLE transfers (
  id VARCHAR(40) PRIMARY KEY,
  client_request_id VARCHAR(128) NOT NULL,
  user_id VARCHAR(40) NOT NULL,
  request_fingerprint VARCHAR(128) NOT NULL,
  from_server_uuid VARCHAR(80) NOT NULL,
  to_server_uuid VARCHAR(80) NOT NULL,
  recipient_game_id VARCHAR(32) NOT NULL,
  recipient_qq VARCHAR(20) NULL,
  amount DECIMAL(18,2) NOT NULL,
  currency VARCHAR(16) NOT NULL,
  note VARCHAR(80) NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_transfers_user FOREIGN KEY (user_id) REFERENCES app_users(id),
  CONSTRAINT uq_transfers_client UNIQUE (user_id, client_request_id)
);

CREATE TABLE wallet_records (
  id VARCHAR(40) PRIMARY KEY,
  user_id VARCHAR(40) NOT NULL,
  direction VARCHAR(16) NOT NULL,
  other_server_uuid VARCHAR(80) NOT NULL,
  other_game_id VARCHAR(32) NOT NULL,
  other_qq VARCHAR(20) NULL,
  amount DECIMAL(18,2) NOT NULL,
  currency VARCHAR(16) NOT NULL,
  status VARCHAR(20) NOT NULL,
  note VARCHAR(80) NULL,
  occurred_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_wallet_records_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE INDEX idx_wallet_records_user_time ON wallet_records (user_id, occurred_at);

CREATE TABLE chat_messages (
  id VARCHAR(40) PRIMARY KEY,
  sender_server_uuid VARCHAR(80) NOT NULL,
  sender_game_id VARCHAR(32) NOT NULL,
  content VARCHAR(256) NOT NULL,
  kind VARCHAR(32) NOT NULL,
  sent_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_chat_messages_sent_at ON chat_messages (sent_at);

CREATE TABLE server_events (
  id VARCHAR(40) PRIMARY KEY,
  event_type VARCHAR(32) NOT NULL,
  content VARCHAR(256) NOT NULL,
  occurred_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_server_events_occurred_at ON server_events (occurred_at);

CREATE TABLE presence_snapshots (
  id INT PRIMARY KEY,
  online_count INT NOT NULL,
  players_json TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

