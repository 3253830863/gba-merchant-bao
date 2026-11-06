if redis.call('EXISTS', KEYS[1]) == 1 then return tostring(math.max(1, redis.call('TTL', KEYS[1]))) end
for i=2,3 do
  if tonumber(redis.call('GET', KEYS[i]) or '0') >= tonumber(ARGV[i]) then
    return tostring(math.max(1, redis.call('TTL', KEYS[i])))
  end
end
redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
for i=2,3 do
  if redis.call('INCR', KEYS[i]) == 1 then redis.call('EXPIRE', KEYS[i], 3600) end
end
return '0'
