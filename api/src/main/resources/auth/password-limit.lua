if ARGV[1] == 'CLEAR' then redis.call('DEL', KEYS[1]); return '0' end
local count = tonumber(redis.call('GET', KEYS[1]) or '0')
if count >= tonumber(ARGV[2]) then return tostring(math.max(1, redis.call('TTL', KEYS[1]))) end
if ARGV[1] == 'FAIL' then
  count = redis.call('INCR', KEYS[1])
  if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end
  if count >= tonumber(ARGV[2]) then return tostring(math.max(1, redis.call('TTL', KEYS[1]))) end
end
return '0'
