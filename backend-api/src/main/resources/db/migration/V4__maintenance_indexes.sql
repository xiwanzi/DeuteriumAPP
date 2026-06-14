CREATE INDEX idx_sessions_expires_revoked
  ON sessions (expires_at, revoked_at);

CREATE INDEX idx_verification_expires
  ON verification_requests (expires_at);

CREATE INDEX idx_login_failures_updated_locked
  ON login_failures (updated_at, locked_until);

CREATE INDEX idx_server_events_occurred_id
  ON server_events (occurred_at, id);
