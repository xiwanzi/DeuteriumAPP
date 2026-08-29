CREATE TABLE IF NOT EXISTS ai_knowledge_items (
  id VARCHAR(40) PRIMARY KEY,
  category VARCHAR(80) NOT NULL,
  title VARCHAR(120) NOT NULL,
  keywords TEXT NOT NULL,
  content TEXT NOT NULL,
  weight DECIMAL(5, 2) NOT NULL,
  active BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ai_knowledge_active_weight
  ON ai_knowledge_items (active, weight);
