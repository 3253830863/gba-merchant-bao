-- Development only. Add missing stable identities; never reset an existing password/status.
-- Guards run before account inserts. Error column names identify the missing dependency/conflict.
DROP TEMPORARY TABLE IF EXISTS dev_seed_account_guard;
CREATE TEMPORARY TABLE dev_seed_account_guard (
  dev_seed_missing_M_3C_DEMO INT NOT NULL,
  dev_seed_missing_M_ISOLATION_TEST INT NOT NULL,
  dev_seed_staff_identity_conflict INT NOT NULL,
  dev_seed_consumer_identity_conflict INT NOT NULL
);
INSERT INTO dev_seed_account_guard
SELECT
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_3C_DEMO') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (SELECT 1 FROM merchant WHERE merchant_code = 'M_ISOLATION_TEST') THEN 1 ELSE NULL END,
  CASE WHEN EXISTS (
    SELECT 1 FROM merchant_staff_account a
    JOIN (
      SELECT 2100000000000101 AS id, 'M_3C_DEMO' AS code
      UNION ALL SELECT 2100000000000102, 'M_ISOLATION_TEST'
    ) s JOIN merchant m ON m.merchant_code = s.code
    WHERE (a.id = s.id OR (a.merchant_id = m.id AND (a.username = 'demo_staff' OR a.phone = '+8613800000301')))
      AND (a.id <> s.id OR a.merchant_id <> m.id OR a.username <> 'demo_staff' OR a.phone <> '+8613800000301')
  ) THEN NULL ELSE 1 END,
  CASE WHEN EXISTS (
    SELECT 1 FROM consumer_account a
    JOIN (
      SELECT 2100000000000201 AS id, 'M_3C_DEMO' AS code, '+8613800000401' AS phone
      UNION ALL SELECT 2100000000000202, 'M_3C_DEMO', '+8613800000402'
      UNION ALL SELECT 2100000000000203, 'M_ISOLATION_TEST', '+8613800000401'
    ) s JOIN merchant m ON m.merchant_code = s.code
    WHERE (a.id = s.id OR (a.merchant_id = m.id AND a.phone = s.phone))
      AND (a.id <> s.id OR a.merchant_id <> m.id OR a.phone <> s.phone)
  ) THEN NULL ELSE 1 END;

INSERT INTO merchant_staff_account (id,merchant_id,username,phone,display_name,password_hash,status,created_by)
SELECT 2100000000000101,m.id,'demo_staff','+8613800000301','开发员工',
 '$2b$12$gnKselqI2U0.vNKlKHmVAOPrWp1fg1M2O5.RBqzpDQ5.LqcjWoWSS','ACTIVE',NULL
FROM merchant m WHERE m.merchant_code='M_3C_DEMO'
AND NOT EXISTS (SELECT 1 FROM merchant_staff_account a WHERE a.merchant_id=m.id AND (a.username='demo_staff' OR a.phone='+8613800000301'));
INSERT INTO merchant_staff_account (id,merchant_id,username,phone,display_name,password_hash,status,created_by)
SELECT 2100000000000102,m.id,'demo_staff','+8613800000301','隔离开发员工',
 '$2b$12$gnKselqI2U0.vNKlKHmVAOPrWp1fg1M2O5.RBqzpDQ5.LqcjWoWSS','ACTIVE',NULL
FROM merchant m WHERE m.merchant_code='M_ISOLATION_TEST'
AND NOT EXISTS (SELECT 1 FROM merchant_staff_account a WHERE a.merchant_id=m.id AND (a.username='demo_staff' OR a.phone='+8613800000301'));
INSERT INTO consumer_account (id,merchant_id,phone,display_name,status)
SELECT 2100000000000201,m.id,'+8613800000401','开发消费者一','ACTIVE' FROM merchant m
WHERE m.merchant_code='M_3C_DEMO' AND NOT EXISTS (SELECT 1 FROM consumer_account a WHERE a.merchant_id=m.id AND a.phone='+8613800000401');
INSERT INTO consumer_account (id,merchant_id,phone,display_name,status)
SELECT 2100000000000202,m.id,'+8613800000402','开发消费者二','ACTIVE' FROM merchant m
WHERE m.merchant_code='M_3C_DEMO' AND NOT EXISTS (SELECT 1 FROM consumer_account a WHERE a.merchant_id=m.id AND a.phone='+8613800000402');
INSERT INTO consumer_account (id,merchant_id,phone,display_name,status)
SELECT 2100000000000203,m.id,'+8613800000401','隔离开发消费者','ACTIVE' FROM merchant m
WHERE m.merchant_code='M_ISOLATION_TEST' AND NOT EXISTS (SELECT 1 FROM consumer_account a WHERE a.merchant_id=m.id AND a.phone='+8613800000401');
DROP TEMPORARY TABLE dev_seed_account_guard;
