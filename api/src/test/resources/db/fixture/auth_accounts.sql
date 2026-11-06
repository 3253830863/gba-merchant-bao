-- Explicit @Sql fixture only; run inside a rollback test transaction.
-- Independent test password: FixtureOnly-Auth-2026! (BCrypt cost 12), never a dev demo credential.
INSERT INTO merchant_staff_account (id, merchant_id, username, phone, display_name, password_hash)
SELECT 2000000000000101, id, 'fixture_staff', '+8613900000301', 'Fixture demo staff',
       '$2b$12$xeX8umWaV9BaN2xHRQeEHuXhKVqlHQriJomAlgBNHyFnfmMDqySxq'
FROM merchant WHERE merchant_code = 'M_3C_DEMO';

INSERT INTO merchant_staff_account (id, merchant_id, username, phone, display_name, password_hash)
SELECT 2000000000000102, id, 'fixture_staff', '+8613900000301', 'Fixture isolation staff',
       '$2b$12$xeX8umWaV9BaN2xHRQeEHuXhKVqlHQriJomAlgBNHyFnfmMDqySxq'
FROM merchant WHERE merchant_code = 'M_ISOLATION_TEST';

INSERT INTO consumer_account (id, merchant_id, phone, display_name)
SELECT 2000000000000201, id, '+8613900000401', 'Fixture consumer one'
FROM merchant WHERE merchant_code = 'M_3C_DEMO';

INSERT INTO consumer_account (id, merchant_id, phone, display_name)
SELECT 2000000000000202, id, '+8613900000402', 'Fixture consumer two'
FROM merchant WHERE merchant_code = 'M_3C_DEMO';

INSERT INTO consumer_account (id, merchant_id, phone, display_name)
SELECT 2000000000000203, id, '+8613900000401', 'Fixture isolation consumer'
FROM merchant WHERE merchant_code = 'M_ISOLATION_TEST';
