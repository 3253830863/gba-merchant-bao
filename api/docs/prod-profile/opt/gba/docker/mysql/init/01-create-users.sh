# Sourced by the official MySQL entrypoint only when the data directory is empty.
# Keep LF endings. Passwords are runtime environment values, never literals here.
# 不启用 nounset：官方 docker_process_sql 会读取可省略的 $1。
: "${FLYWAY_PASSWORD:?Required}"
: "${APP_DB_PASSWORD:?Required}"
migration_password=$(printf '%s' "$FLYWAY_PASSWORD" | sed "s/'/''/g")
application_password=$(printf '%s' "$APP_DB_PASSWORD" | sed "s/'/''/g")
docker_process_sql <<-EOSQL
SET SESSION sql_mode = 'NO_BACKSLASH_ESCAPES';
CREATE USER 'flyway_deploy'@'%' IDENTIFIED BY '$migration_password';
GRANT ALL PRIVILEGES ON gba.* TO 'flyway_deploy'@'%';
CREATE USER 'app'@'%' IDENTIFIED BY '$application_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON gba.* TO 'app'@'%';
EOSQL
unset migration_password application_password
