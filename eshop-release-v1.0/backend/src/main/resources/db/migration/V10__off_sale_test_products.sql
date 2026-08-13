-- Hide smoke/test/junk catalog entries from the public shop and hot ranking
-- before acceptance. Demo catalog names start with "演示" and stay ON_SALE.
UPDATE product
SET status = 'OFF_SALE',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'ON_SALE'
  AND (
       name LIKE '%测试%'
    OR name LIKE '%Test%'
    OR name LIKE '%test%'
    OR name LIKE '%TEST%'
    OR name LIKE '%Smoke%'
    OR name LIKE '%smoke%'
    OR name LIKE 'Smoke Product%'
    OR name LIKE '%乱码%'
    OR name LIKE '%placeholder%'
    OR name LIKE '%PLACEHOLDER%'
    OR name LIKE '%临时%'
    OR name LIKE '%demo-test%'
  );
