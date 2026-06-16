ALTER TABLE ai_messages
  ADD COLUMN client_message_id VARCHAR(128) NULL;

CREATE UNIQUE INDEX uq_ai_messages_user_client
  ON ai_messages (user_id, client_message_id);

CREATE TABLE IF NOT EXISTS ai_knowledge_documents (
  id VARCHAR(40) PRIMARY KEY,
  source_url VARCHAR(512) NOT NULL,
  category VARCHAR(80) NOT NULL,
  title VARCHAR(160) NOT NULL,
  content TEXT NOT NULL,
  checksum VARCHAR(128) NOT NULL,
  trust_level INT NOT NULL,
  active BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ai_knowledge_documents_active
  ON ai_knowledge_documents (active, updated_at);

CREATE TABLE IF NOT EXISTS ai_knowledge_chunks (
  id VARCHAR(40) PRIMARY KEY,
  document_id VARCHAR(40) NOT NULL,
  source_url VARCHAR(512) NOT NULL,
  category VARCHAR(80) NOT NULL,
  title VARCHAR(160) NOT NULL,
  heading_path VARCHAR(255) NOT NULL,
  chunk_text TEXT NOT NULL,
  keywords TEXT NOT NULL,
  weight DECIMAL(5, 2) NOT NULL,
  active BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_ai_knowledge_chunks_document FOREIGN KEY (document_id) REFERENCES ai_knowledge_documents(id)
);

CREATE INDEX idx_ai_knowledge_chunks_active_weight
  ON ai_knowledge_chunks (active, weight);

ALTER TABLE ai_request_audits
  ADD COLUMN provider_status_code INT NULL,
  ADD COLUMN provider_error TEXT NULL,
  ADD COLUMN first_token_latency_ms INT NULL,
  ADD COLUMN total_latency_ms INT NULL,
  ADD COLUMN retry_count INT NULL,
  ADD COLUMN emitted_delta BOOLEAN NULL,
  ADD COLUMN knowledge_query TEXT NULL,
  ADD COLUMN knowledge_sources TEXT NULL;

INSERT INTO ai_knowledge_documents
  (id, source_url, category, title, content, checksum, trust_level, active, created_at, updated_at)
SELECT
  CONCAT('doc_', id),
  CASE
    WHEN CHAR_LENGTH(SUBSTRING_INDEX(REPLACE(content, '来源：', ''), '\n', 1)) <= 512
      AND SUBSTRING_INDEX(REPLACE(content, '来源：', ''), '\n', 1) LIKE 'http%'
    THEN SUBSTRING_INDEX(REPLACE(content, '来源：', ''), '\n', 1)
    ELSE 'https://wiki.deuterium.cafe/'
  END,
  category,
  title,
  content,
  SHA2(content, 256),
  80,
  active,
  created_at,
  updated_at
FROM ai_knowledge_items
WHERE NOT EXISTS (
  SELECT 1 FROM ai_knowledge_documents d WHERE d.id = CONCAT('doc_', ai_knowledge_items.id)
);

INSERT INTO ai_knowledge_chunks
  (id, document_id, source_url, category, title, heading_path, chunk_text, keywords, weight, active, created_at, updated_at)
SELECT
  CONCAT('chunk_', id, '_0'),
  CONCAT('doc_', id),
  CASE
    WHEN CHAR_LENGTH(SUBSTRING_INDEX(REPLACE(content, '来源：', ''), '\n', 1)) <= 512
      AND SUBSTRING_INDEX(REPLACE(content, '来源：', ''), '\n', 1) LIKE 'http%'
    THEN SUBSTRING_INDEX(REPLACE(content, '来源：', ''), '\n', 1)
    ELSE 'https://wiki.deuterium.cafe/'
  END,
  category,
  title,
  title,
  content,
  keywords,
  weight,
  active,
  created_at,
  updated_at
FROM ai_knowledge_items
WHERE NOT EXISTS (
  SELECT 1 FROM ai_knowledge_chunks c WHERE c.id = CONCAT('chunk_', ai_knowledge_items.id, '_0')
);
