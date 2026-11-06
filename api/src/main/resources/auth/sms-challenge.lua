if ARGV[1] == 'CREATE' then
  redis.call('DEL', KEYS[1])
  redis.call('HSET', KEYS[1], 'challengeId', ARGV[2], 'salt', ARGV[3], 'digest', ARGV[4], 'absoluteAt', ARGV[5], 'failures', '0', 'ready', '0')
  redis.call('EXPIRE', KEYS[1], 300)
  return '1'
end
if redis.call('HGET', KEYS[1], 'challengeId') ~= ARGV[2] then return '0' end
if ARGV[1] == 'DELETE' then redis.call('DEL', KEYS[1]); return '1' end
if ARGV[1] == 'ACTIVATE' then redis.call('HSET', KEYS[1], 'ready', '1'); return '1' end
if ARGV[1] == 'CONSUME' then
  if redis.call('HGET', KEYS[1], 'ready') ~= '1' then return '0' end
  if tonumber(redis.call('HGET', KEYS[1], 'absoluteAt')) <= tonumber(ARGV[4]) then redis.call('DEL', KEYS[1]); return '0' end
  if redis.call('HGET', KEYS[1], 'digest') == ARGV[3] then redis.call('DEL', KEYS[1]); return '1' end
  if redis.call('HINCRBY', KEYS[1], 'failures', 1) >= 5 then redis.call('DEL', KEYS[1]) end
end
return '0'
