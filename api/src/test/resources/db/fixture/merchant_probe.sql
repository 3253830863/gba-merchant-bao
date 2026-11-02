-- 用例独享租户根；@Transactional 测试结束自动回滚，不删除共享种子。
INSERT INTO merchant (id, merchant_code, merchant_name, status, timezone, currency_code, version)
VALUES (1000000000000099, 'M_FIXTURE_RUNTIME', 'Runtime fixture', 'ACTIVE', 'Asia/Shanghai', 'CNY', 0);
