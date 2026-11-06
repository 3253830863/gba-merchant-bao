local now=tonumber(ARGV[2])
if redis.call('HGET',KEYS[2],'state')~='ACTIVE' or redis.call('HGET',KEYS[2],'identity')~=ARGV[1] then return '0' end
local absolute=tonumber(redis.call('HGET',KEYS[2],'absoluteAt') or '0')
if absolute<=now then return '0' end
local hash=redis.call('HGET',KEYS[2],'currentHash')
if not hash or redis.call('HGET',ARGV[5]..hash,'state')~='CURRENT' then return '0' end
if redis.call('HGET',ARGV[5]..hash,'sid')~=redis.call('HGET',KEYS[2],'sid') or redis.call('HGET',ARGV[5]..hash,'generation')~=redis.call('HGET',KEYS[2],'generation') then return '0' end
local active=redis.call('GET',KEYS[1])
if not active then return '0' end
local doc=cjson.decode(active)
if doc.identity~=ARGV[1] or tonumber(doc.idleAt)<=now then return '0' end
if ARGV[3]=='TOUCH' then
  doc.idleAt=math.min(now+tonumber(ARGV[4]),absolute)
  redis.call('SET',KEYS[1],cjson.encode(doc),'EXAT',doc.idleAt)
end
return '1'
