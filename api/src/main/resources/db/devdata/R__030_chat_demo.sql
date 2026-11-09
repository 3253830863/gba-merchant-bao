-- Development only; never loaded by test/prod. Stable demo rows, no model invocation.
DROP TEMPORARY TABLE IF EXISTS dev_seed_chat_expected_session;
CREATE TEMPORARY TABLE dev_seed_chat_expected_session (
  id BIGINT NOT NULL, merchant_id BIGINT NULL, owner_account_id BIGINT NOT NULL,
  owner_role VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
);
INSERT INTO dev_seed_chat_expected_session
SELECT e.id,m.id,e.account_id,e.role FROM (
  SELECT 2200000000000001 AS id,'M_3C_DEMO' AS code,2100000000000201 AS account_id,'CONSUMER' AS role
  UNION ALL SELECT 2200000000000002,'M_3C_DEMO',2100000000000202,'CONSUMER'
  UNION ALL SELECT 2200000000000003,'M_3C_DEMO',2100000000000101,'MERCHANT_STAFF'
  UNION ALL SELECT 2200000000000004,'M_ISOLATION_TEST',2100000000000203,'CONSUMER'
) e LEFT JOIN merchant m ON m.merchant_code=e.code;

DROP TEMPORARY TABLE IF EXISTS dev_seed_chat_expected_message;
CREATE TEMPORARY TABLE dev_seed_chat_expected_message (
  id BIGINT NOT NULL, merchant_id BIGINT NULL, session_id BIGINT NOT NULL, message_seq BIGINT NOT NULL,
  sender_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, sender_account_id BIGINT NULL,
  client_message_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL, run_id BIGINT NOT NULL, content LONGTEXT NOT NULL
);
INSERT INTO dev_seed_chat_expected_message
SELECT 2300000000000001+(s.id-2200000000000001)*2,s.merchant_id,s.id,1,'USER',s.owner_account_id,
 '00000000-0000-4000-8000-000000000001',2400000000000001+(s.id-2200000000000001),'你好，请介绍一下店铺服务。'
FROM dev_seed_chat_expected_session s JOIN merchant p ON p.id=s.merchant_id;
INSERT INTO dev_seed_chat_expected_message
SELECT 2300000000000002+(s.id-2200000000000001)*2,s.merchant_id,s.id,2,'ASSISTANT',NULL,
 NULL,2400000000000001+(s.id-2200000000000001),'这是开发演示回复，不代表真实模型调用。'
FROM dev_seed_chat_expected_session s;

DROP TEMPORARY TABLE IF EXISTS dev_seed_chat_guard;
CREATE TEMPORARY TABLE dev_seed_chat_guard (
  dev_seed_chat_missing_owner INT NOT NULL,
  dev_seed_chat_session_identity_conflict INT NOT NULL,
  dev_seed_chat_message_identity_conflict INT NOT NULL
);
INSERT INTO dev_seed_chat_guard SELECT
 CASE WHEN EXISTS (SELECT 1 FROM dev_seed_chat_expected_session e WHERE e.merchant_id IS NULL
   OR (e.owner_role='CONSUMER' AND NOT EXISTS (SELECT 1 FROM consumer_account a WHERE a.id=e.owner_account_id AND a.merchant_id=e.merchant_id))
   OR (e.owner_role='MERCHANT_STAFF' AND NOT EXISTS (SELECT 1 FROM merchant_staff_account a WHERE a.id=e.owner_account_id AND a.merchant_id=e.merchant_id)))
 THEN NULL ELSE 1 END,1,1;
INSERT INTO dev_seed_chat_guard SELECT 1,
 CASE WHEN EXISTS (SELECT 1 FROM chat_session s JOIN dev_seed_chat_expected_session e ON s.id=e.id
   WHERE s.merchant_id<>e.merchant_id OR s.owner_account_id<>e.owner_account_id OR s.owner_role<>e.owner_role OR s.next_message_seq<3)
 THEN NULL ELSE 1 END,1;
INSERT INTO dev_seed_chat_guard SELECT 1,1,
 CASE WHEN EXISTS (SELECT 1 FROM chat_message m JOIN dev_seed_chat_expected_message e
   ON m.id=e.id OR (m.merchant_id=e.merchant_id AND m.session_id=e.session_id
     AND (m.message_seq=e.message_seq OR m.client_message_id=e.client_message_id OR (m.run_id=e.run_id AND m.sender_type=e.sender_type)))
   WHERE NOT (m.id=e.id AND m.merchant_id=e.merchant_id AND m.session_id=e.session_id AND m.message_seq=e.message_seq
     AND m.sender_type=e.sender_type AND (m.sender_account_id <=> e.sender_account_id)
     AND (m.client_message_id <=> e.client_message_id) AND m.run_id=e.run_id))
 THEN NULL ELSE 1 END;

INSERT INTO chat_session(id,merchant_id,owner_account_id,owner_role,title,created_at,updated_at,next_message_seq)
SELECT e.id,e.merchant_id,e.owner_account_id,e.owner_role,'开发演示对话','2026-11-08 00:00:00.000','2026-11-08 00:00:01.000',3
FROM dev_seed_chat_expected_session e WHERE NOT EXISTS (SELECT 1 FROM chat_session s WHERE s.id=e.id);
INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,sender_account_id,client_message_id,content,status,run_id,created_at,completed_at)
SELECT e.id,e.merchant_id,e.session_id,e.message_seq,e.sender_type,e.sender_account_id,e.client_message_id,e.content,'COMPLETED',e.run_id,
 '2026-11-08 00:00:00.000','2026-11-08 00:00:01.000'
FROM dev_seed_chat_expected_message e WHERE NOT EXISTS (SELECT 1 FROM chat_message m WHERE m.id=e.id);
DROP TEMPORARY TABLE dev_seed_chat_guard;
DROP TEMPORARY TABLE dev_seed_chat_expected_message;
DROP TEMPORARY TABLE dev_seed_chat_expected_session;
