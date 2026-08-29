CREATE TABLE ai_request_exchanges (
    id VARCHAR(40) NOT NULL PRIMARY KEY,
    user_id VARCHAR(40) NOT NULL,
    client_message_id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(40) NULL,
    user_message_id VARCHAR(40) NULL,
    assistant_message_id VARCHAR(40) NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(80) NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ai_request_exchanges_user FOREIGN KEY (user_id) REFERENCES app_users(id),
    CONSTRAINT uq_ai_request_exchange_client UNIQUE (user_id, client_message_id)
);
