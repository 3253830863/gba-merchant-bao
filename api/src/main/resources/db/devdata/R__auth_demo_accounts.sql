-- Development only. Add missing stable identities; never reset an existing password/status.
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
