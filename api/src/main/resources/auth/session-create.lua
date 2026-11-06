-- All three records become visible together. No credentials occur in any value.
for i=1,3 do if redis.call('EXISTS',KEYS[i])==1 then return '0' end end
redis.call('SET',KEYS[1],ARGV[1],'EXAT',ARGV[2])
redis.call('HSET',KEYS[2],'identity',ARGV[3],'accountId',ARGV[4],'merchantId',ARGV[5],'role',ARGV[6],
  'loginAt',ARGV[7],'absoluteAt',ARGV[8],'generation','0','currentHash',ARGV[9],'state','ACTIVE','sid',ARGV[10])
redis.call('EXPIREAT',KEYS[2],ARGV[8])
redis.call('HSET',KEYS[3],'sid',ARGV[10],'generation','0','state','CURRENT')
redis.call('EXPIREAT',KEYS[3],ARGV[8])
return '1'
