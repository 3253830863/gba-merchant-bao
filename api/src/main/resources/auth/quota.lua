local count = tonumber(redis.call('GET', KEYS[1]) or '0')
if count >= tonumber(ARGV[1]) then return tostring(math.max(1, redis.call('TTL', KEYS[1]))) end
redis.call('INCR', KEYS[1])
if count == 0 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
return '0'
