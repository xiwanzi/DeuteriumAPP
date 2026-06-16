CREATE TABLE IF NOT EXISTS ai_settings (
  setting_key VARCHAR(80) PRIMARY KEY,
  setting_value TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

ALTER TABLE ai_plans
  ADD COLUMN quota_window_hours INT NOT NULL DEFAULT 5;

ALTER TABLE ai_entitlements
  ADD COLUMN quota_window_hours INT NOT NULL DEFAULT 5;

INSERT INTO ai_settings (setting_key, setting_value, updated_at)
SELECT 'assistant_name', 'xxxAI', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_settings WHERE setting_key = 'assistant_name');

UPDATE ai_plans SET quota_window_hours = 5 WHERE quota_window_hours <= 0;
UPDATE ai_entitlements SET quota_window_hours = 5 WHERE quota_window_hours <= 0;
