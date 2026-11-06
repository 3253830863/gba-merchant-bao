-- Non-production only. Insert missing identities, never reset operator changes.
-- A named NOT NULL guard gives a diagnostic before any business writes, without
-- persistent procedures, INSERT IGNORE, or changing already published V migrations.
DROP TEMPORARY TABLE IF EXISTS dev_seed_tenant_guard;
CREATE TEMPORARY TABLE dev_seed_tenant_guard (dev_seed_tenant_identity_conflict INT NOT NULL);
INSERT INTO dev_seed_tenant_guard
SELECT CASE WHEN EXISTS (
  SELECT 1 FROM merchant m
  JOIN (
    SELECT 1000000000000001 AS id, 'M_3C_DEMO' AS code
    UNION ALL SELECT 1000000000000002, 'M_ISOLATION_TEST'
  ) s ON m.id = s.id OR m.merchant_code = s.code
  WHERE m.id <> s.id OR BINARY m.merchant_code <> BINARY s.code
) THEN NULL ELSE 1 END;

INSERT INTO merchant (id, merchant_code, merchant_name, status, timezone, currency_code, version)
SELECT s.id, s.code, s.name, 'ACTIVE', 'Asia/Shanghai', 'CNY', 0
FROM (
  SELECT 1000000000000001 AS id, 'M_3C_DEMO' AS code, '3C demo tenant' AS name
  UNION ALL SELECT 1000000000000002, 'M_ISOLATION_TEST', 'Isolation test tenant'
) s
WHERE NOT EXISTS (SELECT 1 FROM merchant m WHERE m.id = s.id);
DROP TEMPORARY TABLE dev_seed_tenant_guard;
