-- Explicit @Sql only, after auth_accounts.sql; rollback with the test transaction.
INSERT INTO chat_session(id,merchant_id,owner_account_id,owner_role,title,created_at,updated_at,next_message_seq)
SELECT 2200000000000101,id,2000000000000201,'CONSUMER','Fixture one','2026-11-08 00:00:00.123','2026-11-08 00:00:01.123',3 FROM merchant WHERE merchant_code='M_3C_DEMO';
INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,sender_account_id,client_message_id,content,status,run_id,created_at,completed_at)
SELECT 2300000000000101,id,2200000000000101,1,'USER',2000000000000201,'00000000-0000-4000-8000-000000000001','Fixture user one','COMPLETED',2400000000000101,'2026-11-08 00:00:00.123','2026-11-08 00:00:01.123' FROM merchant WHERE merchant_code='M_3C_DEMO';
INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,sender_account_id,client_message_id,content,status,run_id,created_at,completed_at)
SELECT 2300000000000102,id,2200000000000101,2,'ASSISTANT',NULL,NULL,'Fixture assistant one','COMPLETED',2400000000000101,'2026-11-08 00:00:00.123','2026-11-08 00:00:01.123' FROM merchant WHERE merchant_code='M_3C_DEMO';
INSERT INTO chat_session(id,merchant_id,owner_account_id,owner_role,title,created_at,updated_at,next_message_seq)
SELECT 2200000000000102,id,2000000000000202,'CONSUMER','Fixture two','2026-11-08 00:00:00.123','2026-11-08 00:00:01.123',3 FROM merchant WHERE merchant_code='M_3C_DEMO';
INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,sender_account_id,client_message_id,content,status,run_id,created_at,completed_at)
SELECT 2300000000000103,id,2200000000000102,1,'USER',2000000000000202,'00000000-0000-4000-8000-000000000001','Fixture user two','COMPLETED',2400000000000102,'2026-11-08 00:00:00.123','2026-11-08 00:00:01.123' FROM merchant WHERE merchant_code='M_3C_DEMO';
INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,sender_account_id,client_message_id,content,status,run_id,created_at,completed_at)
SELECT 2300000000000104,id,2200000000000102,2,'ASSISTANT',NULL,NULL,'Fixture assistant two','COMPLETED',2400000000000102,'2026-11-08 00:00:00.123','2026-11-08 00:00:01.123' FROM merchant WHERE merchant_code='M_3C_DEMO';
INSERT INTO chat_session(id,merchant_id,owner_account_id,owner_role,title,created_at,updated_at,next_message_seq)
SELECT 2200000000000103,id,2000000000000101,'MERCHANT_STAFF','Fixture staff','2026-11-08 00:00:00.123','2026-11-08 00:00:01.123',3 FROM merchant WHERE merchant_code='M_3C_DEMO';
INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,sender_account_id,client_message_id,content,status,run_id,created_at,completed_at)
SELECT 2300000000000105,id,2200000000000103,1,'USER',2000000000000101,'00000000-0000-4000-8000-000000000001','Fixture user staff','COMPLETED',2400000000000103,'2026-11-08 00:00:00.123','2026-11-08 00:00:01.123' FROM merchant WHERE merchant_code='M_3C_DEMO';
INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,sender_account_id,client_message_id,content,status,run_id,created_at,completed_at)
SELECT 2300000000000106,id,2200000000000103,2,'ASSISTANT',NULL,NULL,'Fixture assistant staff','COMPLETED',2400000000000103,'2026-11-08 00:00:00.123','2026-11-08 00:00:01.123' FROM merchant WHERE merchant_code='M_3C_DEMO';
INSERT INTO chat_session(id,merchant_id,owner_account_id,owner_role,title,created_at,updated_at,next_message_seq)
SELECT 2200000000000104,id,2000000000000203,'CONSUMER','Fixture isolation','2026-11-08 00:00:00.123','2026-11-08 00:00:01.123',3 FROM merchant WHERE merchant_code='M_ISOLATION_TEST';
INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,sender_account_id,client_message_id,content,status,run_id,created_at,completed_at)
SELECT 2300000000000107,id,2200000000000104,1,'USER',2000000000000203,'00000000-0000-4000-8000-000000000001','Fixture user isolation','COMPLETED',2400000000000104,'2026-11-08 00:00:00.123','2026-11-08 00:00:01.123' FROM merchant WHERE merchant_code='M_ISOLATION_TEST';
INSERT INTO chat_message(id,merchant_id,session_id,message_seq,sender_type,sender_account_id,client_message_id,content,status,run_id,created_at,completed_at)
SELECT 2300000000000108,id,2200000000000104,2,'ASSISTANT',NULL,NULL,'Fixture assistant isolation','COMPLETED',2400000000000104,'2026-11-08 00:00:00.123','2026-11-08 00:00:01.123' FROM merchant WHERE merchant_code='M_ISOLATION_TEST';
