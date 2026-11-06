if redis.call('EXISTS',KEYS[2])==0 or tonumber(redis.call('HGET',KEYS[2],'absoluteAt') or '0')<=tonumber(ARGV[1]) then return '0' end
redis.call('HSET',KEYS[2],'state','REVOKED')
local hash=redis.call('HGET',KEYS[2],'currentHash')
if hash and redis.call('EXISTS',ARGV[2]..hash)==1 then redis.call('HSET',ARGV[2]..hash,'state','REVOKED') end
redis.call('DEL',KEYS[1])
local retry=redis.call('HGET',KEYS[2],'retryKey')
if retry then redis.call('DEL',retry) end
redis.call('HDEL',KEYS[2],'retryKey')
return '1'
