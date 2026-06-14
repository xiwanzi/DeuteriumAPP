CREATE TABLE oidc_clients (
  client_id VARCHAR(80) PRIMARY KEY,
  client_secret_hash VARCHAR(128) NOT NULL,
  redirect_uri VARCHAR(512) NOT NULL,
  enabled BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE oidc_authorization_codes (
  id VARCHAR(40) PRIMARY KEY,
  code_hash VARCHAR(128) NOT NULL UNIQUE,
  user_id VARCHAR(40) NOT NULL,
  client_id VARCHAR(80) NOT NULL,
  redirect_uri VARCHAR(512) NOT NULL,
  scope VARCHAR(255) NOT NULL,
  nonce VARCHAR(255) NULL,
  expires_at TIMESTAMP NOT NULL,
  consumed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_oidc_codes_user FOREIGN KEY (user_id) REFERENCES app_users(id),
  CONSTRAINT fk_oidc_codes_client FOREIGN KEY (client_id) REFERENCES oidc_clients(client_id)
);

CREATE INDEX idx_oidc_codes_client_expiry
  ON oidc_authorization_codes (client_id, expires_at, consumed_at);

CREATE TABLE oidc_access_tokens (
  id VARCHAR(40) PRIMARY KEY,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  user_id VARCHAR(40) NOT NULL,
  client_id VARCHAR(80) NOT NULL,
  scope VARCHAR(255) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_oidc_access_tokens_user FOREIGN KEY (user_id) REFERENCES app_users(id),
  CONSTRAINT fk_oidc_access_tokens_client FOREIGN KEY (client_id) REFERENCES oidc_clients(client_id)
);

CREATE INDEX idx_oidc_access_tokens_expiry
  ON oidc_access_tokens (expires_at, revoked_at);

CREATE TABLE oidc_web_sessions (
  id VARCHAR(40) PRIMARY KEY,
  user_id VARCHAR(40) NOT NULL,
  session_hash VARCHAR(128) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_oidc_web_sessions_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE INDEX idx_oidc_web_sessions_expiry
  ON oidc_web_sessions (expires_at, revoked_at);
