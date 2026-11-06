local now=tonumber(ARGV[1])
local absolute=tonumber(redis.call('HGET',KEYS[2],'absoluteAt') or '0')
if redis.call('HGET',KEYS[2],'state')~='ACTIVE' or absolute<=now or redis.call('HGET',KEYS[2],'identity')~=ARGV[2] then return 'INVALID' end
if redis.call('HGET',KEYS[3],'sid')~=ARGV[3] then return 'INVALID' end
local gen=tonumber(redis.call('HGET',KEYS[2],'generation') or '-1')
local oldgen=tonumber(redis.call('HGET',KEYS[3],'generation') or '-2')
local current=redis.call('HGET',KEYS[2],'currentHash')
if not current or redis.call('HGET',ARGV[11]..current,'state')~='CURRENT' or redis.call('HGET',ARGV[11]..current,'sid')~=ARGV[3] then return 'INVALID' end
if redis.call('HGET',KEYS[3],'state')=='USED' then
  if redis.call('HGET',KEYS[3],'requestId')==ARGV[4] and gen==oldgen+1 and now<tonumber(redis.call('HGET',KEYS[3],'usedAt') or '0')+tonumber(ARGV[10]) then
    local cached=redis.call('GET',KEYS[5])
    if cached then return 'RETRY:'..cached end
  end
  redis.call('HSET',KEYS[2],'state','REVOKED')
  redis.call('HSET',ARGV[11]..current,'state','REVOKED')
  redis.call('DEL',KEYS[1])
  local previous=redis.call('HGET',KEYS[2],'retryKey')
  if previous then redis.call('DEL',previous) end
  return 'REPLAY'
end
if gen~=oldgen or redis.call('HGET',KEYS[2],'currentHash')~=ARGV[5] then return 'INVALID' end
if redis.call('EXISTS',KEYS[4])==1 then return 'INVALID' end
local previous=redis.call('HGET',KEYS[2],'retryKey')
if previous then redis.call('DEL',previous) end
redis.call('HSET',KEYS[3],'state','USED','requestId',ARGV[4],'usedAt',now)
redis.call('HSET',KEYS[4],'sid',ARGV[3],'generation',gen+1,'state','CURRENT')
redis.call('EXPIREAT',KEYS[4],absolute)
redis.call('HSET',KEYS[2],'generation',gen+1,'currentHash',ARGV[6],'retryKey',KEYS[5])
redis.call('SET',KEYS[1],ARGV[7],'EXAT',ARGV[8])
redis.call('SET',KEYS[5],ARGV[9],'EXAT',math.min(now+tonumber(ARGV[10]),absolute))
return 'OK'
