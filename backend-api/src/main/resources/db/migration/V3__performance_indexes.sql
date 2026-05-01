CREATE INDEX idx_player_refs_server_registered_expires
  ON player_refs (server_uuid, registered, expires_at);

CREATE INDEX idx_app_users_status_game_id
  ON app_users (status, current_game_id);

CREATE INDEX idx_chat_messages_sent_id
  ON chat_messages (sent_at, id);

CREATE INDEX idx_wallet_records_user_time_id
  ON wallet_records (user_id, occurred_at, id);

CREATE INDEX idx_app_presence_last_seen
  ON app_presence (last_seen_at);
