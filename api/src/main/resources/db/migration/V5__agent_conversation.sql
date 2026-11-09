-- UTC DATETIME values are supplied explicitly by the application.
CREATE TABLE chat_session (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  owner_account_id BIGINT NOT NULL,
  owner_role VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  title VARCHAR(200) NOT NULL DEFAULT '新对话',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  archived_at DATETIME(3) NULL,
  archived_by BIGINT NULL,
  next_message_seq BIGINT NOT NULL DEFAULT 1,
  active_run_id BIGINT NULL,
  run_deadline_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_session_merchant_id (merchant_id,id),
  KEY idx_chat_session_owner_page (merchant_id,owner_account_id,owner_role,updated_at,id),
  CONSTRAINT fk_chat_session_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id),
  CONSTRAINT ck_chat_session_role CHECK (owner_role IN ('CONSUMER','MERCHANT_STAFF')),
  CONSTRAINT ck_chat_session_status CHECK (status IN ('ACTIVE','ARCHIVED')),
  CONSTRAINT ck_chat_session_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
  CONSTRAINT ck_chat_session_seq CHECK (next_message_seq > 0),
  CONSTRAINT ck_chat_session_archive CHECK (
    (status='ACTIVE' AND archived_at IS NULL AND archived_by IS NULL) OR
    (status='ARCHIVED' AND archived_at IS NOT NULL AND archived_by IS NOT NULL AND archived_by=owner_account_id)),
  CONSTRAINT ck_chat_session_run CHECK (
    (active_run_id IS NULL AND run_deadline_at IS NULL) OR
    (active_run_id IS NOT NULL AND run_deadline_at IS NOT NULL AND status='ACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_message (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  session_id BIGINT NOT NULL,
  message_seq BIGINT NOT NULL,
  sender_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  sender_account_id BIGINT NULL,
  client_message_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  content LONGTEXT NOT NULL,
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  run_id BIGINT NOT NULL,
  error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  prompt_version VARCHAR(64) NULL,
  model_service_label VARCHAR(64) NULL,
  model_name VARCHAR(128) NULL,
  input_tokens BIGINT NULL,
  output_tokens BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_message_seq (merchant_id,session_id,message_seq),
  UNIQUE KEY uk_chat_message_client (merchant_id,session_id,client_message_id),
  UNIQUE KEY uk_chat_message_run_sender (merchant_id,session_id,run_id,sender_type),
  CONSTRAINT fk_chat_message_session FOREIGN KEY (merchant_id,session_id) REFERENCES chat_session(merchant_id,id),
  CONSTRAINT ck_chat_message_seq CHECK (message_seq > 0),
  CONSTRAINT ck_chat_message_sender CHECK (sender_type IN ('USER','ASSISTANT')),
  CONSTRAINT ck_chat_message_status CHECK (status IN ('STARTED','STREAMING','COMPLETED','CANCELLED','TIMED_OUT','FAILED')),
  CONSTRAINT ck_chat_message_actor CHECK (
    (sender_type='USER' AND sender_account_id IS NOT NULL AND client_message_id IS NOT NULL
      AND REGEXP_LIKE(client_message_id,'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$','c')
      AND status='COMPLETED' AND input_tokens IS NULL AND output_tokens IS NULL) OR
    (sender_type='ASSISTANT' AND sender_account_id IS NULL AND client_message_id IS NULL)),
  CONSTRAINT ck_chat_message_usage CHECK ((input_tokens IS NULL OR input_tokens >= 0) AND (output_tokens IS NULL OR output_tokens >= 0)),
  CONSTRAINT ck_chat_message_completion CHECK (
    (status IN ('STARTED','STREAMING') AND completed_at IS NULL) OR
    (status IN ('COMPLETED','CANCELLED','TIMED_OUT','FAILED') AND completed_at IS NOT NULL AND completed_at >= created_at)),
  CONSTRAINT ck_chat_message_error CHECK (
    (status IN ('STARTED','STREAMING','COMPLETED') AND error_code IS NULL) OR
    (status IN ('CANCELLED','TIMED_OUT','FAILED') AND error_code IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
