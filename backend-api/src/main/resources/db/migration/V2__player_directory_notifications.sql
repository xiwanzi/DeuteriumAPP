CREATE TABLE app_presence (
  user_id VARCHAR(40) PRIMARY KEY,
  foreground BOOLEAN NOT NULL,
  last_foreground_at TIMESTAMP NULL,
  last_seen_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_app_presence_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE TABLE player_follows (
  user_id VARCHAR(40) NOT NULL,
  target_server_uuid VARCHAR(80) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY (user_id, target_server_uuid),
  CONSTRAINT fk_player_follows_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE INDEX idx_player_follows_target ON player_follows (target_server_uuid);
